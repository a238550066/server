/*
 * TMS 113 server/quests/MapleQuestAction.java
 *
 * Copyright (C) 2017 ~ Present
 *
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
package server.quests;

import client.*;
import client.inventory.MapleInventoryType;
import client.inventory.MaplePet;
import constants.GameConstants;
import provider.MapleData;
import provider.MapleDataTool;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import server.MapleStatEffect;
import server.Randomizer;
import tools.MaplePacketCreator;

import java.util.*;

class MapleQuestAction
{
    public final MapleQuestActionType type;
    public final MapleData data;
    private final MapleQuest quest;

    MapleQuestAction(final MapleQuestActionType type, final MapleData data, final MapleQuest quest) {
        this.type = type;
        this.data = data;
        this.quest = quest;
    }

    final boolean restoreLostItem(final MapleCharacter c, final int itemId)
    {
        if (this.type == MapleQuestActionType.item) {
            for (final MapleData item : this.data.getChildren()) {
                final int restoreItem = MapleDataTool.getInt(item.getChildByPath("id"));

                if (restoreItem == itemId) {
                    if (!c.haveItem(restoreItem, 1, true, false)) {
                        MapleInventoryManipulator.addById(c.getClient(), restoreItem, (short) 1);
                    }

                    return true;
                }
            }
        }

        return false;
    }

    void runStart(final MapleCharacter c)
    {
        this.run(c, null, true);
    }

    void runComplete(final MapleCharacter c, final Integer extSelection)
    {
        this.run(c, extSelection, false);
    }

    private void run(final MapleCharacter c, final Integer extSelection, final boolean start)
    {
        switch (this.type) {
            case buffItemID: // 物品 Buff
                final MapleStatEffect itemEffect = MapleItemInformationProvider.getInstance().getItemEffect(MapleDataTool.getInt(this.data));

                if (itemEffect == null) {
                    System.err.println("Item does not exists: " + MapleDataTool.getInt(this.data) + ", quest id: " + this.quest.getId());
                } else {
                    itemEffect.applyTo(c);
                }
                break;

            case exp: // 經驗值
                if (start && c.getQuest(this.quest).getForfeited() > 0) {
                    break;
                }
                c.gainExp(MapleDataTool.getInt(this.data), true, true, true);
                break;

            case info:
                if (start && c.getQuest(this.quest).getForfeited() > 0) {
                    break;
                }
                c.getOrAddQuest(this.quest).setInfo(MapleDataTool.getString(this.data));
                break;

            case item: // 物品
                // 如有機率性物品，則加進列表中
                final Map<Integer, Integer> props = new HashMap<>();

                for (final MapleData item : this.data.getChildren()) {
                    if (item.getChildByPath("prop") == null) {
                        continue;
                    }

                    if (MapleDataTool.getInt(item.getChildByPath("prop")) != -1 && canGetItem(item, c)) {
                        for (int i = 0; i < MapleDataTool.getInt(item.getChildByPath("prop")); i++) {
                            props.put(props.size(), MapleDataTool.getInt(item.getChildByPath("id")));
                        }
                    }
                }

                int selection = 0;
                int extNum = 0;

                if (props.size() > 0) {
                    selection = props.get(Randomizer.nextInt(props.size()));
                }

                for (final MapleData item : this.data.getChildren()) {
                    if (!canGetItem(item, c)) {
                        continue;
                    }

                    final int itemId = MapleDataTool.getInt(item.getChildByPath("id"));

                    if (item.getChildByPath("prop") != null) {
                        if (MapleDataTool.getInt(item.getChildByPath("prop")) == -1) {
                            if (extSelection != extNum++) {
                                continue;
                            }
                        } else if (itemId != selection) {
                            continue;
                        }
                    }

                    final short count = (short) MapleDataTool.getInt(item.getChildByPath("count"));

                    if (count < 0) {
                        MapleInventoryManipulator.removeById(c.getClient(), GameConstants.getInventoryType(itemId), itemId, (count * -1), true, false);
                        c.getClient().getSession().write(MaplePacketCreator.getShowItemGain(itemId, count, true));
                    } else {
                        final int period = MapleDataTool.getInt(item.getChildByPath("period"), 0) / 1440;
                        final String name = MapleItemInformationProvider.getInstance().getName(itemId);

                        if (itemId / 10000 == 114 && name != null && name.length() > 0) { // 勳章
                            final String msg = "你已獲得稱號 <" + name + ">";
                            c.dropMessage(-1, msg);
                            c.dropMessage(5, msg);
                        }

                        MapleInventoryManipulator.addById(c.getClient(), itemId, count, "", null, period);
                        c.getClient().getSession().write(MaplePacketCreator.getShowItemGain(itemId, count, true));
                    }
                }
                break;

            case message: // 訊息
                c.dropMessage(5, MapleDataTool.getString(this.data));
                break;

            case money: // 楓幣
                if (start && c.getQuest(this.quest).getForfeited() > 0) {
                    break;
                }
                c.gainMeso(MapleDataTool.getInt(this.data), true, false, true);
                break;

            case nextQuest: { // 觸發任務
                // 如果不略過，會拿不到任務起始物品
                //c.updateQuest(new MapleQuestStatus(quest, (byte) 1), true);
                //c.getClient().getSession().write(MaplePacketCreator.updateQuestFinish(this.quest.getId(), c.getQuest(this.quest).getNpc(), MapleDataTool.getInt(this.data)));
                break;
            }

            case npcAct:
                // @todo 應該是要顯示動畫
                break;

            case petskill:
                // @todo 寵物獲得技能
                break;

            case petspeed:
                // @todo 不知道要做什麼
                break;

            case pettameness: // 寵物親密度
                for (final MaplePet pet : c.getPets()) {
                    if (pet.getSummoned()) {
                        pet.setCloseness(pet.getCloseness() + MapleDataTool.getInt(this.data));
                    }
                }
                break;

            case pop: // 名聲
                if (start && c.getQuest(this.quest).getForfeited() > 0) {
                    break;
                }
                final int fameGain = MapleDataTool.getInt(this.data);
                c.addFame(fameGain);
                c.updateSingleStat(MapleStat.FAME, c.getFame());
                c.getClient().getSession().write(MaplePacketCreator.getShowFameGain(fameGain));
                break;

            case quest: // 任務
                for (final MapleData quest : this.data.getChildren()) {
                    c.updateQuest(new MapleQuestStatus(
                        MapleQuest.getInstance(MapleDataTool.getInt(quest.getChildByPath("id"))),
                        (byte) MapleDataTool.getInt(quest.getChildByPath("state"))
                    ));
                }
                break;

            case skill: // 技能
                if (start) {
                    break;
                }

                for (final MapleData skill : this.data.getChildren()) {
                    final int skillId = MapleDataTool.getInt(skill.getChildByPath("id"));
                    final ISkill skillObject = SkillFactory.getSkill(skillId);

                    if (skillObject == null) {
                        System.err.println("Null skill: " + skillId);
                        continue;
                    }

                    for (final MapleData job : skill.getChildByPath("job")) {
                        if (c.getJob() == MapleDataTool.getInt(job)) {
                            c.changeSkillLevel(
                                skillObject,
                                (byte) Math.max(MapleDataTool.getInt(skill.getChildByPath("skillLevel")), c.getSkillLevel(skillObject)),
                                (byte) Math.max(MapleDataTool.getInt(skill.getChildByPath("masterLevel")), c.getMasterLevel(skillObject))
                            );
                            break;
                        }
                    }
                }
                break;

            case ask:
            case infoNumber:
            case interval:
            case job:
            case lvmax:
            case lvmin:
            case no:
            case npc:
            case start:
            case stop:
            case yes:
                // 已在其他地方處理過，可略過
                break;

            case dayByDay:
            case end:
            case map:
                // 意義不明，略過
                break;

            default:
                break;
        }
    }

    boolean checkRequirement(final MapleCharacter c)
    {
        switch (this.type) {
            case item: {
                // first check for randomness in item selection
                byte eq = 0, use = 0, setup = 0, etc = 0, cash = 0;

                for (final MapleData item : this.data.getChildren()) {
                    if (!canGetItem(item, c)) {
                        continue;
                    }

                    final int itemId = MapleDataTool.getInt(item.getChildByPath("id"));
                    final short count = (short) MapleDataTool.getInt(item.getChildByPath("count"));

                    if (count < 0) { // remove items
                        if (!c.haveItem(itemId, count, false, true)) {
                            c.dropMessage(1, "您尚未收集到所有任務所需的物品。");
                            return false;
                        }
                    } else { // add items
                        if (MapleItemInformationProvider.getInstance().isPickupRestricted(itemId) && c.haveItem(itemId, 1, true, false)) {
                            c.dropMessage(1, "您已擁有「" + MapleItemInformationProvider.getInstance().getName(itemId) + "」");
                            return false;
                        }

                        switch (GameConstants.getInventoryType(itemId)) {
                            case EQUIP:
                                eq++;
                                break;
                            case USE:
                                use++;
                                break;
                            case SETUP:
                                setup++;
                                break;
                            case ETC:
                                etc++;
                                break;
                            case CASH:
                                cash++;
                                break;
                        }
                    }
                }

                if (c.getInventory(MapleInventoryType.EQUIP).getNumFreeSlot() < eq) {
                    c.dropMessage(1, "請確認裝備欄是否有足夠的空間。");
                    return false;
                } else if (c.getInventory(MapleInventoryType.USE).getNumFreeSlot() < use) {
                    c.dropMessage(1, "請確認消耗欄是否有足夠的空間。");
                    return false;
                } else if (c.getInventory(MapleInventoryType.SETUP).getNumFreeSlot() < setup) {
                    c.dropMessage(1, "Please make space for your Setup inventory.");
                    return false;
                } else if (c.getInventory(MapleInventoryType.ETC).getNumFreeSlot() < etc) {
                    c.dropMessage(1, "Please make space for your Etc inventory.");
                    return false;
                } else if (c.getInventory(MapleInventoryType.CASH).getNumFreeSlot() < cash) {
                    c.dropMessage(1, "Please make space for your Cash inventory.");
                    return false;
                }

                return true;
            }

            case money: {
                final int meso = MapleDataTool.getInt(this.data);

                if (c.getMeso() + meso < 0) { // 溢位啦
                    c.dropMessage(1, "楓幣超出最大值，2,147,483,647。");
                    return false;
                }

                if (meso < 0 && c.getMeso() < Math.abs(meso)) { // 楓幣不足
                    c.dropMessage(1, "楓幣不足。");
                    return false;
                }

                return true;
            }

            default:
                return true;
        }
    }

    private static boolean canGetItem(final MapleData item, final MapleCharacter c)
    {
        if (item.getChildByPath("gender") != null) {
            final int gender = MapleDataTool.getInt(item.getChildByPath("gender"));

            if (gender != 2 && gender != c.getGender()) {
                return false;
            }
        }

        if (item.getChildByPath("job") != null) {
            for (final int code : getJob(MapleDataTool.getInt(item.getChildByPath("job")))) {
                if (c.getJob() / 100 == code / 100) {
                    return true;
                }
            }

            return false;
        }

        return true;
    }

    private static List<Integer> getJob(final int encoded)
    {
        List<Integer> ret = new ArrayList<>();

        if ((encoded & 0x1) != 0) {
            ret.add(0);
        }

        if ((encoded & 0x2) != 0) {
            ret.add(100);
        }

        if ((encoded & 0x4) != 0) {
            ret.add(200);
        }

        if ((encoded & 0x8) != 0) {
            ret.add(300);
        }

        if ((encoded & 0x10) != 0) {
            ret.add(400);
        }

        if ((encoded & 0x20) != 0) {
            ret.add(500);
        }

        if ((encoded & 0x400) != 0) {
            ret.add(1000);
        }

        if ((encoded & 0x800) != 0) {
            ret.add(1100);
        }

        if ((encoded & 0x1000) != 0) {
            ret.add(1200);
        }

        if ((encoded & 0x2000) != 0) {
            ret.add(1300);
        }

        if ((encoded & 0x4000) != 0) {
            ret.add(1400);
        }

        if ((encoded & 0x8000) != 0) {
            ret.add(1500);
        }

        if ((encoded & 0x20000) != 0) {
            ret.add(2001); //im not sure of this one
            ret.add(2200);
        }

        if ((encoded & 0x100000) != 0) {
            ret.add(2000);
            ret.add(2001); //?
        }

        if ((encoded & 0x200000) != 0) {
            ret.add(2100);
        }

        if ((encoded & 0x400000) != 0) {
            ret.add(2001); //?
            ret.add(2200);
        }

        if ((encoded & 0x40000000) != 0) { //i haven't seen any higher than this o.o
            ret.add(3000);
            ret.add(3200);
            ret.add(3300);
            ret.add(3500);
        }

        return ret;
    }
}
