/*
 * TMS 113 client/NewMapleQuestStatus.java
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
package client;

import server.life.MapleLifeFactory;
import server.quests.MapleQuest;

import java.util.LinkedHashMap;
import java.util.Map;

public class MapleQuestStatus
{
    private transient MapleQuest quest;
    private byte status;
    private String info;
    private int forfeited;
    private int npc;
    private long completionTime;
    private Map<Integer, Integer> killedMobs = null;
    private String data;
    private boolean changed = false;

    public MapleQuestStatus(final MapleQuest quest, final byte status)
    {
        this.quest = quest;
        this.setStatus(status);
        this.completionTime = System.currentTimeMillis();

        if (status == 1 && !quest.getRelevantMobs().isEmpty()) {
            this.registerMobs();
        }
    }

    public MapleQuestStatus(final MapleQuest quest, final byte status, final int npc)
    {
        this.quest = quest;
        this.setStatus(status);
        this.setNpc(npc);
        this.completionTime = System.currentTimeMillis();

        if (status == 1 && !quest.getRelevantMobs().isEmpty()) {
            this.registerMobs();
        }
    }

    public final MapleQuest getQuest()
    {
        return this.quest;
    }

    public final byte getStatus()
    {
        return this.status;
    }

    public final void setStatus(final byte status)
    {
        this.status = status;
    }

    public final String getInfo()
    {
        return this.info;
    }

    public final void setInfo(final String info)
    {
        this.info = info;
    }

    public final int getForfeited()
    {
        return this.forfeited;
    }

    public final void setForfeited(final int forfeited)
    {
        this.forfeited = forfeited;
    }

    public final int getNpc()
    {
        return this.npc;
    }

    public final void setNpc(final int npc)
    {
        this.npc = npc;
    }

    public final String getData() {
        return this.data;
    }

    public final void setData(final String data)
    {
        this.data = data;
    }

    public final long getCompletionTime()
    {
        return this.completionTime;
    }

    public final void setCompletionTime(final long completionTime)
    {
        this.completionTime = completionTime;
    }

    public final boolean isChanged()
    {
        return this.changed;
    }

    public final void setChanged(final boolean changed)
    {
        this.changed = changed;
    }

    final boolean mobKilled(final int mobId, final int skillID)
    {
        if (this.quest != null && this.quest.getSkillID() > 0) {
            if (this.quest.getSkillID() != skillID) {
                return false;
            }
        }

        final Integer count = this.killedMobs.get(mobId);

        if (count != null) {
            final int maxCount = this.maxMob(mobId);

            if (count >= maxCount) {
                return false;
            }

            this.killedMobs.put(mobId, Math.min(count + 1, maxCount));

            return true;
        }

        for (final Map.Entry<Integer, Integer> mob : this.killedMobs.entrySet()) {
            if (this.questCount(mob.getKey(), mobId)) {
                final int maxCount = this.maxMob(mob.getKey());

                if (mob.getValue() >= maxCount) {
                    return false;
                }

                this.killedMobs.put(mob.getKey(), Math.min(mob.getValue() + 1, maxCount));

                return true;
            }
        }

        return false;
    }

    private boolean questCount(final int mobId, final int targetMobId)
    {
        if (MapleLifeFactory.getQuestCount(mobId) != null) {
            for (final int count : MapleLifeFactory.getQuestCount(mobId)) {
                if (count == targetMobId) {
                    return true;
                }
            }
        }

        return false;
    }

    public final void setMobKills(final int mobId, final int count)
    {
        if (this.killedMobs == null) {
            this.registerMobs();
        }

        this.killedMobs.put(mobId, count);
    }

    public final boolean hasMobKills()
    {
        return this.killedMobs != null && this.killedMobs.size() > 0;
    }

    public final int getMobKills(final int mobId)
    {
        final Integer count = this.killedMobs.get(mobId);

        if (count == null) {
            return 0;
        }

        return count;
    }

    public final Map<Integer, Integer> getMobKills()
    {
        return this.killedMobs;
    }

    private void registerMobs()
    {
        this.killedMobs = new LinkedHashMap<>();

        for (final int i : quest.getRelevantMobs().keySet())
        {
            killedMobs.put(i, 0);
        }
    }

    private int maxMob(final int mobId)
    {
        for (final Map.Entry<Integer, Integer> quest : this.quest.getRelevantMobs().entrySet()) {
            if (quest.getKey() == mobId) {
                return quest.getValue();
            }
        }

        return 0;
    }
}
