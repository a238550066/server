/*
 * TMS 113 server/quests/MapleQuest.java
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
import client.MapleQuestStatus;
import constants.GameConstants;
import provider.MapleData;
import provider.MapleDataProvider;
import provider.MapleDataProviderFactory;
import provider.MapleDataTool;
import tools.MaplePacketCreator;
import tools.Pair;

import java.io.File;
import java.util.*;

public class MapleQuest
{
    private static Map<Integer, MapleQuest> quests = new LinkedHashMap<>();

    private final int id;
    List<MapleQuestRequirement> startRequirements = new LinkedList<>();
    List<MapleQuestRequirement> completeRequirements = new LinkedList<>();
    private List<MapleQuestAction> startActions = new LinkedList<>();
    private List<MapleQuestAction> completeActions = new LinkedList<>();
    private Map<String, List<Pair<String, Pair<String, Integer>>>> partyQuestInfo = new LinkedHashMap<>();
    private Map<Integer, Integer> relevantMobs = new LinkedHashMap<>();

    protected String name = "";
    private boolean autoStart = false;
    private boolean autoPreComplete = false;
    private boolean repeatable = false;
    private int viewMedalItem = 0;
    private int selectedSkillID = 0;

    private static MapleData actions;
    private static MapleData requirements;
    private static MapleData info;
    private static MapleData pinfo;

    private MapleQuest(final int id)
    {
        this.id = id;
    }

    public static void initQuests()
    {
        final MapleDataProvider questData = MapleDataProviderFactory.getDataProvider(new File("wz/Quest.wz"));

        actions = questData.getData("Act.img");
        requirements = questData.getData("Check.img");
        info = questData.getData("QuestInfo.img");
        pinfo = questData.getData("PQuest.img");
    }

    public static void clearQuests()
    {
        quests.clear();
        initQuests();
    }

    /**
     * 載入任務資訊
     */
    private static boolean loadQuest(MapleQuest ret, int id)
    {
        final MapleData requirement = requirements.getChildByPath(String.valueOf(id));
        final MapleData action = actions.getChildByPath(String.valueOf(id));

        if (requirement == null || action == null) {
            return false;
        }

        // 任務起始條件
        if (requirement.getChildByPath("0") != null) {
            loadStartRequirements(ret, requirement.getChildByPath("0"));
        }

        // 任務完成條件
        if (requirement.getChildByPath("1") != null) {
            loadCompleteRequirements(ret, requirement.getChildByPath("1"));
        }

        // 任務起始事件
        if (action.getChildByPath("0") != null) {
            loadStartActions(ret, action.getChildByPath("0"));
        }

        // 任務完成事件
        if (action.getChildByPath("1") != null) {
            loadCompleteActions(ret, action.getChildByPath("1"));
        }

        final MapleData questInfo = info.getChildByPath(String.valueOf(id));

        if (questInfo != null) {
            // 任務名稱
            ret.name = MapleDataTool.getString("name", questInfo, "");
            // 自動開始
            ret.autoStart = MapleDataTool.getInt("autoStart", questInfo, 0) == 1;
            // 自動完成任務所需條件
            ret.autoPreComplete = MapleDataTool.getInt("autoPreComplete", questInfo, 0) == 1;
            // 勳章
            ret.viewMedalItem = MapleDataTool.getInt("viewMedalItem", questInfo, 0);
            // 指定技能
            ret.selectedSkillID = MapleDataTool.getInt("selectedSkillID", questInfo, 0);
        }

        // 組隊任務排名：S，A，B，C，D，F
        final MapleData PQuestInfo = pinfo.getChildByPath(String.valueOf(id));

        if (PQuestInfo != null) {
            for (final MapleData rank : PQuestInfo.getChildByPath("rank")) {
                final List<Pair<String, Pair<String, Integer>>> pInfo = new ArrayList<>();

                for (final MapleData type : rank.getChildren()) {
                    for (final MapleData req : type.getChildren()) {
                        pInfo.add(new Pair<>(type.getName(), new Pair<>(req.getName(), MapleDataTool.getInt(req, 0))));
                    }
                }

                ret.partyQuestInfo.put(rank.getName(), pInfo);
            }
        }

        return true;
    }

    /**
     * 載入任務起始條件
     */
    private static void loadStartRequirements(final MapleQuest quest, final MapleData data)
    {
        for (final MapleData requirement : data.getChildren()) {
            final MapleQuestRequirementType type = MapleQuestRequirementType.getByWZName(requirement.getName());

            if (type.equals(MapleQuestRequirementType.interval)) {
                quest.repeatable = true;
            }

            quest.startRequirements.add(new MapleQuestRequirement(quest, type, requirement));
        }
    }

    /**
     * 載入任務完成條件
     */
    private static void loadCompleteRequirements(final MapleQuest quest, final MapleData data)
    {
        for (final MapleData requirement : data.getChildren()) {
            quest.completeRequirements.add(
                new MapleQuestRequirement(
                    quest,
                    MapleQuestRequirementType.getByWZName(requirement.getName()),
                    requirement
                ))
            ;
        }
    }

    /**
     * 載入任務起始事件
     */
    private static void loadStartActions(final MapleQuest quest, final MapleData data)
    {
        for (final MapleData action : data.getChildren()) {
            quest.startActions.add(new MapleQuestAction(MapleQuestActionType.getByWZName(action.getName()), action, quest));
        }
    }

    /**
     * 載入任務完成事件
     */
    private static void loadCompleteActions(final MapleQuest quest, final MapleData data)
    {
        for (final MapleData action : data.getChildren()) {
            quest.completeActions.add(new MapleQuestAction(MapleQuestActionType.getByWZName(action.getName()), action, quest));
        }
    }

    /**
     * 開始任務
     */
    public void start(final MapleCharacter c, final int npc)
    {
        if ((this.autoStart || this.checkNPCOnMap(c, npc)) && this.canStart(c, npc)) {
            for (final MapleQuestAction action : this.startActions) {
                if (!action.checkRequirement(c)) {
                    return;
                }
            }

            for (final MapleQuestAction action : this.startActions) {
                action.runStart(c);
            }

            this.forceStart(c, npc, null);
        }
    }

    /**
     * 放棄任務
     *
     * @todo 相依任務會出問題
     */
    public void forfeit(MapleCharacter c)
    {
        MapleQuestStatus oldStatus = c.getQuest(this);

        if (oldStatus.getStatus() != (byte) 1) {
            return;
        }

        final MapleQuestStatus status = new MapleQuestStatus(this, (byte) 0);

        status.setInfo(oldStatus.getInfo());
        status.setForfeited(oldStatus.getForfeited() + 1);
        status.setCompletionTime(oldStatus.getCompletionTime());

        c.updateQuest(status);
    }

    /**
     * 完成任務
     */
    public void complete(final MapleCharacter c, final int npc)
    {
        this.complete(c, npc, null);
    }

    /**
     * 完成任務
     */
    public void complete(final MapleCharacter c, final int npc, final Integer selection)
    {
        if ((this.autoPreComplete || this.checkNPCOnMap(c, npc)) && this.canComplete(c, npc)) {
            for (final MapleQuestAction action : this.completeActions) {
                if (!action.checkRequirement(c)) {
                    return;
                }
            }

            this.forceComplete(c, npc);

            for (final MapleQuestAction action : completeActions) {
                action.runComplete(c, selection);
            }

            c.getClient().getSession().write(MaplePacketCreator.showSpecialEffect(9)); // Quest completion
            c.getMap().broadcastMessage(c, MaplePacketCreator.showSpecialEffect(c.getId(), 9), false);
        }
    }

    public void forceStart(final MapleCharacter c, final int npc, final String customData)
    {
        final MapleQuestStatus oldStatus = c.getQuest(this);
        final MapleQuestStatus newStatus = new MapleQuestStatus(this, (byte) 1, npc);

        newStatus.setInfo(oldStatus.getInfo());
        newStatus.setCustomData(customData);
        newStatus.setCompletionTime(oldStatus.getCompletionTime());

        if (oldStatus.getMobKills() != null) {
            for (final Map.Entry<Integer, Integer> mob : oldStatus.getMobKills().entrySet()) {
                newStatus.setMobKills(mob.getKey(), mob.getValue());
            }
        }

        c.updateQuest(newStatus);
    }

    public void forceComplete(final MapleCharacter c, final int npc)
    {
        final MapleQuestStatus oldStatus = c.getQuest(this);
        final MapleQuestStatus newStatus = new MapleQuestStatus(this, (byte) 2, npc);

        newStatus.setInfo(oldStatus.getInfo());
        newStatus.setCustomData(oldStatus.getCustomData());
        newStatus.setCompletionTime(System.currentTimeMillis());

        if (oldStatus.getMobKills() != null) {
            for (final Map.Entry<Integer, Integer> mob : oldStatus.getMobKills().entrySet()) {
                newStatus.setMobKills(mob.getKey(), mob.getValue());
            }
        }

        c.updateQuest(newStatus);
    }

    /**
     * 檢查任務起始條件是否符合
     */
    public boolean canStart(MapleCharacter c, Integer npcId)
    {
        if (c.getQuest(this).getStatus() != 0) { // 任務進行中或已完成
            if (! (c.getQuest(this).getStatus() == 2 && this.repeatable)) { // 任務未完成或是不可重複執行之任務
                return false;
            }
        }

        // 確認所有任務先決條件皆符合
        for (final MapleQuestRequirement r : this.startRequirements) {
            if (! r.check(c, npcId, true)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 檢查任務完成條件是否符合
     */
    public boolean canComplete(MapleCharacter c, Integer npcId)
    {
        if (c.getQuest(this).getStatus() != 1) {
            return false;
        }

        for (final MapleQuestRequirement r : this.completeRequirements) {
            if (! r.check(c, npcId, false)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 確認任務對象 NPC 是否在地圖上
     */
    private boolean checkNPCOnMap(MapleCharacter c, int npcId)
    {
        return (GameConstants.isEvan(c.getJob()) && npcId == 1013000) || (c.getMap() != null && c.getMap().containsNPC(npcId));
    }

    public final void restoreLostItem(final MapleCharacter c, final int itemId)
    {
        for (final MapleQuestAction action : this.startActions) {
            if (action.restoreLostItem(c, itemId)) {
                break;
            }
        }
    }

    public static MapleQuest getInstance(int id)
    {
        MapleQuest ret = quests.get(id);

        if (ret == null) {
            ret = new MapleQuest(id);

            if (!loadQuest(ret, id)) {
                System.err.println("Invalid quest id: " + id);
                return null;
            }

            quests.put(id, ret);
        }

        return ret;
    }

    public final int getId()
    {
        return this.id;
    }

    public final int getMedalItem()
    {
        return this.viewMedalItem;
    }

    public final int getSkillID()
    {
        return this.selectedSkillID;
    }

    public final Map<Integer, Integer> getRelevantMobs()
    {
        return this.relevantMobs;
    }

    public final List<Pair<String, Pair<String, Integer>>> getInfoByRank(final String rank)
    {
        return this.partyQuestInfo.get(rank);
    }

    public enum MedalQuest
    {
        新手冒險家(29005, 29015, 15, new int[]{104000000, 104010001, 100000006, 104020000, 100000000, 100010000, 100040000, 100040100, 101010103, 101020000, 101000000, 102000000, 101030104, 101030406, 102020300, 103000000, 102050000, 103010001, 103030200, 110000000}),
        ElNath(29006, 29012, 50, new int[]{200000000, 200010100, 200010300, 200080000, 200080100, 211000000, 211030000, 211040300, 211041200, 211041800}),
        LudusLake(29007, 29012, 40, new int[]{222000000, 222010400, 222020000, 220000000, 220020300, 220040200, 221020701, 221000000, 221030600, 221040400}),
        Underwater(29008, 29012, 40, new int[]{230000000, 230010400, 230010200, 230010201, 230020000, 230020201, 230030100, 230040000, 230040200, 230040400}),
        MuLung(29009, 29012, 50, new int[]{251000000, 251010200, 251010402, 251010500, 250010500, 250010504, 250000000, 250010300, 250010304, 250020300}),
        NihalDesert(29010, 29012, 70, new int[]{261030000, 261020401, 261020000, 261010100, 261000000, 260020700, 260020300, 260000000, 260010600, 260010300}),
        MinarForest(29011, 29012, 70, new int[]{240000000, 240010200, 240010800, 240020401, 240020101, 240030000, 240040400, 240040511, 240040521, 240050000}),
        Sleepywood(29014, 29015, 50, new int[]{105040300, 105070001, 105040305, 105090200, 105090300, 105090301, 105090312, 105090500, 105090900, 105080000});

        public final int questId, lastQuestId, level;
        public final int[] maps;

        MedalQuest(int questId, int lastQuestId, int level, int[] maps)
        {
            this.questId = questId; //infoquest = questid -2005, customdata = questid -1995
            this.lastQuestId = lastQuestId;
            this.level = level;
            this.maps = maps; //note # of maps
        }
    }
}
