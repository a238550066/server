/*
 This file is part of the OdinMS Maple Story Server
 Copyright (C) 2008 ~ 2010 Patrick Huy <patrick.huy@frz.cc>
 Matthias Butz <matze@odinms.de>
 Jan Christian Meyer <vimes@odinms.de>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License version 3
 as published by the Free Software Foundation. You may not use, modify
 or distribute this program under any other version of the
 GNU Affero General Public License.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package scripting;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import javax.script.ScriptException;

import client.MapleCharacter;
import client.MapleQuestStatus;
import handling.channel.ChannelServer;
import handling.world.MapleParty;
import handling.world.MaplePartyCharacter;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import server.MapleCarnivalParty;
import server.MapleItemInformationProvider;
import server.MapleSquad;
import server.Timer.EventTimer;
import server.quests.MapleQuest;
import server.life.MapleMonster;
import server.maps.MapleMap;
import server.maps.MapleMapFactory;
import tools.FileoutputUtil;
import tools.MaplePacketCreator;
import tools.packet.UIPacket;

public class EventInstanceManager
{
    private List<MapleCharacter> chars = new LinkedList<>(); //this is messy
    private List<Integer> dced = new LinkedList<>();
    private List<MapleMonster> mobs = new LinkedList<>();
    private Map<Integer, Integer> killCount = new HashMap<>();
    private EventManager em;
    private int channel;
    private String name;
    private Properties props = new Properties();
    private long timeStarted = 0;
    private long eventTime = 0;
    private List<Integer> mapIds = new LinkedList<>();
    private List<Boolean> isInstanced = new LinkedList<>();
    private ScheduledFuture<?> eventTimer;
    private final ReentrantReadWriteLock mutex = new ReentrantReadWriteLock();
    private final Lock rL = mutex.readLock(), wL = mutex.writeLock();
    private boolean disposed = false;

    public EventInstanceManager(final EventManager em, final String name, final int channel)
    {
        this.em = em;
        this.name = name;
        this.channel = channel;
    }

    public final void registerPlayer(final MapleCharacter chr)
    {
        if (this.disposed || chr == null) {
            return;
        }

        try {
            this.wL.lock();

            try {
                this.chars.add(chr);
            } finally {
                this.wL.unlock();
            }

            chr.setEventInstance(this);

            this.em.getIv().invokeFunction("playerEntry", this, chr);
        } catch (NullPointerException ex) {
            FileoutputUtil.outputFileError(FileoutputUtil.ScriptEx_Log, ex);
            ex.printStackTrace();
        } catch (Exception ex) {
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + em.getName() + ", Instance name : " + name + ", method Name : playerEntry:\n" + ex);
            System.out.println("Event name" + em.getName() + ", Instance name : " + name + ", method Name : playerEntry:\n" + ex);
        }
    }

    public final void changedMap(final MapleCharacter chr, final int mapId)
    {
        if (this.disposed) {
            return;
        }

        try {
            this.em.getIv().invokeFunction("changedMap", this, chr, mapId);
        } catch (NullPointerException npe) {
        } catch (Exception ex) {
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + em.getName() + ", Instance name : " + name + ", method Name : changedMap:\n" + ex);
            System.out.println("Event name" + em.getName() + ", Instance name : " + name + ", method Name : changedMap:\n" + ex);
        }
    }

    public final void timeOut(final long delay, final EventInstanceManager eim)
    {
        if (this.disposed || eim == null) {
            return;
        }

        this.eventTimer = EventTimer.getInstance().schedule(() -> {
            if (this.disposed || this.em == null) {
                return;
            }

            try {
                this.em.getIv().invokeFunction("scheduledTimeout", eim);
            } catch (Exception ex) {
                FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + em.getName() + ", Instance name : " + name + ", method Name : scheduledTimeout:\n" + ex);
                System.out.println("Event name" + em.getName() + ", Instance name : " + name + ", method Name : scheduledTimeout:\n" + ex);
            }
        }, delay);
    }

    public final void stopEventTimer()
    {
        this.eventTime = 0;
        this.timeStarted = 0;

        if (this.eventTimer != null) {
            this.eventTimer.cancel(false);
        }
    }

    public final void restartEventTimer(final long time)
    {
        try {
            if (this.disposed) {
                return;
            }

            this.timeStarted = System.currentTimeMillis();
            this.eventTime = time;

            if (this.eventTimer != null) {
                this.eventTimer.cancel(false);
            }

            this.eventTimer = null;

            final int second = (int) time / 1000;

            for (final MapleCharacter chr : getPlayers()) {
                chr.getClient().getSession().write(MaplePacketCreator.getClock(second));
            }

            this.timeOut(time, this);
        } catch (Exception ex) {
            FileoutputUtil.outputFileError(FileoutputUtil.ScriptEx_Log, ex);
            System.out.println("Event name" + em.getName() + ", Instance name : " + name + ", method Name : restartEventTimer:\n");
            ex.printStackTrace();
        }
    }

    public final void startEventTimer(final long time)
    {
        this.restartEventTimer(time); //just incase
    }

    public final boolean isTimerStarted()
    {
        return this.eventTime > 0 && this.timeStarted > 0;
    }

    public final long getTimeLeft()
    {
        return this.eventTime - (System.currentTimeMillis() - this.timeStarted);
    }

    public final void registerParty(final MapleParty party, final MapleMap map)
    {
        if (this.disposed) {
            return;
        }

        for (final MaplePartyCharacter pc : party.getMembers()) {
            this.registerPlayer(map.getCharacterById(pc.getId()));
        }
    }

    public final void unregisterPlayer(final MapleCharacter chr)
    {
        if (this.disposed) {
            chr.setEventInstance(null);
            return;
        }

        try {
            this.wL.lock();
            this.unregisterPlayerNoLock(chr);
        } finally {
            this.wL.unlock();
        }
    }

    private boolean unregisterPlayerNoLock(final MapleCharacter chr)
    {
        if (this.name.equals("CWKPQ")) { //hard code it because i said so
            final MapleSquad squad = ChannelServer.getInstance(channel).getMapleSquad("CWKPQ");//so fkin hacky

            if (squad != null) {
                squad.removeMember(chr.getName());

                if (squad.getLeaderName().equals(chr.getName())) {
                    this.em.setProperty("leader", "false");
                }
            }
        }

        chr.setEventInstance(null);

        if (this.disposed) {
            return false;
        }
        if (this.chars.contains(chr)) {
            this.chars.remove(chr);
            return true;
        }

        return false;
    }

    public final boolean disposeIfPlayerBelow(final byte size, final int mapId)
    {
        if (this.disposed) {
            return true;
        }

        MapleMap map = null;

        if (mapId > 0) {
            map = this.getMapFactory().getMap(mapId);
        }

        try {
            this.wL.lock();

            if (this.chars.size() <= size) {
                for (final MapleCharacter chr : new LinkedList<>(this.chars)) {
                    this.unregisterPlayerNoLock(chr);

                    if (mapId > 0) {
                        chr.changeMap(map, map.getPortal(0));
                    }
                }

                this.disposeNoLock();

                return true;
            }
        } finally {
            this.wL.unlock();
        }

        return false;
    }

    public final void saveBossQuest(final int points)
    {
        if (this.disposed) {
            return;
        }

        for (final MapleCharacter chr : this.getPlayers()) {
            // @todo fix quest id
            final MapleQuestStatus record = chr.getOrAddQuest(MapleQuest.getInstance(150001));

            if (record.getData() != null) {
                record.setData(String.valueOf(points + Integer.parseInt(record.getData())));
            } else {
                record.setData(String.valueOf(points)); // First time
            }
        }
    }

    public final List<MapleCharacter> getPlayers()
    {
        if (this.disposed) {
            return Collections.emptyList();
        }

        try {
            this.rL.lock();

            return new LinkedList<>(this.chars);
        } finally {
            this.rL.unlock();
        }
    }

    public final List<Integer> getDisconnected()
    {
        return this.dced;
    }

    public final int getPlayerCount()
    {
        if (this.disposed) {
            return 0;
        }

        return this.chars.size();
    }

    public final void registerMonster(final MapleMonster mob)
    {
        if (this.disposed) {
            return;
        }

        this.mobs.add(mob);

        mob.setEventInstance(this);
    }

    public final void unregisterMonster(final MapleMonster mob)
    {
        mob.setEventInstance(null);

        if (this.disposed) {
            return;
        }

        this.mobs.remove(mob);

        if (this.mobs.size() == 0) {
            try {
                this.em.getIv().invokeFunction("allMonstersDead", this);
            } catch (Exception ex) {
                FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + em.getName() + ", Instance name : " + name + ", method Name : allMonstersDead:\n" + ex);
                System.out.println("Event name" + em.getName() + ", Instance name : " + name + ", method Name : allMonstersDead:\n" + ex);
            }
        }
    }

    public final void playerKilled(final MapleCharacter chr)
    {
        if (this.disposed) {
            return;
        }

        try {
            this.em.getIv().invokeFunction("playerDead", this, chr);
        } catch (Exception ex) {
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + em.getName() + ", Instance name : " + name + ", method Name : playerDead:\n" + ex);
            System.out.println("Event name" + em.getName() + ", Instance name : " + name + ", method Name : playerDead:\n" + ex);
        }
    }

    public final boolean revivePlayer(final MapleCharacter chr)
    {
        if (this.disposed) {
            return false;
        }

        try {
            final Object b = this.em.getIv().invokeFunction("playerRevive", this, chr);

            if (b instanceof Boolean) {
                return (Boolean) b;
            }
        } catch (Exception ex) {
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + em.getName() + ", Instance name : " + name + ", method Name : playerRevive:\n" + ex);
            System.out.println("Event name" + em.getName() + ", Instance name : " + name + ", method Name : playerRevive:\n" + ex);
        }

        return true;
    }

    public final void playerDisconnected(final MapleCharacter chr, final int idz)
    {
        if (this.disposed) {
            return;
        }

        byte ret;

        try {
            ret = ((Double) this.em.getIv().invokeFunction("playerDisconnected", this, chr)).byteValue();
        } catch (Exception e) {
            ret = 0;
        }

        try {
            this.wL.lock();

            if (this.disposed) {
                return;
            }

            this.dced.add(idz);

            if (chr != null) {
                this.unregisterPlayerNoLock(chr);
            }

            if (ret == 0) {
                if (this.getPlayerCount() <= 0) {
                    this.disposeNoLock();
                }
            } else if ((ret > 0 && this.getPlayerCount() < ret) || (ret < 0 && (this.isLeader(chr) || this.getPlayerCount() < (ret * -1)))) {
                final List<MapleCharacter> chrs = new LinkedList<>(this.chars);

                for (final MapleCharacter player : chrs) {
                    if (player.getId() != idz) {
                        this.removePlayer(player);
                    }
                }

                this.disposeNoLock();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            FileoutputUtil.outputFileError(FileoutputUtil.ScriptEx_Log, ex);
        } finally {
            this.wL.unlock();
        }
    }

    public final void monsterKilled(final MapleCharacter chr, final MapleMonster mob)
    {
        if (this.disposed) {
            return;
        }

        try {
            Integer kc = this.killCount.get(chr.getId());

            final int inc = (Integer) this.em.getIv().invokeFunction("monsterValue", this, mob.getId());

            if (this.disposed) {
                return;
            }

            if (kc == null) {
                kc = inc;
            } else {
                kc += inc;
            }

            this.killCount.put(chr.getId(), kc);

            if (chr.getCarnivalParty() != null && (mob.getStats().getPoint() > 0 || mob.getStats().getCP() > 0)) {
                this.em.getIv().invokeFunction("monsterKilled", this, chr, mob.getStats().getCP() > 0 ? mob.getStats().getCP() : mob.getStats().getPoint());
            }
        } catch (ScriptException ex) {
            System.out.println("Event name" + (em == null ? "null" : em.getName()) + ", Instance name : " + name + ", method Name : monsterValue:\n" + ex);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + (em == null ? "null" : em.getName()) + ", Instance name : " + name + ", method Name : monsterValue:\n" + ex);
        } catch (NoSuchMethodException ex) {
            System.out.println("Event name" + (em == null ? "null" : em.getName()) + ", Instance name : " + name + ", method Name : monsterValue:\n" + ex);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + (em == null ? "null" : em.getName()) + ", Instance name : " + name + ", method Name : monsterValue:\n" + ex);
        } catch (Exception ex) {
            ex.printStackTrace();
            FileoutputUtil.outputFileError(FileoutputUtil.ScriptEx_Log, ex);
        }
    }

    public final void monsterDamaged(final MapleCharacter chr, final MapleMonster mob, final int damage)
    {
        if (this.disposed || mob.getId() != 9700037) { //ghost PQ boss only.
            return;
        }

        try {
            this.em.getIv().invokeFunction("monsterDamaged", this, chr, mob.getId(), damage);
        } catch (ScriptException ex) {
            System.out.println("Event name" + (em == null ? "null" : em.getName()) + ", Instance name : " + name + ", method Name : monsterValue:\n" + ex);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + (em == null ? "null" : em.getName()) + ", Instance name : " + name + ", method Name : monsterValue:\n" + ex);
        } catch (NoSuchMethodException ex) {
            System.out.println("Event name" + (em == null ? "null" : em.getName()) + ", Instance name : " + name + ", method Name : monsterValue:\n" + ex);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + (em == null ? "null" : em.getName()) + ", Instance name : " + name + ", method Name : monsterValue:\n" + ex);
        } catch (Exception ex) {
            ex.printStackTrace();
            FileoutputUtil.outputFileError(FileoutputUtil.ScriptEx_Log, ex);
        }
    }

    public final int getKillCount(final MapleCharacter chr)
    {
        if (this.disposed) {
            return 0;
        }

        final Integer kc = this.killCount.get(chr.getId());

        if (kc == null) {
            return 0;
        } else {
            return kc;
        }
    }

    private void disposeNoLock()
    {
        if (this.disposed || this.em == null) {
            return;
        }

        final String emN = this.em.getName();

        try {
            this.disposed = true;

            for (final MapleCharacter chr : this.chars) {
                chr.setEventInstance(null);
            }

            this.chars.clear();
            this.chars = null;

            for (final MapleMonster mob : this.mobs) {
                mob.setEventInstance(null);
            }

            this.mobs.clear();
            this.mobs = null;

            this.killCount.clear();
            this.killCount = null;

            this.dced.clear();
            this.dced = null;

            this.timeStarted = 0;
            this.eventTime = 0;

            this.props.clear();
            this.props = null;

            for (int i = 0; i < this.mapIds.size(); i++) {
                if (this.isInstanced.get(i)) {
                    this.getMapFactory().removeInstanceMap(this.mapIds.get(i));
                }
            }

            this.mapIds.clear();
            this.mapIds = null;

            this.isInstanced.clear();
            this.isInstanced = null;

            this.em.disposeInstance(this.name);
        } catch (Exception e) {
            System.out.println("Caused by : " + emN + " instance name: " + name + " method: dispose: " + e);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + emN + ", Instance name : " + name + ", method Name : dispose:\n" + e);
        }
    }

    public final void dispose()
    {
        try {
            this.wL.lock();
            this.disposeNoLock();
        } finally {
            this.wL.unlock();
        }

    }

    public final ChannelServer getChannelServer()
    {
        return ChannelServer.getInstance(this.channel);
    }

    public final List<MapleMonster> getMobs()
    {
        return this.mobs;
    }

    public final void broadcastPlayerMsg(final int type, final String msg)
    {
        if (this.disposed) {
            return;
        }

        for (final MapleCharacter chr : getPlayers()) {
            chr.getClient().getSession().write(MaplePacketCreator.serverNotice(type, msg));
        }
    }

    public final MapleMap createInstanceMap(final int mapId)
    {
        if (this.disposed) {
            return null;
        }

        final int assignedId = this.getChannelServer().getEventSM().getNewInstanceMapId();

        this.mapIds.add(assignedId);
        this.isInstanced.add(true);

        return this.getMapFactory().CreateInstanceMap(mapId, true, true, true, assignedId);
    }

    public final MapleMap createInstanceMapS(final int mapId)
    {
        if (this.disposed) {
            return null;
        }

        final int assignedId = this.getChannelServer().getEventSM().getNewInstanceMapId();

        this.mapIds.add(assignedId);
        this.isInstanced.add(true);

        return this.getMapFactory().CreateInstanceMap(mapId, false, false, false, assignedId);
    }

    public final MapleMap setInstanceMap(final int mapId)
    {
        if (this.disposed) {
            return this.getMapFactory().getMap(mapId);
        }

        this.mapIds.add(mapId);
        this.isInstanced.add(false);

        return this.getMapFactory().getMap(mapId);
    }

    public final MapleMapFactory getMapFactory()
    {
        return this.getChannelServer().getMapFactory();
    }

    public final MapleMap getMapInstance(final int args)
    {
        if (this.disposed) {
            return null;
        }

        try {
            final MapleMap map;
            final int trueMapID;
            boolean instanced = false;

            if (args >= this.mapIds.size()) {
                //assume real map
                trueMapID = args;
            } else {
                trueMapID = this.mapIds.get(args);
                instanced = this.isInstanced.get(args);
            }

            if (!instanced) {
                map = this.getMapFactory().getMap(trueMapID);

                if (map == null) {
                    return null;
                }

                // in case reactors need shuffling and we are actually loading the map
                if (map.getCharactersSize() == 0) {
                    if (this.em.getProperty("shuffleReactors") != null && this.em.getProperty("shuffleReactors").equals("true")) {
                        map.shuffleReactors();
                    }
                }
            } else {
                map = this.getMapFactory().getInstanceMap(trueMapID);

                if (map == null) {
                    return null;
                }

                // in case reactors need shuffling and we are actually loading the map
                if (map.getCharactersSize() == 0) {
                    if (this.em.getProperty("shuffleReactors") != null && this.em.getProperty("shuffleReactors").equals("true")) {
                        map.shuffleReactors();
                    }
                }
            }

            return map;
        } catch (NullPointerException ex) {
            FileoutputUtil.outputFileError(FileoutputUtil.ScriptEx_Log, ex);
            ex.printStackTrace();
            return null;
        }
    }

    public final void schedule(final String methodName, final long delay)
    {
        if (this.disposed) {
            return;
        }

        EventTimer.getInstance().schedule(() -> {
            if (this.disposed || this.em == null) {
                return;
            }

            try {
                this.em.getIv().invokeFunction(methodName, EventInstanceManager.this);
            } catch (NullPointerException npe) {
            } catch (Exception ex) {
                System.out.println("Event name" + em.getName() + ", Instance name : " + name + ", method Name : " + methodName + ":\n" + ex);
                FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + em.getName() + ", Instance name : " + name + ", method Name(schedule) : " + methodName + " :\n" + ex);
            }
        }, delay);
    }

    public final String getName()
    {
        return this.name;
    }

    public final void setProperty(final String key, final String value)
    {
        if (this.disposed) {
            return;
        }

        this.props.setProperty(key, value);
    }

    public final Object setProperty(final String key, final String value, final boolean prev)
    {
        if (this.disposed) {
            return null;
        }

        return this.props.setProperty(key, value);
    }

    public final String getProperty(final String key)
    {
        if (this.disposed) {
            return "";
        }

        return this.props.getProperty(key);
    }

    public final Properties getProperties()
    {
        return this.props;
    }

    public final void leftParty(final MapleCharacter chr)
    {
        if (this.disposed) {
            return;
        }

        try {
            this.em.getIv().invokeFunction("leftParty", this, chr);
        } catch (Exception ex) {
            System.out.println("Event name" + em.getName() + ", Instance name : " + name + ", method Name : leftParty:\n" + ex);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + em.getName() + ", Instance name : " + name + ", method Name : leftParty:\n" + ex);
        }
    }

    public final void disbandParty()
    {
        if (this.disposed) {
            return;
        }

        try {
            this.em.getIv().invokeFunction("disbandParty", this);
        } catch (Exception ex) {
            System.out.println("Event name" + em.getName() + ", Instance name : " + name + ", method Name : disbandParty:\n" + ex);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + em.getName() + ", Instance name : " + name + ", method Name : disbandParty:\n" + ex);
        }
    }

    //Separate function to warp players to a "finish" map, if applicable
    public final void finishPQ()
    {
        if (this.disposed) {
            return;
        }

        try {
            this.em.getIv().invokeFunction("clearPQ", this);
        } catch (Exception ex) {
            System.out.println("Event name" + em.getName() + ", Instance name : " + name + ", method Name : clearPQ:\n" + ex);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + em.getName() + ", Instance name : " + name + ", method Name : clearPQ:\n" + ex);
        }
    }

    public final void removePlayer(final MapleCharacter chr)
    {
        if (this.disposed) {
            return;
        }

        try {
            this.em.getIv().invokeFunction("playerExit", this, chr);
        } catch (Exception ex) {
            System.out.println("Event name" + em.getName() + ", Instance name : " + name + ", method Name : playerExit:\n" + ex);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + em.getName() + ", Instance name : " + name + ", method Name : playerExit:\n" + ex);
        }
    }

    public final void registerCarnivalParty(final MapleCharacter leader, final MapleMap map, final byte team)
    {
        if (this.disposed) {
            return;
        }

        leader.clearCarnivalRequests();

        final List<MapleCharacter> characters = new LinkedList<>();
        final MapleParty party = leader.getParty();

        if (party == null) {
            return;
        }

        for (final MaplePartyCharacter pc : party.getMembers()) {
            final MapleCharacter c = map.getCharacterById(pc.getId());

            if (c != null) {
                characters.add(c);
                this.registerPlayer(c);
                c.resetCP();
            }
        }

        final MapleCarnivalParty carnivalParty = new MapleCarnivalParty(leader, characters, team);

        try {
            this.em.getIv().invokeFunction("registerCarnivalParty", this, carnivalParty);
        } catch (ScriptException ex) {
            System.out.println("Event name" + em.getName() + ", Instance name : " + name + ", method Name : registerCarnivalParty:\n" + ex);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + em.getName() + ", Instance name : " + name + ", method Name : registerCarnivalParty:\n" + ex);
        } catch (NoSuchMethodException ex) {
            //ignore
        }
    }

    public final void onMapLoad(final MapleCharacter chr)
    {
        if (this.disposed) {
            return;
        }

        try {
            this.em.getIv().invokeFunction("onMapLoad", this, chr);
        } catch (ScriptException ex) {
            System.out.println("Event name" + em.getName() + ", Instance name : " + name + ", method Name : onMapLoad:\n" + ex);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Event name" + em.getName() + ", Instance name : " + name + ", method Name : onMapLoad:\n" + ex);
        } catch (NoSuchMethodException ex) {
            // Ignore, we don't want to update this for all events.
        }
    }

    public final boolean isLeader(final MapleCharacter chr)
    {
        return (chr != null && chr.getParty() != null && chr.getParty().getLeader().getId() == chr.getId());
    }

    public final void registerSquad(final MapleSquad squad, final MapleMap map, final int questID)
    {
        if (this.disposed) {
            return;
        }

        final int mapId = map.getId();

        for (final String chr : squad.getMembers()) {
            final MapleCharacter player = squad.getChar(chr);
            if (player != null && player.getMapId() == mapId) {
                if (questID > 0) {
                    player.getOrAddQuest(MapleQuest.getInstance(questID)).setData(String.valueOf(System.currentTimeMillis()));
                }

                this.registerPlayer(player);
                /*                if (player.getParty() != null) {
                 PartySearch ps = World.Party.getSearch(player.getParty());
                 if (ps != null) {
                 World.Party.removeSearch(ps, "The Party Listing has been removed because the Party Quest has started.");
                 }
                 }*/
            }
        }

        squad.setStatus((byte) 2);
        squad.getBeginMap().broadcastMessage(MaplePacketCreator.stopClock());
    }

    public final boolean isDisconnected(final MapleCharacter chr)
    {
        if (this.disposed) {
            return false;
        }

        return this.dced.contains(chr.getId());
    }

    public final void removeDisconnected(final int id)
    {
        if (this.disposed) {
            return;
        }

        this.dced.remove(id);
    }

    public final EventManager getEventManager()
    {
        return this.em;
    }

    public final void applyBuff(final MapleCharacter chr, final int id)
{
        MapleItemInformationProvider.getInstance().getItemEffect(id).applyTo(chr);
        chr.getClient().getSession().write(UIPacket.getStatusMsg(id));
    }
}
