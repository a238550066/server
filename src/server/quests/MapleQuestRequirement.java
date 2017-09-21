/*
 * TMS 113 server/quests/MapleQuestRequirement.java
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

import client.MapleCharacter;
import client.inventory.IItem;
import client.inventory.MaplePet;
import constants.GameConstants;
import provider.MapleData;
import provider.MapleDataTool;
import tools.DateTimeUtil;
import tools.Pair;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class MapleQuestRequirement
{
    private final MapleQuest quest;
    private final MapleQuestRequirementType type;

    private int intStore;
    private String stringStore;
    private final List<Integer> intList = new LinkedList<>();
    private final List<String> stringList = new LinkedList<>();
    private final List<Pair<Integer, Integer>> intDataStore = new LinkedList<>();

    public MapleQuestRequirement(final MapleQuest quest, final MapleQuestRequirementType type, final MapleData data)
    {
        this.type = type;
        this.quest = quest;

        switch (type) {
            case dayByDay:
            case endmeso:
            case infoNumber:
            case interval:
            case level:
            case lvmax:
            case lvmin:
            case mbmin: // 怪物卡數量
            case normalAutoStart:
            case npc:
            case partyQuest_S:
            case petAutoSpeakingLimit:
            case petRecallLimit:
            case pettamenessmax: // 寵物親密度
            case pettamenessmin: // 寵物親密度
            case pop: // 名聲
            case questComplete:
            case tamingmoblevelmin: // 馴服技能
            case worldmax:
            case worldmin:
                this.intStore = MapleDataTool.getInt(data);
                break;

            case end:
            case endscript:
            case start:
            case startscript:
                this.stringStore = MapleDataTool.getString(data);
                break;

            case equipAllNeed: // all
            case equipSelectNeed: // one of all
            case fieldEnter: // one of all
            case job: // one of all
                for (final MapleData child : data.getChildren()) {
                    this.intList.add(MapleDataTool.getInt(child));
                }
                break;

            case info: // all
                // 檢查 infoNumber 所指向的任務的 info 值
                for (final MapleData child : data.getChildren()) {
                    this.stringList.add(MapleDataTool.getString(child));
                }
                break;

            case infoex: // one of all
                // 檢查 infoNumber 所指向的任務的 info 值，或是自身的 info 值
                for (final MapleData child : data.getChildren()) {
                    this.stringList.add(MapleDataTool.getString(child.getChildByPath("value")));
                }
                break;

            case dayOfWeek: // one of all
                for (final MapleData child : data.getChildren()) {
                    this.stringList.add(child.getName());
                }
                break;

            case item: // all
                for (final MapleData child : data.getChildren()) {
                    this.intDataStore.add(new Pair<>(
                        MapleDataTool.getInt(child.getChildByPath("id")),
                        MapleDataTool.getInt(child.getChildByPath("count"), 0)
                    ));
                }
                break;

            case mbcard: // all
                for (final MapleData child : data.getChildren()) {
                    this.intDataStore.add(new Pair<>(
                        MapleDataTool.getInt(child.getChildByPath("id")),
                        MapleDataTool.getInt(child.getChildByPath("min"))
                    ));
                }
                break;

            case mob: // all
                for (final MapleData child : data.getChildren()) {
                    final int mobId = MapleDataTool.getInt(child.getChildByPath("id"));
                    final int count = MapleDataTool.getInt(child.getChildByPath("count"));

                    this.quest.getRelevantMobs().put(mobId, count);
                    this.intDataStore.add(new Pair<>(mobId, count));
                }
                break;

            case pet: // one of all
                for (final MapleData child : data.getChildren()) {
                    this.intList.add(MapleDataTool.getInt("id", child));
                }
                break;


            case quest: // all
                for (final MapleData child : data.getChildren()) {
                    this.intDataStore.add(new Pair<>(
                        MapleDataTool.getInt(child.getChildByPath("id")),
                        MapleDataTool.getInt(child.getChildByPath("state"), 0)
                    ));
                }
                break;

            case skill: // all
                for (final MapleData child : data.getChildren()) {
                    this.intDataStore.add(new Pair<>(
                        MapleDataTool.getInt(child.getChildByPath("id")),
                        MapleDataTool.getInt(child.getChildByPath("acquire"), 0)
                    ));
                }
                break;
        }
    }

    public final boolean check(final MapleCharacter c, final Integer npcId, final boolean start)
    {
        switch (this.type) {
            case dayByDay:
                return true;

            case dayOfWeek: {
                final String name = Calendar.getInstance().getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.TAIWAN).toLowerCase();

                for (final String day : this.stringList) {
                    if (name.equals(day)) {
                        return true;
                    }
                }

                return false;
            }

            case endmeso:
                return c.getMeso() > this.intStore;

            case endscript:
                // @todo
                break;

            case equipAllNeed:
                for (final Integer itemId : this.intList) {
                    if (!c.haveItem(itemId)) {
                        return false;
                    }
                }
                return true;

            case equipSelectNeed:
                for (final Integer itemId : this.intList) {
                    if (c.haveItem(itemId)) {
                        return true;
                    }
                }
                return false;

            case fieldEnter:
                return this.intList.contains(c.getMapId());

            case infoNumber:
                return true;

            case info: { // 判斷 infoNumber 所指向的任務的 info 值
                String info = null;

                for (final MapleQuestRequirement req : (start ? this.quest.startRequirements : this.quest.completeRequirements)) {
                    if (req.type == MapleQuestRequirementType.infoNumber) {
                        info = c.getQuest(MapleQuest.getInstance(req.intStore)).getInfo();
                        break;
                    }
                }

                if (info == null) {
                    return false;
                }

                for (final String val : this.stringList) {
                    if (!info.equals(val)) {
                        return false;
                    }
                }

                return true;
            }

            case infoex: { // 判斷 infoNumber 所指向的任務的 info 值，或是自身的 info 值
                String info = null;

                for (final MapleQuestRequirement req : (start ? this.quest.startRequirements : this.quest.completeRequirements)) {
                    if (req.type == MapleQuestRequirementType.infoNumber) {
                        info = c.getQuest(MapleQuest.getInstance(req.intStore)).getInfo();
                        break;
                    }
                }

                if (info == null) {
                    info = c.getQuest(this.quest).getInfo();

                    if (info == null) {
                        return false;
                    }
                }

                for (final String val : this.stringList) {
                    if (info.equals(val)) {
                        return true;
                    }
                }

                return false;
            }

            case interval:
                return c.getQuest(this.quest).getStatus() != 2 || c.getQuest(this.quest).getCompletionTime() <= (System.currentTimeMillis() - this.intStore * 60 * 1000L);

            case item:
                for (final Pair<Integer, Integer> item : this.intDataStore) {
                    int quantity = 0;

                    for (final IItem iitem : c.getInventory(GameConstants.getInventoryType(item.getLeft())).listById(item.getLeft())) {
                        quantity += iitem.getQuantity();
                    }

                    if (item.getRight() > quantity) {
                        return false;
                    }
                }
                return true;

            case job:
                for (final Integer jobId : this.intList) {
                    if (c.getJob() == jobId) {
                        return true;
                    }
                }
                return true;

            case level:
                return c.getLevel() == this.intStore;

            case lvmax:
                return c.getLevel() <= this.intStore;

            case lvmin:
                return c.getLevel() >= this.intStore;

            case mbcard:
                for (final Pair<Integer, Integer> card : this.intDataStore) {
                    if (c.getMonsterBook().getLevelByCard(card.getLeft()) < card.getRight()) {
                        return false;
                    }
                }
                return true;

            case mbmin:
                return c.getMonsterBook().getTotalCards() >= this.intStore;

            case mob:
                for (final Pair<Integer, Integer> mob : this.intDataStore) {
                    if (c.getQuest(this.quest).getMobKills(mob.getLeft()) < mob.getRight()) {
                        return false;
                    }
                }
                return true;

            case normalAutoStart:
                return true;

            case npc:
                // js script 的 npcId 會是 null
                return npcId == null || npcId == this.intStore;

            case partyQuest_S:
                // @todo
                return false;

            case pet:
                for (final MaplePet pet : c.getPets()) {
                    if (this.intList.contains(pet.getPetItemId())) {
                        return true;
                    }
                }
                return false;

            case petAutoSpeakingLimit:
                // @todo
                break;

            case petRecallLimit:
                // @todo
                break;

            case pettamenessmax:
                for (final MaplePet pet : c.getPets()) {
                    if (pet.getSummoned() && pet.getCloseness() > this.intStore) {
                        return false;
                    }
                }
                return true;

            case pettamenessmin:
                for (final MaplePet pet : c.getPets()) {
                    if (pet.getSummoned() && pet.getCloseness() >= this.intStore) {
                        return true;
                    }
                }
                return false;

            case pop:
                return c.getFame() >= this.intStore;

            case quest:
                for (final Pair<Integer, Integer> quest : this.intDataStore) {
                    if (c.getQuest(MapleQuest.getInstance(quest.getLeft())).getStatus() != quest.getRight()) {
                        return false;
                    }
                }
                return true;

            case questComplete:
                return c.getCompletedQuestNumber() >= this.intStore;

            case skill:
                for (final Pair<Integer, Integer> skill : this.intDataStore) {
                    final int skillId = skill.getLeft();

                    if (skill.getRight() == 0) {
                        if (c.getSkillLevel(skillId) > 0 || c.getMasterLevel(skillId) > 0) {
                            return false;
                        }
                    } else {
                        if (c.getSkillLevel(skillId) == 0 && c.getMasterLevel(skillId) == 0) {
                            return false;
                        }
                    }
                }
                return true;

            case start:
            case end: {
                final long time = DateTimeUtil.toTimestamp(
                    LocalDateTime.parse(this.stringStore, DateTimeFormatter.ofPattern("yyyyMMddHH")).toString()
                );

                if (this.type == MapleQuestRequirementType.start) {
                    return time < System.currentTimeMillis();
                }

                return time >= System.currentTimeMillis();
            }

            case startscript:
                // @todo
                break;

            case tamingmoblevelmin:
                // @todo
                break;

            case worldmax:
                return c.getWorld() <= this.intStore;

            case worldmin:
                return c.getWorld() >= this.intStore;

            default:
                return true;
        }

        return true;
    }

    public final MapleQuestRequirementType getType()
    {
        return this.type;
    }
}
