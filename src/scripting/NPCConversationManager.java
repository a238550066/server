/*
 * TMS 113 scripting/NPCConversationManager.java
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
import client.inventory.IItem;
import client.inventory.MapleInventory;
import client.inventory.MapleInventoryType;
import constants.GameConstants;
import handling.channel.ChannelServer;
import handling.channel.MapleGuildRanking;
import handling.channel.handler.HiredFishingHandler;
import handling.world.MapleParty;
import handling.world.MaplePartyCharacter;
import handling.world.World;
import handling.world.guild.MapleGuild;
import handling.world.guild.MapleGuildAlliance;
import server.*;
import server.Timer;
import server.maps.*;
import server.quests.MapleQuest;
import tools.MaplePacketCreator;
import tools.Pair;
import tools.StringUtil;
import tools.packet.PlayerShopPacket;

import javax.script.Invocable;
import java.util.*;

public class NPCConversationManager extends AbstractPlayerInteraction
{
    boolean pendingDisposal = false;
    private final int npcId;
    private final int questId;
    private final byte type; // -1 = NPC, 0 = start quest, 1 = end quest
    private final Invocable iv;
    private byte lastMsg = -1;
    private String getText;

    public NPCConversationManager(final MapleClient c, final int npcId, final int questId, final byte type, final Invocable iv)
    {
        super(c);

        this.npcId = npcId;
        this.questId = questId;
        this.type = type;
        this.iv = iv;
    }

    /* NPC 對話相關 */

    public final void sendNext(final String text)
    {
        if (this.lastMsg > -1) {
            return;
        }

        this.writePacket(MaplePacketCreator.getNPCTalk(this.getNpc(), (byte) 0, text, "00 01", (byte) 0));
        this.lastMsg = 0;
    }

    public final void sendNextS(final String text, final byte type)
    {
        if (this.lastMsg > -1) {
            return;
        }

        this.writePacket(MaplePacketCreator.getNPCTalk(this.getNpc(), (byte) 0, text, "00 01", type));
        this.lastMsg = 0;
    }

    public final void sendPrev(final String text)
    {
        if (this.lastMsg > -1) {
            return;
        }

        this.writePacket(MaplePacketCreator.getNPCTalk(this.getNpc(), (byte) 0, text, "01 00", (byte) 0));
        this.lastMsg = 0;
    }

    public final void sendPrevS(final String text, final byte type)
    {
        if (this.lastMsg > -1) {
            return;
        }

        this.writePacket(MaplePacketCreator.getNPCTalk(this.getNpc(), (byte) 0, text, "01 00", type));
        this.lastMsg = 0;
    }

    public final void sendNextPrev(final String text)
    {
        if (this.lastMsg > -1) {
            return;
        }

        this.writePacket(MaplePacketCreator.getNPCTalk(this.getNpc(), (byte) 0, text, "01 01", (byte) 0));
        this.lastMsg = 0;
    }

    public final void sendNextPrevS(final String text)
    {
        this.sendNextPrevS(text, (byte) 3);
    }

    public final void sendPlayerToNpc(final String text)
    {
        this.sendNextPrevS(text, (byte) 3);
    }

    public final void sendNextPrevS(final String text, final byte type)
    {
        if (this.lastMsg > -1) {
            return;
        }

        this.writePacket(MaplePacketCreator.getNPCTalk(this.getNpc(), (byte) 0, text, "01 01", type));
        this.lastMsg = 0;
    }

    public final void sendOk(final String text)
    {
        if (this.lastMsg > -1) {
            return;
        }

        this.writePacket(MaplePacketCreator.getNPCTalk(this.getNpc(), (byte) 0, text, "00 00", (byte) 0));
        this.lastMsg = 0;
    }

    public final void sendOkS(final String text, final byte type)
    {
        if (this.lastMsg > -1) {
            return;
        }

        this.writePacket(MaplePacketCreator.getNPCTalk(this.getNpc(), (byte) 0, text, "00 00", type));
        this.lastMsg = 0;
    }

    public final void sendYesNo(final String text)
    {
        if (this.lastMsg > -1) {
            return;
        }

        this.writePacket(MaplePacketCreator.getNPCTalk(this.getNpc(), (byte) 1, text, "", (byte) 0));
        this.lastMsg = 1;
    }

    public final void sendYesNoS(final String text, final byte type)
    {
        if (this.lastMsg > -1) {
            return;
        }

        this.writePacket(MaplePacketCreator.getNPCTalk(this.getNpc(), (byte) 1, text, "", type));
        this.lastMsg = 1;
    }

    public final void sendAcceptDecline(final String text)
    {
        if (this.lastMsg > -1) {
            return;
        }

        this.writePacket(MaplePacketCreator.getNPCTalk(this.getNpc(), (byte) 0x0B, text, "", (byte) 0));
        this.lastMsg = 0xB;
    }

    public void sendSimple(final String text)
    {
        if (this.lastMsg > -1) {
            return;
        }

        if (!text.contains("#L")) { //sendSimple will dc otherwise!
            this.sendNext(text);
            return;
        }

        this.writePacket(MaplePacketCreator.getNPCTalk(this.getNpc(), (byte) 4, text, "", (byte) 0));
        this.lastMsg = 4;
    }

    public final void sendStyle(final String text, final int styles[])
    {
        if (this.lastMsg > -1) {
            return;
        }

        this.writePacket(MaplePacketCreator.getNPCTalkStyle(this.getNpc(), text, styles));
        this.lastMsg = 7;
    }

    public final void sendGetNumber(final String text, final int def, final int min, final int max)
    {
        if (this.lastMsg > -1) {
            return;
        }

        this.writePacket(MaplePacketCreator.getNPCTalkNum(this.getNpc(), text, def, min, max));
        this.lastMsg = 3;
    }

    public final void sendGetText(final String text)
    {
        if (this.lastMsg > -1) {
            return;
        }

        this.writePacket(MaplePacketCreator.getNPCTalkText(this.getNpc(), text));
        this.lastMsg = 2;
    }

    public final void askAvatar(final String text, final int... args)
    {
        if (this.lastMsg > -1) {
            return;
        }

        this.writePacket(MaplePacketCreator.getNPCTalkStyle(this.getNpc(), text, args));
        this.lastMsg = 7;
    }

    public final void setGetText(final String text)
    {
        this.getText = text;
    }

    public final String getText()
    {
        return this.getText;
    }

    /* 地圖相關 */

    public final void showEffect(final boolean broadcast, final String effect)
    {
        if (broadcast) {
            this.getMap().broadcastMessage(MaplePacketCreator.showEffect(effect));
        } else {
            this.writePacket(MaplePacketCreator.showEffect(effect));
        }
    }

    public final void playSound(final boolean broadcast, final String sound)
    {
        if (broadcast) {
            this.getMap().broadcastMessage(MaplePacketCreator.playSound(sound));
        } else {
            this.writePacket(MaplePacketCreator.playSound(sound));
        }
    }

    public final void environmentChange(final boolean broadcast, final String env)
    {
        if (broadcast) {
            this.getMap().broadcastMessage(MaplePacketCreator.environmentChange(env, 2));
        } else {
            this.writePacket(MaplePacketCreator.environmentChange(env, 2));
        }
    }

    /* 玩家相關 */

    public final int getMeso()
    {
        return this.getPlayer().getMeso();
    }

    public boolean hasSkill(final int skillId)
    {
        final ISkill skill = SkillFactory.getSkill(skillId);

        return skill != null && this.getPlayer().getSkillLevel(skill) > 0;
    }

    public final void gainAp(final int amount)
    {
        this.getPlayer().gainAp((short) amount);
    }

    public final void expandInventory(final byte type, final int amount)
    {
        this.getPlayer().expandInventory(type, amount);
    }

    public final void changeJob(final int job)
    {
        this.getPlayer().changeJob(job);
    }

    public final void updateBuddyCapacity(final int capacity)
    {
        this.getPlayer().setBuddyCapacity((byte) capacity);
    }

    public final int getBuddyCapacity() {
        return this.getPlayer().getBuddyCapacity();
    }

    public final void changeStat(final byte slot, final int type, final short amount)
    {
        final Equip sel = (Equip) this.getPlayer().getInventory(MapleInventoryType.EQUIPPED).getItem(slot);

        switch (type) {
            case 0:
                sel.setStr(amount);
                break;
            case 1:
                sel.setDex(amount);
                break;
            case 2:
                sel.setInt(amount);
                break;
            case 3:
                sel.setLuk(amount);
                break;
            case 4:
                sel.setHp(amount);
                break;
            case 5:
                sel.setMp(amount);
                break;
            case 6:
                sel.setWatk(amount);
                break;
            case 7:
                sel.setMatk(amount);
                break;
            case 8:
                sel.setWdef(amount);
                break;
            case 9:
                sel.setMdef(amount);
                break;
            case 10:
                sel.setAcc(amount);
                break;
            case 11:
                sel.setAvoid(amount);
                break;
            case 12:
                sel.setHands(amount);
                break;
            case 13:
                sel.setSpeed(amount);
                break;
            case 14:
                sel.setJump(amount);
                break;
            case 15:
                sel.setUpgradeSlots((byte) amount);
                break;
            case 16:
                sel.setViciousHammer((byte) amount);
                break;
            case 17:
                sel.setLevel((byte) amount);
                break;
            case 18:
                sel.setEnhance((byte) amount);
                break;
            case 19:
                sel.setPotential1(amount);
                break;
            case 20:
                sel.setPotential2(amount);
                break;
            case 21:
                sel.setPotential3(amount);
                break;
            case 22:
                sel.setOwner(getText());
                break;
            default:
                break;
        }

        this.getPlayer().equipChanged();
    }

    public final void maxStats()
    {
        if (!this.getPlayer().isAdmin()) {
            return;
        }

        final PlayerStats stats = this.getPlayer().getStat();
        final List<Pair<MapleStat, Integer>> statUp = new ArrayList<>(2);

        stats.setStr((short) 32767);
        stats.setDex((short) 32767);
        stats.setInt((short) 32767);
        stats.setLuk((short) 32767);
        stats.setMaxHp((short) 30000);
        stats.setMaxMp((short) 30000);
        stats.setHp((short) 30000);
        stats.setMp((short) 30000);

        statUp.add(new Pair<>(MapleStat.STR, 32767));
        statUp.add(new Pair<>(MapleStat.DEX, 32767));
        statUp.add(new Pair<>(MapleStat.LUK, 32767));
        statUp.add(new Pair<>(MapleStat.INT, 32767));
        statUp.add(new Pair<>(MapleStat.HP, 30000));
        statUp.add(new Pair<>(MapleStat.MAXHP, 30000));
        statUp.add(new Pair<>(MapleStat.MP, 30000));
        statUp.add(new Pair<>(MapleStat.MAXMP, 30000));

        this.writePacket(MaplePacketCreator.updatePlayerStats(statUp, this.getJob()));
    }

    public final void maxAllSkills()
    {
        for (final ISkill skill : SkillFactory.getAllSkills()) {
            if (GameConstants.isApplicableSkill(skill.getId())) {
                this.teachSkill(skill.getId(), skill.getMaxLevel(), skill.getMaxLevel());
            }
        }
    }

    public final void resetStats(final int str, final int dex, final int _int, final int luk)
    {
        this.getPlayer().resetStats(str, dex, _int, luk);
    }

    public final int setAvatar(final int ticket, final int args)
    {
        if (!this.haveItem(ticket)) {
            return -1;
        }

        this.gainItem(ticket, (short) -1);

        if (args < 100) {
            this.getPlayer().setSkinColor((byte) args);
            this.getPlayer().updateSingleStat(MapleStat.SKIN, args);
        } else if (args < 30000) {
            this.getPlayer().setFace(args);
            this.getPlayer().updateSingleStat(MapleStat.FACE, args);
        } else {
            this.getPlayer().setHair(args);
            this.getPlayer().updateSingleStat(MapleStat.HAIR, args);
        }

        this.getPlayer().equipChanged();

        return 1;
    }

    public final void setHair(final int hair)
    {
        this.getPlayer().setHair(hair);
        this.getPlayer().updateSingleStat(MapleStat.HAIR, hair);
        this.getPlayer().equipChanged();
    }

    public final void setFace(final int face)
    {
        this.getPlayer().setFace(face);
        this.getPlayer().updateSingleStat(MapleStat.FACE, face);
        this.getPlayer().equipChanged();
    }

    public void setSkin(final int color)
    {
        this.getPlayer().setSkinColor((byte) color);
        this.getPlayer().updateSingleStat(MapleStat.SKIN, color);
        this.getPlayer().equipChanged();
    }

    public final int setRandomAvatar(final int ticket, final int... args)
    {
        if (!this.haveItem(ticket)) {
            return -1;
        }

        this.gainItem(ticket, (short) -1);

        final int val = args[Randomizer.nextInt(args.length)];

        if (val < 100) {
            this.getPlayer().setSkinColor((byte) val);
            this.getPlayer().updateSingleStat(MapleStat.SKIN, val);
        } else if (val < 30000) {
            this.getPlayer().setFace(val);
            this.getPlayer().updateSingleStat(MapleStat.FACE, val);
        } else {
            this.getPlayer().setHair(val);
            this.getPlayer().updateSingleStat(MapleStat.HAIR, val);
        }

        this.getPlayer().equipChanged();

        return 1;
    }

    /* 物品相關 */

    public final MapleInventory getInventory(final int type)
    {
        return this.getPlayer().getInventory(MapleInventoryType.getByType((byte) type));
    }

    public final boolean isCash(final int itemId)
    {
        return MapleItemInformationProvider.getInstance().isCash(itemId);
    }

    public final boolean dropItem(final int slot, final int invType, final int quantity)
    {
        final MapleInventoryType inv = MapleInventoryType.getByType((byte) invType);

        return inv != null && MapleInventoryManipulator.drop(this.getClient(), inv, (short) slot, (short) quantity, true);
    }

    /* 轉蛋機 */

    public final int gainGachaponItem(final int itemId, final int quantity)
    {
        return gainGachaponItem(itemId, quantity, this.getMap().getStreetName() + " - " + this.getMap().getMapName() , false);
    }

    public final int gainGachaponItem(final int itemId, int quantity, final boolean broad)
    {
        return gainGachaponItem(itemId, quantity, this.getMap().getStreetName() + " - " + this.getMap().getMapName() , broad);
    }

    public final int gainGachaponItem(final int itemId, int quantity, final String msg , final boolean broad)
    {
        try {
            if (!MapleItemInformationProvider.getInstance().itemExists(itemId)) {
                return -1;
            }

            final IItem item = MapleInventoryManipulator.addbyId_Gachapon(this.getClient(), itemId, (short) quantity);

            if (item == null) {
                return -1;
            }

            final byte rareness = GameConstants.gachaponRareItem(item.getItemId());

            if (rareness > 0 || broad ) {
                World.Broadcast.broadcastMessage(MaplePacketCreator.getGachaponMega("[" + msg + "] " + this.getPlayer().getName(), " : 從轉蛋機轉到了!", item, rareness,this.getChannelNumber()-1).getBytes());
            }

            return item.getItemId();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
    }

    /* NPC 相關 */

    public final void sendStorage()
    {
        this.getPlayer().setConversation(4);
        this.getPlayer().getStorage().sendStorage(this.getClient(), this.getNpc());
    }

    public final void openShop(final int shopId)
    {
        MapleShopFactory.getInstance().getShop(shopId).sendShop(this.getClient());
    }

    public void sendRepairWindow()
    {
        this.writePacket(MaplePacketCreator.sendRepairWindow(this.getNpc()));
    }

    /* 任務相關 */

    public final void startQuest(final int questId)
    {
        final MapleQuest quest = this.getQuest(questId);

        if (quest == null) {
            return;
        }

        quest.start(this.getPlayer(), this.getNpc());
    }

    public final void completeQuest(final int questId)
    {
        final MapleQuest quest = this.getQuest(questId);

        if (quest == null) {
            return;
        }

        quest.complete(this.getPlayer(), this.getNpc());
    }

    public final void forfeitQuest(final int questId)
    {
        final MapleQuest quest = this.getQuest(questId);

        if (quest == null) {
            return;
        }

        quest.forfeit(this.getPlayer());
    }

    public final void forceStartQuest()
    {
        final MapleQuest quest = this.getQuest(this.getQuestId());

        if (quest == null) {
            return;
        }

        quest.forceStart(this.getPlayer(), this.getNpc(), null);
    }

    public final void forceStartQuest(final int questId)
    {
        final MapleQuest quest = this.getQuest(questId);

        if (quest == null) {
            return;
        }

        quest.forceStart(this.getPlayer(), this.getNpc(), null);
    }

    public final void forceStartQuest(final String customData)
    {
        final MapleQuest quest = this.getQuest(this.getQuestId());

        if (quest == null) {
            return;
        }

        quest.forceStart(this.getPlayer(), this.getNpc(), customData);
    }

    public final void forceCompleteQuest()
    {
        final MapleQuest quest = this.getQuest(this.getQuestId());

        if (quest == null) {
            return;
        }

        quest.forceComplete(this.getPlayer(), this.getNpc());
    }

    public final void forceCompleteQuest(final int questId)
    {
        final MapleQuest quest = this.getQuest(questId);

        if (quest == null) {
            return;
        }

        quest.forceComplete(this.getPlayer(), this.getNpc());
    }

    public final void setQuestRecord(final MapleCharacter ch, final int questId, final String data)
    {
        ch.getOrAddQuest(MapleQuest.getInstance(questId)).setData(data);
    }

    /* NPC 相關 */

    public void openDuey()
    {
        this.getPlayer().setConversation(2);
        this.writePacket(MaplePacketCreator.sendDuey((byte) 9, null));
    }

    public void openMerchantItemStore()
    {
        this.getPlayer().setConversation(3);
        this.writePacket(PlayerShopPacket.merchItemStore((byte) 0x22));
    }

    public void openFishingItemStore()
    {
        this.getPlayer().setConversation(6);
        HiredFishingHandler.OpenFishingItemStore(this.getClient());
    }

    /* 隊伍相關 */

    public final int getPartyMembersInMap()
    {
        int count = 0;

        for (final MapleCharacter chr : this.getMap().getCharactersThreadsafe()) {
            if (chr.getParty() == this.getPlayer().getParty()) {
                count++;
            }
        }

        return count;
    }

    public final List<MapleCharacter> getPartyMembers()
    {
        if (this.getPlayer().getParty() == null) {
            return null;
        }

        final List<MapleCharacter> chars = new LinkedList<>();

        for (final MaplePartyCharacter chr : this.getPlayer().getParty().getMembers()) {
            for (final ChannelServer channel : ChannelServer.getAllInstances()) {
                final MapleCharacter ch = channel.getPlayerStorage().getCharacterById(chr.getId());

                if (ch != null) {
                    chars.add(ch);
                }
            }
        }

        return chars;
    }

    /* 遠征相關 */

    public final MapleSquad getSquad(final String type)
    {
        return this.getChannelServer().getMapleSquad(type);
    }

    public final int getSquadAvailability(final String type)
    {
        final MapleSquad squad = this.getSquad(type);

        if (squad == null) {
            return -1;
        }

        return squad.getStatus();
    }

    public final boolean registerSquad(final String type, final int minutes, final String startText)
    {
        if (this.getSquad(type) == null) {
            final MapleSquad squad = new MapleSquad(this.getChannelNumber(), type, this.getPlayer(), minutes * 60 * 1000, startText);
            final boolean ret = this.getChannelServer().addMapleSquad(squad, type);

            if (!ret) {
                squad.clear();
            } else {
                final MapleMap map = this.getMap();

                map.broadcastMessage(MaplePacketCreator.getClock(minutes * 60));
                map.broadcastMessage(MaplePacketCreator.serverNotice(6, this.getName() + startText));
            }

            return ret;
        }

        return false;
    }

    public final boolean getSquadList(final String type, final byte _type)
    {
        final MapleSquad squad = this.getSquad(type);

        if (squad == null) {
            return false;
        }

        if (_type == 0 || _type == 3) { // Normal viewing
            this.sendNext(squad.getSquadMemberString(_type));
        } else if (_type == 1) { // Squad Leader banning, Check out banned participant
            this.sendSimple(squad.getSquadMemberString(_type));
        } else if (_type == 2) {
            if (squad.getBannedMemberSize() > 0) {
                this.sendSimple(squad.getSquadMemberString(_type));
            } else {
                this.sendNext(squad.getSquadMemberString(_type));
            }
        }

        return true;
    }

    public final byte isSquadLeader(final String type)
    {
        final MapleSquad squad = this.getSquad(type);

        if (squad == null) {
            return -1;
        } else if (squad.getLeader() != null && squad.getLeader().getId() == this.getPlayer().getId()) {
            return 1;
        }

        return 0;
    }

    public final byte isSquadMember(final String type)
    {
        final MapleSquad squad = this.getSquad(type);

        if (squad == null) {
            return -1;
        } else if (squad.getMembers().contains(this.getName())) {
            return 1;
        } else if (squad.isBanned(this.getPlayer())) {
            return 2;
        }

        return 0;
    }

    public final boolean reAdd(final String event, final String type)
    {
        final EventInstanceManager eim = this.getDisconnected(event);
        final MapleSquad squad = this.getSquad(type);

        if (eim == null || squad == null) {
            return false;
        }

        squad.reAddMember(this.getPlayer());
        eim.registerPlayer(this.getPlayer());

        return true;
    }

    public final int addMember(final String type, final boolean join)
    {
        final MapleSquad squad = this.getSquad(type);

        if (squad != null) {
            return squad.addMember(this.getPlayer(), join);
        }

        return -1;
    }

    public final void acceptMember(final String type, final int pos)
    {
        final MapleSquad squad = this.getSquad(type);

        if (squad != null) {
            squad.acceptMember(pos);
        }
    }

    public final void banMember(final String type, final int pos)
    {
        final MapleSquad squad = this.getSquad(type);

        if (squad != null) {
            squad.banMember(pos);
        }
    }

    /* 公會相關 */

    public final void genericGuildMessage(final int code)
    {
        this.writePacket(MaplePacketCreator.genericGuildMessage((byte) code));
    }

    public final void disbandGuild()
    {
        final int gid = this.getPlayer().getGuildId();

        if (gid <= 0 || this.getPlayer().getGuildRank() != 1) {
            return;
        }

        World.Guild.disbandGuild(gid);
    }

    public final void increaseGuildCapacity(final boolean useGP)
    {
        if (useGP) {
            if (this.getPlayer().getGuild().getGP() < 2500) {
                this.writePacket(MaplePacketCreator.serverNotice(1, "公會 GP 不足"));
                return;
            }

            final int gid = this.getPlayer().getGuildId();

            if (gid > 0) {
                World.Guild.increaseGuildCapacity(gid);
                this.getPlayer().getGuild().gainGP(-2500);
            }
        } else {
            if (this.getMeso() < 250000) {
                this.writePacket(MaplePacketCreator.serverNotice(1, "楓幣不足"));
                return;
            }

            if (this.getPlayer().getGuild().getCapacity() >= 100) {
                this.writePacket(MaplePacketCreator.serverNotice(1, "公會擴充人數已達上限"));
                return;
            }

            final int gid = this.getPlayer().getGuildId();

            if (gid > 0) {
                World.Guild.increaseGuildCapacity(gid);
                this.getPlayer().gainMeso(-250000, true, false, true);
            }
        }
    }

    public final void displayGuildRanks()
    {
        this.writePacket(MaplePacketCreator.showGuildRanks(this.getNpc(), MapleGuildRanking.getInstance().getRank()));
    }

    /* 公會聯盟 */

    public final boolean createAlliance(final String allianceName)
    {
        final MapleParty pt = this.getPlayer().getParty();
        final MapleCharacter otherChar = this.getChannelServer().getPlayerStorage().getCharacterById(pt.getMemberByIndex(1).getId());

        if (otherChar == null || otherChar.getId() == this.getPlayer().getId()) {
            return false;
        }

        try {
            return World.Alliance.createAlliance(allianceName, this.getPlayer().getId(), otherChar.getId(), this.getPlayer().getGuildId(), otherChar.getGuildId());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public final boolean addCapacityToAlliance()
    {
        try {
            final MapleGuild gs = World.Guild.getGuild(this.getPlayer().getGuildId());

            if (gs != null && this.getPlayer().getGuildRank() == 1 && this.getPlayer().getAllianceRank() == 1) {
                if (World.Alliance.getAllianceLeader(gs.getAllianceId()) == this.getPlayer().getId() && World.Alliance.changeAllianceCapacity(gs.getAllianceId())) {
                    this.gainMeso(-MapleGuildAlliance.CHANGE_CAPACITY_COST);
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public final boolean disbandAlliance()
    {
        try {
            final MapleGuild gs = World.Guild.getGuild(this.getPlayer().getGuildId());
            if (gs != null && this.getPlayer().getGuildRank() == 1 && this.getPlayer().getAllianceRank() == 1) {
                if (World.Alliance.getAllianceLeader(gs.getAllianceId()) == this.getPlayer().getId() && World.Alliance.disbandAlliance(gs.getAllianceId())) {
                    return true;
                }
            }
        } catch (Exception re) {
            re.printStackTrace();
        }

        return false;
    }

    /* 活動相關 */

    public final boolean removePlayerFromInstance()
    {
        if (this.getEventInstance() == null) {
            return false;
        }

        this.getEventInstance().removePlayer(this.getPlayer());

        return true;
    }

    public final boolean isPlayerInstance()
    {
        return this.getEventInstance() != null;
    }

    /* 武陵道場相關 */

    public final int getDojoPoints()
    {
        return this.getPlayer().getDojo();
    }

    public final int getDojoRecord()
    {
        return this.getPlayer().getDojoRecord();
    }

    public final void setDojoRecord(final boolean reset)
    {
        this.getPlayer().setDojoRecord(reset);
    }

    public final boolean startDojo(final boolean party)
    {
        return EventDojoAgent.start(this.getPlayer(), party);
    }

    /* 其他 */

    public final boolean startPyramidSubway(final int pyramid)
    {
        if (pyramid >= 0) {
            return Event_PyramidSubway.warpStartPyramid(this.getPlayer(), pyramid);
        }

        return Event_PyramidSubway.warpStartSubway(this.getPlayer());
    }

    public final boolean bonusPyramidSubway(final int pyramid)
    {
        if (pyramid >= 0) {
            return Event_PyramidSubway.warpBonusPyramid(this.getPlayer(), pyramid);
        }

        return Event_PyramidSubway.warpBonusSubway(this.getPlayer());
    }

    public final short getKegs()
    {
        return AramiaFireWorks.getInstance().getKegsPercentage();
    }

    public final void giveKegs(final int kegs)
    {
        AramiaFireWorks.getInstance().giveKegs(this.getPlayer(), kegs);
    }

    public final short getSunshines()
    {
        return AramiaFireWorks.getInstance().getSunsPercentage();
    }

    public final void addSunshines(final int kegs)
    {
        AramiaFireWorks.getInstance().giveSuns(this.getPlayer(), kegs);
    }

    public final short getDecorations()
    {
        return AramiaFireWorks.getInstance().getDecsPercentage();
    }

    public final void addDecorations(final int kegs)
    {
        try {
            AramiaFireWorks.getInstance().giveDecs(this.getPlayer(), kegs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public final MapleCarnivalParty getCarnivalParty()
    {
        return this.getPlayer().getCarnivalParty();
    }

    public final MapleCarnivalChallenge getNextCarnivalRequest()
    {
        return this.getPlayer().getNextCarnivalRequest();
    }

    public final MapleCarnivalChallenge getCarnivalChallenge(final MapleCharacter chr)
    {
        return new MapleCarnivalChallenge(chr);
    }

    public final Pair<String, Map<Integer, String>> getSpeedRun(final String typ)
    {
        final SpeedRunType type = SpeedRunType.valueOf(typ);

        if (SpeedRunner.getInstance().getSpeedRunData(type) != null) {
            return SpeedRunner.getInstance().getSpeedRunData(type);
        }

        return new Pair<>("", new HashMap<>());
    }

    public final boolean getSR(final Pair<String, Map<Integer, String>> ma, final int sel)
    {
        if (ma.getRight().get(sel) == null || ma.getRight().get(sel).length() <= 0) {
            this.safeDispose();
            return false;
        }

        this.sendOk(ma.getRight().get(sel));

        return true;
    }

    public final List<Integer> getAllPotentialInfo()
    {
        return new ArrayList<>(MapleItemInformationProvider.getInstance().getAllPotentialInfo().keySet());
    }

    public final String getPotentialInfo(final int id)
    {
        final List<StructPotentialItem> potInfo = MapleItemInformationProvider.getInstance().getPotentialInfo(id);
        final StringBuilder builder = new StringBuilder("#b#ePOTENTIAL INFO FOR ID: ");

        builder.append(id);
        builder.append("#n#k\r\n\r\n");

        int minLevel = 1, maxLevel = 10;

        for (final StructPotentialItem item : potInfo) {
            builder.append("#eLevels ");
            builder.append(minLevel);
            builder.append("~");
            builder.append(maxLevel);
            builder.append(": #n");
            builder.append(item.toString());
            builder.append("\r\n");

            minLevel += 10;
            maxLevel += 10;
        }

        return builder.toString();
    }

    public final void sendRPS()
    {
        this.writePacket(MaplePacketCreator.getRPSMode((byte) 8, -1, -1, -1));
    }

    public final void doWeddingEffect(final MapleCharacter chr)
    {
        this.getMap().broadcastMessage(MaplePacketCreator.yellowChat(getPlayer().getName() + "，你願意娶 " + chr.getName() + " 作為你的妻子嗎，與她在神聖的婚約中共同生活？無論是疾病或健康、貧窮或富裕、美貌或失色、順利或失意，你都願意愛她、安慰她、尊敬她、保護她？並願意在你們一生之中對她永遠忠心不變？"));

        Timer.CloneTimer.getInstance().schedule(() -> {
            if (this.getPlayer() == null) {
                this.warpMap(680000500, 0);
            } else {
                this.getMap().broadcastMessage(MaplePacketCreator.yellowChat(chr.getName() + "，你願意嫁 " + getPlayer().getName() + " 作為你的丈夫嗎，與他在神聖的婚約中共同生活？無論是疾病或健康、貧窮或富裕、美貌或失色、順利或失意，你都願意愛他、安慰他、尊敬他、保護他？並願意在你們一生之中對他永遠忠心不變？"));
            }
        }, 10000);

        Timer.CloneTimer.getInstance().schedule(() -> {
            if (this.getPlayer() == null) {
                if (this.getPlayer() != null) {
                    this.setQuestRecord(this.getPlayer(), 160001, "3");
                    this.setQuestRecord(this.getPlayer(), 160002, "0");
                } else {
                    this.setQuestRecord(chr, 160001, "3");
                    this.setQuestRecord(chr, 160002, "0");
                }

                this.warpMap(680000500, 0);
            } else {
                this.setQuestRecord(this.getPlayer(), 160001, "2");
                this.setQuestRecord(chr, 160001, "2");
                this.sendNPCText(this.getName() + " 和 " + chr.getName() + ", 祝福你們在未來的路上永恆不變、幸福美滿!", 9201002);
                this.getMap().startExtendedMapEffect("親吻你的新娘， " + this.getName() + " !", 5120006);

                if (chr.getGuildId() > 0) {
                    World.Guild.guildPacket(chr.getGuildId(), MaplePacketCreator.sendMarriage(false, chr.getName()));
                }

                if (chr.getFamilyId() > 0) {
                    World.Family.familyPacket(chr.getFamilyId(), MaplePacketCreator.sendMarriage(true, chr.getName()), chr.getId());
                }
                if (getPlayer().getGuildId() > 0) {
                    World.Guild.guildPacket(getPlayer().getGuildId(), MaplePacketCreator.sendMarriage(false, this.getName()));
                }

                if (getPlayer().getFamilyId() > 0) {
                    World.Family.familyPacket(getPlayer().getFamilyId(), MaplePacketCreator.sendMarriage(true, chr.getName()), this.getPlayer().getId());
                }
            }
        }, 20000); //10 sec 10 sec
    }

    public String getReadableMillis(final long startMillis, final long endMillis)
    {
        return StringUtil.getReadableMillis(startMillis, endMillis);
    }

    public void safeDispose()
    {
        this.pendingDisposal = true;
    }

    public void dispose()
    {
        NPCScriptManager.getInstance().dispose(this.getClient());
    }

    public Invocable getIv()
    {
        return this.iv;
    }

    public int getNpc()
    {
        return this.npcId;
    }

    public int getQuestId()
    {
        return this.questId;
    }

    public byte getType()
    {
        return this.type;
    }

    public final byte getLastMsg()
    {
        return this.lastMsg;
    }

    public final void setLastMsg(final byte last)
    {
        this.lastMsg = last;
    }
}
