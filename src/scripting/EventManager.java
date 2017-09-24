/*
 * TMS 113 scripting/EventManager.java
 *
 * Copyright (C) 2017 ~ Present
 *
 * Patrick Huy <patrick.huy@frz.cc>
 * Matthias Butz <matze@odinms.de>
 * Jan Christian Meyer <vimes@odinms.de>
 * freedom <freedom@csie.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package scripting;

import client.MapleCharacter;
import handling.channel.ChannelServer;
import handling.world.MapleParty;
import server.MapleSquad;
import server.Randomizer;
import server.Timer;
import server.events.MapleEvent;
import server.events.MapleEventType;
import server.life.MapleLifeFactory;
import server.life.MapleMonster;
import server.life.OverrideMonsterStats;
import server.maps.MapleMap;
import server.maps.MapleMapFactory;
import server.maps.MapleMapObject;
import tools.FileoutputUtil;
import tools.MaplePacketCreator;

import javax.script.Invocable;
import javax.script.ScriptException;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

public class EventManager
{
    private final static int[] eventChannel = new int[2];
    private final Invocable iv;
    private final int channel;
    private final String name;
    private final Properties props = new Properties();
    private final Map<String, EventInstanceManager> instances = new WeakHashMap<>();

    public EventManager(final ChannelServer cserv, final Invocable iv, final String name)
    {
        this.iv = iv;
        this.channel = cserv.getChannel();
        this.name = name;
    }

    public final void cancel()
    {
        try {
            this.iv.invokeFunction("cancelSchedule", (Object) null);
        } catch (Exception e) {
            this.logError(this.name, "cancelSchedule", e);
        }
    }

    public final ScheduledFuture<?> schedule(final String methodName, final long delay)
    {
        return Timer.EventTimer.getInstance().schedule(() -> {
            try {
                this.iv.invokeFunction(methodName, (Object) null);
            } catch (Exception e) {
                this.logError(this.name, methodName, e);
            }
        }, delay);
    }

    public final ScheduledFuture<?> schedule(final String methodName, final long delay, final EventInstanceManager eim)
    {
        return Timer.EventTimer.getInstance().schedule(() -> {
            try {
                this.iv.invokeFunction(methodName, eim);
            } catch (Exception e) {
                this.logError(this.name, methodName, e);
            }
        }, delay);
    }

    public final ScheduledFuture<?> scheduleAtTimestamp(final String methodName, final long timestamp)
    {
        return Timer.EventTimer.getInstance().scheduleAtTimestamp(() -> {
            try {
                this.iv.invokeFunction(methodName, (Object) null);
            } catch (ScriptException | NoSuchMethodException e) {
                this.logError(this.name, methodName, e);
            }
        }, timestamp);
    }

    public final EventInstanceManager newInstance(final String name)
    {
        final EventInstanceManager ret = new EventInstanceManager(this, name, this.channel);

        this.instances.put(name, ret);

        return ret;
    }

    public final void disposeInstance(final String name)
    {
        this.instances.remove(name);

        if (this.getProperty("state") != null && this.instances.size() == 0) {
            this.setProperty("state", "0");
        }

        if (this.getProperty("leader") != null && this.instances.size() == 0 && this.getProperty("leader").equals("false")) {
            this.setProperty("leader", "true");
        }

        if (this.name.equals("CWKPQ")) { //hard code it because i said so
            final MapleSquad squad = ChannelServer.getInstance(this.channel).getMapleSquad("CWKPQ");

            if (squad != null) {
                squad.clear();
            }
        }
    }

    public final void startInstance()
    {
        try {
            this.iv.invokeFunction("setup", (Object) null);
        } catch (Exception e) {
            this.logError(this.name, "setup", e);
        }
    }

    public final void startInstance(final String mapId, final MapleCharacter chr)
    {
        try {
            ((EventInstanceManager) this.iv.invokeFunction("setup", (Object) mapId)).registerCarnivalParty(chr, chr.getMap(), (byte) 0);
        } catch (Exception e) {
            this.logError(this.name, "setup", e);
        }
    }

    public final void startInstanceParty(final String mapId, final MapleCharacter chr)
    {
        try {
            ((EventInstanceManager) this.iv.invokeFunction("setup", (Object) mapId)).registerParty(chr.getParty(), chr.getMap());
        } catch (Exception e) {
            this.logError(this.name, "setup", e);
        }
    }

    //GPQ
    public final void startInstance(final MapleCharacter character, final String leader)
    {
        try {
            final EventInstanceManager eim = (EventInstanceManager) (this.iv.invokeFunction("setup", (Object) null));

            eim.registerPlayer(character);
            eim.setProperty("leader", leader);
            eim.setProperty("guildid", String.valueOf(character.getGuildId()));

            this.setProperty("guildid", String.valueOf(character.getGuildId()));
        } catch (Exception e) {
            this.logError(this.name, "setup-Guild", e);
        }
    }

    public final void startInstanceCharID(final MapleCharacter character)
    {
        try {
            ((EventInstanceManager) (this.iv.invokeFunction("setup", character.getId()))).registerPlayer(character);
        } catch (Exception e) {
            this.logError(this.name, "setup-CharID", e);
        }
    }

    public final void startInstance(final MapleCharacter character)
    {
        try {
            ((EventInstanceManager) (this.iv.invokeFunction("setup", (Object) null))).registerPlayer(character);
        } catch (Exception e) {
            this.logError(this.name, "setup-character", e);
        }
    }

    //PQ method: starts a PQ
    public final void startInstance(final MapleParty party, final MapleMap map)
    {
        try {
            ((EventInstanceManager) (this.iv.invokeFunction("setup", party.getId()))).registerParty(party, map);
        } catch (Exception e) {
            this.logError(this.name, "setup-partyid", e);
        }
    }

    //non-PQ method for starting instance
    public final void startInstance(final EventInstanceManager eim, final String leader)
    {
        try {
            this.iv.invokeFunction("setup", eim);
            eim.setProperty("leader", leader);
        } catch (Exception e) {
            this.logError(this.name, "setup-leader", e);
        }
    }

    public final void startInstance(final MapleSquad squad, final MapleMap map)
    {
        this.startInstance(squad, map, -1);
    }

    public final void startInstance(final MapleSquad squad, final MapleMap map, final int questID)
    {
        if (squad.getStatus() == 0) {
            return; //we dont like cleared squads
        }

        if (!squad.getLeader().isGM()) {
            if (squad.getMembers().size() < squad.getType().i) { //less than 3
                squad.getLeader().dropMessage(5, "這個遠征隊至少要有 " + squad.getType().i + " 人以上才可以開戰");
                return;
            }

            if (this.name.equals("CWKPQ") && squad.getJobs().size() < 5) {
                squad.getLeader().dropMessage(5, "此遠征隊中需包含所有職業玩家");
                return;
            }
        }

        try {
            ((EventInstanceManager) (this.iv.invokeFunction("setup", squad.getLeaderName()))).registerSquad(squad, map, questID);
        } catch (Exception e) {
            this.logError(this.name, "setup-squad", e);
        }
    }

    public final void warpAllPlayer(final int from, final int to)
    {
        final MapleMap fromMap = this.getMapFactory().getMap(from);
        final MapleMap toMap = this.getMapFactory().getMap(to);
        final List<MapleCharacter> list = fromMap.getCharactersThreadsafe();

        if (toMap != null && list != null && fromMap.getCharactersSize() > 0) {
            for (final MapleMapObject mmo : list) {
                ((MapleCharacter) mmo).changeMap(toMap, toMap.getPortal(0));
            }
        }
    }

    public final MapleMapFactory getMapFactory()
    {
        return this.getChannelServer().getMapFactory();
    }

    public final OverrideMonsterStats newMonsterStats()
    {
        return new OverrideMonsterStats();
    }

    public final List<MapleCharacter> newCharList()
    {
        return new ArrayList<>();
    }

    public final MapleMonster getMonster(final int mobId)
    {
        return MapleLifeFactory.getMonster(mobId);
    }

    public final void broadcastShip(final int mapId, final int effect)
    {
        this.getMapFactory().getMap(mapId).broadcastMessage(MaplePacketCreator.boatEffect(effect));
    }

    public final void broadcastChangeMusic(final int mapId)
    {
        this.getMapFactory().getMap(mapId).broadcastMessage(MaplePacketCreator.musicChange("Bgm04/ArabPirate"));
    }

    public final void broadcastYellowMsg(final String msg)
    {
        this.getChannelServer().broadcastPacket(MaplePacketCreator.yellowChat(msg));
    }

    public final void broadcastServerMsg(final int type, final String msg, final boolean weather)
    {
        if (!weather) {
            this.getChannelServer().broadcastPacket(MaplePacketCreator.serverNotice(type, msg));
        } else {
            for (final MapleMap map : getMapFactory().getAllMaps()) {
                if (map.getCharactersSize() > 0) {
                    map.startMapEffect(msg, type);
                }
            }
        }
    }

    public final boolean scheduleRandomEvent()
    {
        boolean omg = false;

        for (final int channel : eventChannel) {
            omg |= this.scheduleRandomEventInChannel(channel);
        }

        return omg;
    }

    public final boolean scheduleRandomEventInChannel(final int channel)
    {
        final ChannelServer cs = ChannelServer.getInstance(channel);

        if (cs == null || cs.getEvent() > -1) {
            return false;
        }

        MapleEventType type = null;

        while (type == null) {
            for (final MapleEventType _type : MapleEventType.values()) {
                if (Randomizer.nextInt(MapleEventType.values().length) == 0 && _type != MapleEventType.OxQuiz/*選邊站*/) {
                    type = _type;
                    break;
                }
            }
        }

        final String msg = MapleEvent.scheduleEvent(type, cs);

        if (msg.length() > 0) {
            this.broadcastYellowMsg(msg);
            return false;
        }

        Timer.EventTimer.getInstance().schedule(() -> {
            if (cs.getEvent() >= 0) {
                MapleEvent.setEvent(cs, true);
            }
        }, 600000);

        return true;
    }

    public final void setWorldEvent()
    {
        for (int i = 0; i < eventChannel.length; i++) {
            eventChannel[i] = Randomizer.nextInt(ChannelServer.getAllInstances().size()) + i; //2-13
        }
    }

    public final int getChannel()
    {
        return this.channel;
    }

    public final ChannelServer getChannelServer()
    {
        return ChannelServer.getInstance(this.channel);
    }

    public final EventInstanceManager getInstance(final String name)
    {
        return this.instances.get(name);
    }

    public final Collection<EventInstanceManager> getInstances()
    {
        return Collections.unmodifiableCollection(this.instances.values());
    }

    public final Invocable getIv()
    {
        return this.iv;
    }

    public final String getName()
    {
        return this.name;
    }

    public final Properties getProperties()
    {
        return this.props;
    }

    public final String getProperty(final String key)
    {
        return this.props.getProperty(key);
    }

    public final void setProperty(final String key, final String value)
    {
        this.props.setProperty(key, value);
    }

    private void logError(final String name, final String method, final Exception e)
    {
        final String msg = "Event 腳本錯誤，name：" + name + " method：" + method + " " + e;

        System.err.println(msg);
        FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, msg);
    }
}
