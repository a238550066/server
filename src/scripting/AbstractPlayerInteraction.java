/*
 * TMS 113 scripting/AbstractPlayerInteraction.java
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

import client.*;
import client.inventory.Equip;
import client.inventory.MapleInventoryIdentifier;
import client.inventory.MapleInventoryType;
import client.inventory.MaplePet;
import constants.GameConstants;
import handling.MaplePacket;
import handling.channel.ChannelServer;
import handling.world.MapleParty;
import handling.world.MaplePartyCharacter;
import handling.world.World;
import handling.world.guild.MapleGuild;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import server.MapleStatEffect;
import server.Randomizer;
import server.events.MapleEvent;
import server.events.MapleEventType;
import server.life.MapleLifeFactory;
import server.life.MapleMonster;
import server.maps.EventDojoAgent;
import server.maps.MapleMap;
import server.maps.MapleReactor;
import server.maps.SavedLocationType;
import server.quests.MapleQuest;
import tools.MaplePacketCreator;
import tools.packet.PetPacket;
import tools.packet.UIPacket;

import java.awt.Point;
import java.util.List;

public abstract class AbstractPlayerInteraction
{
    final MapleClient c;

    public AbstractPlayerInteraction(final MapleClient c)
    {
        this.c = c;
    }

    /* 地圖相關 */

    public final void warp(final int mapId)
    {
        final MapleMap map = this.getChannelMap(mapId);

        this.getPlayer().changeMap(map, map.getPortal(0));
    }

    public final void warp(final int mapId, final int portal)
    {
        final MapleMap map = this.getChannelMap(mapId);

        this.getPlayer().changeMap(map, map.getPortal(portal));
    }

    public final void warp(final int mapId, String portal)
    {
        final MapleMap map = this.getChannelMap(mapId);

        if (mapId == 109060000 || mapId == 109060002 || mapId == 109060004) {
            portal = map.getSnowballPortal();
        }

        this.getPlayer().changeMap(map, map.getPortal(portal));
    }

    public final void warpMap(final int mapId, final int portal)
    {
        final MapleMap map = this.getMap(mapId);

        for (final MapleCharacter chr : this.getPlayer().getMap().getCharactersThreadsafe()) {
            chr.changeMap(map, map.getPortal(portal));
        }
    }

    public final MapleMap getMap()
    {
        return this.getPlayer().getMap();
    }

    public final MapleMap getMap(final int mapId)
    {
        return this.getChannelMap(mapId);
    }

    public final int getMapId()
    {
        return this.getPlayer().getMap().getId();
    }

    public final void resetMap(final int mapId)
    {
        this.getMap(mapId).resetFully();
    }

    public final void changeMusic(final String songName)
    {
        this.getMap().broadcastMessage(MaplePacketCreator.musicChange(songName));
    }

    public final int getPlayerCount(final int mapId)
    {
        return this.getMap(mapId).getCharactersSize();
    }

    public void showMapEffect(final String path)
    {
        this.writePacket(UIPacket.MapEff(path));
    }

    private MapleMap getChannelMap(final int mapId)
    {
        return this.getChannelServer().getMapFactory().getMap(mapId);
    }

    private MapleMap getMapInstanced(final int mapId)
    {
        if (this.getEventInstance() == null) {
            return this.getMap(mapId);
        }

        return this.getEventInstance().getMapInstance(mapId);
    }

    // @todo not work, need fix
    public final void playPortalSE()
    {
        this.writePacket(MaplePacketCreator.showOwnBuffEffect(0, 7));
    }

    /* 怪物相關 */

    public final int getMonsterCount(final int mapId)
    {
        return this.getMap(mapId).getNumMonsters();
    }

    public final boolean haveMonster(final int mobId)
    {
        for (final MapleMonster mob : this.getMap().getAllMonstersThreadsafe()) {
            if (mob.getId() == mobId) {
                return true;
            }
        }

        return false;
    }

    public void spawnMonster(final int mobId)
    {
        this.spawnMonster(mobId, 1, new Point(this.getPlayer().getPosition()));
    }

    public void spawnMonster(final int mobId, final int quantity)
    {
        this.spawnMonster(mobId, quantity, new Point(this.getPlayer().getPosition()));
    }

    public void spawnMonster(final int mobId, final int x, final int y)
    {
        this.spawnMonster(mobId, 1, new Point(x, y));
    }

    public void spawnMonster(final int mobId, final int quantity, final int x, final int y)
    {
        this.spawnMonster(mobId, quantity, new Point(x, y));
    }

    public void spawnMonster(final int mobId, final int quantity, final Point pos)
    {
        for (int i = 0; i < quantity; i++) {
            this.getMap().spawnMonsterOnGroundBelow(MapleLifeFactory.getMonster(mobId), pos);
        }
    }

    public final void killAllMob()
    {
        this.getMap().killAllMonsters(true);
    }

    /* 玩家相關 */

    public final void addHP(final int delta)
    {
        this.getPlayer().addHP(delta);
    }

    public final String getName()
    {
        return this.getPlayer().getName();
    }

    public final int getJob()
    {
        return this.getPlayer().getJob();
    }

    public final int getMorphState()
    {
        return this.getPlayer().getMorphState();
    }

    public final int getPlayerStat(final String type)
    {
        final MapleCharacter player = this.getPlayer();
        
        switch (type) {
            case "LVL":
                return player.getLevel();
            case "STR":
                return player.getStat().getStr();
            case "DEX":
                return player.getStat().getDex();
            case "INT":
                return player.getStat().getInt();
            case "LUK":
                return player.getStat().getLuk();
            case "HP":
                return player.getStat().getHp();
            case "MP":
                return player.getStat().getMp();
            case "MAXHP":
                return player.getStat().getMaxHp();
            case "MAXMP":
                return player.getStat().getMaxMp();
            case "RAP":
                return player.getRemainingAp();
            case "RSP":
                return player.getRemainingSp();
            case "GID":
                return player.getGuildId();
            case "GRANK":
                return player.getGuildRank();
            case "ARANK":
                return player.getAllianceRank();
            case "GM":
                return player.isGM() ? 1 : 0;
            case "ADMIN":
                return player.isAdmin() ? 1 : 0;
            case "GENDER":
                return player.getGender();
            case "FACE":
                return player.getFace();
            case "HAIR":
                return player.getHair();
            default:
                return -1;
        }
    }

    public void gainMeso(final int amount)
    {
        this.getPlayer().gainMeso(amount * this.getChannelServer().getMesoRate(), true, false, true);
    }

    public void gainExp(final int amount)
    {
        this.getPlayer().gainExp(amount * this.getChannelServer().getExpRate(), true, true, true);
    }

    public final void gainMaplePoint(final int amount)
    {
        this.getPlayer().modifyCSPoints(2, amount, true);
    }

    /* 物品相關 */

    public final String getItemName(final int itemId)
    {
        return MapleItemInformationProvider.getInstance().getName(itemId);
    }

    public final int itemQuantity(final int itemId)
    {
        return this.getPlayer().itemQuantity(itemId);
    }

    public final boolean haveItem(final int itemId)
    {
        return this.haveItem(itemId, 1);
    }

    public final boolean haveItem(final int itemId, final int quantity)
    {
        return this.haveItem(itemId, quantity, false, true);
    }

    public final boolean haveItem(final int itemId, final int quantity, final boolean checkEquipped, final boolean greaterOrEquals)
    {
        return this.getPlayer().haveItem(itemId, quantity, checkEquipped, greaterOrEquals);
    }

    public final boolean canHold()
    {
        for (int i = 1; i <= 5; i++) {
            if (this.getPlayer().getInventory(MapleInventoryType.getByType((byte) i)).getNextFreeSlot() <= -1) {
                return false;
            }
        }

        return true;
    }

    public final boolean canHold(final int itemId)
    {
        return this.getPlayer().getInventory(GameConstants.getInventoryType(itemId)).getNextFreeSlot() > -1;
    }

    public final boolean canHold(final int itemId, final int quantity)
    {
        return MapleInventoryManipulator.checkSpace(this.getClient(), itemId, quantity, "");
    }

    /**
     * @implNote period is in days
     */
    public final void gainItemPeriod(final int itemId, final short quantity, final long period)
    {
        this.gainItem(itemId, quantity, false, period, -1, null);
    }

    public final void gainItemPeriod(final int itemId, final short quantity, final long period, final String owner)
    {
        this.gainItem(itemId, quantity, false, period, -1, owner);
    }

    public final void gainItem(final int itemId, final short quantity)
    {
        this.gainItem(itemId, quantity, false, 0, -1, null);
    }

    public final void gainItem(final int itemId, final short quantity, final boolean randomStats)
    {
        this.gainItem(itemId, quantity, randomStats, 0, -1, null);
    }

    public final void gainItem(final int itemId, final short quantity, final boolean randomStats, final int slots)
    {
        this.gainItem(itemId, quantity, randomStats, 0, slots, null);
    }

    public final void gainItem(final int itemId, final short quantity, final long period)
    {
        this.gainItem(itemId, quantity, false, period, -1, null);
    }

    public final void gainItem(final int itemId, final short quantity, final boolean randomStats, final long period, final int slots)
    {
        this.gainItem(itemId, quantity, randomStats, period, slots, null);
    }

    public final void gainItem(final int itemId, final short quantity, final boolean randomStats, final long period, final int slots, final String owner)
    {
        this.gainItem(itemId, quantity, randomStats, period, slots, owner, this.getClient());
    }

    public final void gainItem(final int itemId, final short quantity, final boolean randomStats, final long period, final int slots, final String owner, final MapleClient client)
    {
        if (quantity < 0) { // 移除物品
            MapleInventoryManipulator.removeById(client, GameConstants.getInventoryType(itemId), itemId, -quantity, true, false);
        } else { // 新增物品
            final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
            final MapleInventoryType type = GameConstants.getInventoryType(itemId);

            if (!MapleInventoryManipulator.checkSpace(client, itemId, quantity, "")) {
                return;
            }

            if (!type.equals(MapleInventoryType.EQUIP) || GameConstants.isThrowingStar(itemId) || GameConstants.isBullet(itemId)) {
                MapleInventoryManipulator.addById(client, itemId, quantity, owner == null ? "" : owner, null, period);
            } else {
                final Equip item = (Equip) (randomStats ? ii.randomizeStats((Equip) ii.getEquipById(itemId)) : ii.getEquipById(itemId));

                if (period > 0) {
                    item.setExpiration(System.currentTimeMillis() + (period * 24 * 60 * 60 * 1000));
                }

                if (slots > 0) {
                    item.setUpgradeSlots((byte) (item.getUpgradeSlots() + slots));
                }

                if (owner != null) {
                    item.setOwner(owner);
                }

                final String name = ii.getName(itemId);

                if (itemId / 10000 == 114 && name != null && name.length() > 0) { // 勳章
                    final String msg = "你已獲得稱號 <" + name + ">";

                    client.getPlayer().dropMessage(-1, msg);
                    client.getPlayer().dropMessage(5, msg);
                }

                MapleInventoryManipulator.addbyItem(client, item.copy());
            }
        }

        client.getSession().write(MaplePacketCreator.getShowItemGain(itemId, quantity, true));
    }

    public final void useItem(final int itemId)
    {
        final MapleStatEffect effect = MapleItemInformationProvider.getInstance().getItemEffect(itemId);

        if (effect == null) {
            // @todo log error
            return;
        }

        effect.applyTo(this.getPlayer());
        this.writePacket(UIPacket.getStatusMsg(itemId));
    }

    public final void cancelItem(final int itemId)
    {
        this.getPlayer().cancelEffect(MapleItemInformationProvider.getInstance().getItemEffect(itemId), false, -1);
    }

    public final void removeAll(final int itemId)
    {
        this.getPlayer().removeAll(itemId);
    }

    public final void removeSlot(final int invType, final byte slot, final short quantity)
    {
        MapleInventoryManipulator.removeFromSlot(this.getClient(), this.getInvType(invType), slot, quantity, true);
    }

    public MapleInventoryType getInvType(final int i)
    {
        return MapleInventoryType.getByType((byte) i);
    }

    /* 寵物相關 */

    public final void gainPet(int petId, final String name, int level, int closeness, int fullness, final long period , final short flag)
    {
        if (petId < 5000000 || petId > 5000200) {
            petId = 5000000;
        }

        if (level > 30) {
            level = 30;
        }

        if (closeness > 30000) {
            closeness = 30000;
        }

        if (fullness > 100) {
            fullness = 100;
        }

        try {
            MapleInventoryManipulator.addById(this.getClient(), petId, (short) 1, null, MaplePet.createPet(petId, name, level, closeness, fullness, MapleInventoryIdentifier.getInstance(), petId == 5000054 ? (int) period : 0, flag), 45);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    public final void gainClosenessAll(final int closeness)
    {
        for (final MaplePet pet : this.getPlayer().getPets()) {
            if (pet.getSummoned()) {
                pet.setCloseness(pet.getCloseness() + closeness);
                this.writePacket(PetPacket.updatePet(pet, this.getPlayer().getInventory(MapleInventoryType.CASH).getItem((byte) pet.getInventoryPosition())));
            }
        }
    }

    /* 任務相關 */

    public final MapleQuestStatus getQuestRecord(final int questId)
    {
        return this.getPlayer().getOrAddQuest(MapleQuest.getInstance(questId));
    }

    public final boolean isQuestActive(final int questId)
    {
        return this.getQuestStatus(questId) == 1;
    }

    public final boolean isQuestFinished(final int questId)
    {
        return this.getQuestStatus(questId) == 2;
    }

    public final byte getQuestStatus(final int questId)
    {
        return this.getPlayer().getQuestStatus(questId);
    }

    public final void showQuestMsg(final String msg)
    {
        this.writePacket(MaplePacketCreator.showQuestMsg(msg));
    }

    public final void forceStartQuest(final int questId, final String data)
    {
        final MapleQuest quest = this.getQuest(questId);

        if (quest == null) {
            return;
        }

        quest.forceStart(this.getPlayer(), 0, data);
    }

    public final void forceStartQuest(final int questId, final int data, final boolean filler)
    {
        final MapleQuest quest = this.getQuest(questId);

        if (quest == null) {
            return;
        }

        quest.forceStart(this.getPlayer(), 0, filler ? String.valueOf(data) : null);
    }

    public void forceStartQuest(final int questId)
    {
        final MapleQuest quest = this.getQuest(questId);

        if (quest == null) {
            return;
        }

        quest.forceStart(this.getPlayer(), 0, null);
    }

    public void forceCompleteQuest(final int questId)
    {
        final MapleQuest quest = this.getQuest(questId);

        if (quest == null) {
            return;
        }

        quest.forceComplete(getPlayer(), 0);
    }

    final MapleQuest getQuest(final int questId)
    {
        final MapleQuest quest = MapleQuest.getInstance(questId);

        if (quest == null) {
            // @todo log error
        }

        return quest;
    }

    /* NPC 相關 */

    public final void openNpc(final int npcId)
    {
        NPCScriptManager.getInstance().start(this.getClient(), npcId);
    }

    public final void openNpc(final MapleClient client, final int npcId)
    {
        NPCScriptManager.getInstance().start(client, npcId);
    }

    public void spawnNpc(final int npcId)
    {
        this.getMap().spawnNpc(npcId, this.getPlayer().getPosition());
    }

    public final void spawnNpc(final int npcId, final int x, final int y)
    {
        this.getMap().spawnNpc(npcId, new Point(x, y));
    }

    public final void spawnNpc(final int npcId, final Point pos)
    {
        this.getMap().spawnNpc(npcId, pos);
    }

    public final void removeNpc(final int mapId, final int npcId)
    {
        this.getChannelMap(mapId).removeNpc(npcId);
    }

    public void sendNPCText(final String text, final int npc)
    {
        this.getMap().broadcastMessage(MaplePacketCreator.getNPCTalk(npc, (byte) 0, text, "00 00", (byte) 0));
    }

    /* reactor 相關指令 */

    public final void forceStartReactor(final int mapId, final int id)
    {
        for (final MapleReactor reactor : this.getChannelMap(mapId).getAllReactorsThreadsafe()) {
            if (reactor.getReactorId() == id) {
                reactor.forceStartReactor(this.getClient());
                break;
            }
        }
    }

    public final void destroyReactor(final int mapId, final int id)
    {
        for (final MapleReactor reactor : this.getChannelMap(mapId).getAllReactorsThreadsafe()) {
            if (reactor.getReactorId() == id) {
                reactor.sendDestroyData(this.getClient());
                break;
            }
        }
    }

    public final void hitReactor(final int mapId, final int id)
    {
        for (final MapleReactor reactor : this.getChannelMap(mapId).getAllReactorsThreadsafe()) {
            if (reactor.getReactorId() == id) {
                reactor.hitReactor(this.getClient());
                break;
            }
        }
    }

    public boolean isAllReactorState(final int reactorId, final int state)
    {
        for (final MapleReactor reactor : this.getMap().getAllReactorsThreadsafe()) {
            if (reactor.getReactorId() == reactorId) {
                if (reactor.getState() != state) {
                    return false;
                }
            }
        }

        return true;
    }

    /* 訊息相關 */

    public final void worldMessage(final int type, final String message)
    {
        World.Broadcast.broadcastMessage(MaplePacketCreator.serverNotice(type, message).getBytes());
    }

    public final void playerMessage(final String message)
    {
        this.playerMessage(5, message);
    }

    public final void playerMessage(final int type, final String message)
    {
        this.writePacket(MaplePacketCreator.serverNotice(type, message));
    }

    public final void mapMessage(final String message)
    {
        this.mapMessage(5, message);
    }

    public final void mapMessage(final int type, final String message)
    {
        this.getMap().broadcastMessage(MaplePacketCreator.serverNotice(type, message));
    }

    public final void guildMessage(final String message)
    {
        this.guildMessage(5, message);
    }

    public final void guildMessage(final int type, final String message)
    {
        if (this.getPlayer().getGuildId() > 0) {
            World.Guild.guildPacket(this.getPlayer().getGuildId(), MaplePacketCreator.serverNotice(type, message));
        }
    }

    /* 社群相關 */

    public final MapleGuild getGuild()
    {
        return this.getGuild(this.getPlayer().getGuildId());
    }

    public final MapleGuild getGuild(int guildId)
    {
        return World.Guild.getGuild(guildId);
    }

    public final void gainGP(final int amount)
    {
        if (this.getPlayer().getGuildId() <= 0) {
            return;
        }

        World.Guild.gainGP(this.getPlayer().getGuildId(), amount);
    }

    public final MapleParty getParty()
    {
        return this.getPlayer().getParty();
    }

    public final boolean isLeader()
    {
        return this.getParty() != null && this.getParty().getLeader().getId() == this.getPlayer().getId();
    }

    /**
     * 所有隊員是否都為特定職業
     */
    public final boolean isAllPartyMembersAllowedJob(final int job)
    {
        if (this.getParty() == null) {
            return false;
        }

        for (final MaplePartyCharacter chr : this.getParty().getMembers()) {
            if ((chr.getJobId() / 100) != job) {
                return false;
            }
        }

        return true;
    }

    /**
     * 所有隊員是否都在相同地圖
     */
    public final boolean isAllMembersHere()
    {
        if (this.getParty() == null) {
            return false;
        }

        for (final MaplePartyCharacter chr : this.getParty().getMembers()) {
            if (this.getMapId() != chr.getMapid()) {
                return false;
            }
        }

        return true;
    }

    public final void warpParty(final int mapId)
    {
        if (!this.isPartyHasMembers()) {
            this.warp(mapId, 0);
            return;
        }

        final MapleMap map = this.getMap(mapId);
        final int currentMap = this.getMapId();

        for (final MaplePartyCharacter mem : this.getParty().getMembers()) {
            final MapleCharacter chr = this.getChannelServer().getPlayerStorage().getCharacterById(mem.getId());

            if (chr != null && (chr.getMapId() == currentMap || chr.getEventInstance() == this.getEventInstance())) {
                chr.changeMap(map, map.getPortal(0));
            }
        }
    }

    public final void warpParty(final int mapId, final int portal)
    {
        if (!this.isPartyHasMembers()) {
            this.warp(mapId, Math.max(portal, 0));
            return;
        }

        final boolean rand = portal < 0;
        final MapleMap map = this.getMap(mapId);
        final int currentMap = this.getMapId();

        for (final MaplePartyCharacter mem : this.getParty().getMembers()) {
            final MapleCharacter chr = this.getChannelServer().getPlayerStorage().getCharacterById(mem.getId());

            if (chr != null && (chr.getMapId() == currentMap || chr.getEventInstance() == this.getEventInstance())) {
                if (!rand) {
                    chr.changeMap(map, map.getPortal(portal));
                } else {
                    try {
                        chr.changeMap(map, map.getPortal(Randomizer.nextInt(map.getPortals().size())));
                    } catch (Exception e) {
                        chr.changeMap(map, map.getPortal(0));
                    }
                }
            }
        }
    }

    public final void warpPartyInstanced(final int mapId)
    {
        final MapleMap map = this.getMapInstanced(mapId);

        if (!this.isPartyHasMembers()) {
            this.getPlayer().changeMap(map, map.getPortal(0));
            return;
        }

        final int currentMap = getPlayer().getMapId();

        for (final MaplePartyCharacter mem : getPlayer().getParty().getMembers()) {
            final MapleCharacter chr = this.getChannelServer().getPlayerStorage().getCharacterById(mem.getId());

            if (chr != null && (chr.getMapId() == currentMap || chr.getEventInstance() == this.getEventInstance())) {
                chr.changeMap(map, map.getPortal(0));
            }
        }
    }

    public final void givePartyItems(final int itemId, final short quantity)
    {
        this.givePartyItems(itemId, quantity, false);
    }

    public final void givePartyItems(final int itemId, final short quantity, final boolean removeAll)
    {
        if (!this.isPartyHasMembers()) {
            this.gainItem(itemId, (short) (removeAll ? -this.getPlayer().itemQuantity(itemId) : quantity));
            return;
        }

        for (final MaplePartyCharacter mem : this.getParty().getMembers()) {
            final MapleCharacter chr = this.getMap().getCharacterById(mem.getId());

            if (chr != null) {
                this.gainItem(itemId, (short) (removeAll ? -chr.itemQuantity(itemId) : quantity), false, 0, 0, null, chr.getClient());
            }
        }
    }

    public final void givePartyExp(final int amount, final List<MapleCharacter> party)
    {
        for (final MapleCharacter chr : party) {
            chr.gainExp(amount * this.getChannelServer().getExpRate(), true, true, true);
        }
    }

    public final void givePartyExp(final int amount)
    {
        if (!this.isPartyHasMembers()) {
            this.gainExp(amount * this.getChannelServer().getExpRate());
            return;
        }

        for (final MaplePartyCharacter mem : this.getParty().getMembers()) {
            final MapleCharacter chr = this.getMap().getCharacterById(mem.getId());

            if (chr != null) {
                chr.gainExp(amount * this.getChannelServer().getExpRate(), true, true, true);
            }
        }
    }

    public final void endPartyQuest(final int questId, final List<MapleCharacter> party)
    {
        for (final MapleCharacter chr : party) {
            chr.endPartyQuest(questId);
        }
    }

    public final void endPartyQuest(final int questId)
    {
        if (!this.isPartyHasMembers()) {
            this.getPlayer().endPartyQuest(questId);
            return;
        }

        for (final MaplePartyCharacter mem : this.getParty().getMembers()) {
            final MapleCharacter chr = getMap().getCharacterById(mem.getId());

            if (chr != null) {
                chr.endPartyQuest(questId);
            }
        }
    }

    public final void removeFromParty(final int itemId, final List<MapleCharacter> party)
    {
        for (final MapleCharacter chr : party) {
            final int possessed = chr.getInventory(GameConstants.getInventoryType(itemId)).countById(itemId);

            if (possessed > 0) {
                MapleInventoryManipulator.removeById(chr.getClient(), GameConstants.getInventoryType(itemId), itemId, possessed, true, false);
                chr.getClient().getSession().write(MaplePacketCreator.getShowItemGain(itemId, (short) -possessed, true));
            }
        }
    }

    private boolean isPartyHasMembers()
    {
        return this.getParty() != null && this.getParty().getMembers().size() > 1;
    }

    /* 技能相關 */

    public final void teachSkill(final int skillId, final byte level, final byte masterLevel)
    {
        this.getPlayer().changeSkillLevel(SkillFactory.getSkill(skillId), level, masterLevel);
    }

    public final void teachSkill(final int skillId, final byte level)
    {
        final ISkill skill = SkillFactory.getSkill(skillId);

        if (skill == null) {
            // @todo log error
            return;
        }

        if (this.getPlayer().getSkillLevel(skill) > level) {
            return;
        }

        this.getPlayer().changeSkillLevel(skill, level, skill.getMaxLevel());
    }

    /* 武陵道場相關 */

    public final void dojoGetUp()
    {
        this.writePacket(MaplePacketCreator.updateInfoQuest(1207, "pt=1;min=4;belt=1;tuto=1")); //todo
        this.writePacket(MaplePacketCreator.Mulung_DojoUp2());
        this.writePacket(MaplePacketCreator.instantMapWarp((byte) 6));
    }

    /**
     * 武陵道場 - 下一關
     */
    public final boolean nextDojo(final boolean fromResting)
    {
        return EventDojoAgent.next(this.getPlayer(), fromResting, this.getMap());
    }

    /**
     * 武陵道場 - 下一關
     */
    public final boolean nextDojo(final boolean fromResting, final int mapId)
    {
        return EventDojoAgent.next(this.getPlayer(), fromResting, this.getMap(mapId));
    }

    /* 活動相關 */

    public final MapleEvent getEvent(final String loc)
    {
        return this.getChannelServer().getEvent(MapleEventType.valueOf(loc));
    }

    public final EventInstanceManager getDisconnected(final String event)
    {
        final EventManager em = this.getEventManager(event);

        if (em == null) {
            return null;
        }

        for (final EventInstanceManager eim : em.getInstances()) {
            if (eim.isDisconnected(this.getPlayer()) && eim.getPlayerCount() > 0) {
                return eim;
            }
        }

        return null;
    }

    public final String getInfoQuest(final int id)
    {
        return this.getPlayer().getQuestData(id);
    }

    public final boolean getEvanIntroState(final String data)
    {
        return this.getInfoQuest(22013).equals(data);
    }

    public final void updateEvanIntroState(final String data)
    {
        this.updateInfoQuest(22013, data);
    }

    public final void updateInfoQuest(final int id, final String data)
    {
        this.getPlayer().updateQuestData(id, data);
    }

    public final int getSavedLocation(final String loc)
    {
        final Integer ret = this.getPlayer().getSavedLocation(SavedLocationType.fromString(loc));

        if (ret == -1) {
            return 100000000;
        }

        return ret;
    }

    public final void saveLocation(final String loc)
    {
        this.getPlayer().saveLocation(SavedLocationType.fromString(loc));
    }

    public final void saveReturnLocation(final String loc)
    {
        this.getPlayer().saveLocation(SavedLocationType.fromString(loc), this.getMap().getReturnMap().getId());
    }

    public final void clearSavedLocation(final String loc)
    {
        this.getPlayer().clearSavedLocation(SavedLocationType.fromString(loc));
    }

    /* 其他 */

    public final void summonMsg(final String msg)
    {
        if (!this.getPlayer().hasSummon()) {
            this.playerSummonHint(true);
        }

        this.writePacket(UIPacket.summonMessage(msg));
    }

    public final void summonMsg(final int type)
    {
        if (!this.getPlayer().hasSummon()) {
            playerSummonHint(true);
        }

        this.writePacket(UIPacket.summonMessage(type));
    }

    public final void playerSummonHint(final boolean summon)
    {
        this.getPlayer().setHasSummon(summon);
        this.writePacket(UIPacket.summonHelper(summon));
    }

    public final void showInstruction(final String msg, final int width, final int height)
    {
        this.writePacket(MaplePacketCreator.sendHint(msg, width, height));
    }

    public final void aranStart()
    {
        this.writePacket(UIPacket.Aran_Start());
    }

    public final void aranTutInstructionalBubble(final String data)
    {
        this.writePacket(UIPacket.AranTutInstructionalBalloon(data));
    }

    public final void evanTutorial(final String data, final int v1)
    {
        this.writePacket(MaplePacketCreator.getEvanTutorial(data));
    }

    public final void movieClipIntroUI(final boolean enabled)
    {
        this.writePacket(UIPacket.IntroDisableUI(enabled));
        this.writePacket(UIPacket.IntroLock(enabled));
    }

    public final void ShowWZEffect(final String data)
    {
        this.writePacket(UIPacket.AranTutInstructionalBalloon(data));
    }

    public final void showWZEffect(final String data)
    {
        this.writePacket(UIPacket.ShowWZEffect(data));
    }

    public final MapleClient getClient()
    {
        return this.c;
    }

    public final MapleCharacter getPlayer()
    {
        return this.getClient().getPlayer();
    }

    public final int getChannelNumber()
    {
        return this.getClient().getChannel();
    }

    public final ChannelServer getChannelServer()
    {
        return this.getClient().getChannelServer();
    }

    public final EventInstanceManager getEventInstance()
    {
        return this.getPlayer().getEventInstance();
    }

    public final EventManager getEventManager(final String event)
    {
        return this.getChannelServer().getEventSM().getEventManager(event);
    }

    public final long getCurrentTime()
    {
        return System.currentTimeMillis();
    }

    void writePacket(final MaplePacket packet)
    {
        this.getClient().getSession().write(packet);
    }
}
