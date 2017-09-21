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
    private String customData;

    public MapleQuestStatus(final MapleQuest quest, final byte status)
    {
        this.quest = quest;
        this.setStatus(status);
        this.completionTime = System.currentTimeMillis();

        if (status == 1) { // Started
            if (!quest.getRelevantMobs().isEmpty()) {
                registerMobs();
            }
        }
    }

    public MapleQuestStatus(final MapleQuest quest, final byte status, final int npc)
    {
        this.quest = quest;
        this.setStatus(status);
        this.setNpc(npc);
        this.completionTime = System.currentTimeMillis();

        if (status == 1) { // Started
            if (!quest.getRelevantMobs().isEmpty()) {
                registerMobs();
            }
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
        for (final Map.Entry<Integer, Integer> qs : quest.getRelevantMobs().entrySet()) {
            if (qs.getKey() == mobId) {
                return qs.getValue();
            }
        }

        return 0;
    }

    public final boolean mobKilled(final int id, final int skillID)
    {
        if (quest != null && quest.getSkillID() > 0) {
            if (quest.getSkillID() != skillID) {
                return false;
            }
        }

        final Integer mob = killedMobs.get(id);

        if (mob != null) {
            final int mo = maxMob(id);

            if (mob >= mo) {
                return false; //nothing happened
            }

            killedMobs.put(id, Math.min(mob + 1, mo));

            return true;
        }

        for (Map.Entry<Integer, Integer> mo : killedMobs.entrySet()) {
            if (questCount(mo.getKey(), id)) {
                final int mobb = maxMob(mo.getKey());
                if (mo.getValue() >= mobb) {
                    return false; //nothing
                }
                killedMobs.put(mo.getKey(), Math.min(mo.getValue() + 1, mobb));
                return true;
            }
        } //i doubt this
        return false;
    }

    private final boolean questCount(final int mo, final int id) {
        if (MapleLifeFactory.getQuestCount(mo) != null) {
            for (int i : MapleLifeFactory.getQuestCount(mo)) {
                if (i == id) {
                    return true;
                }
            }
        }
        return false;
    }

    public final void setMobKills(final int id, final int count) {
        if (killedMobs == null) {
            registerMobs(); //lol
        }
        killedMobs.put(id, count);
    }

    public final boolean hasMobKills() {
        if (killedMobs == null) {
            return false;
        }
        return killedMobs.size() > 0;
    }

    public final int getMobKills(final int id)
    {
        final Integer mob = killedMobs.get(id);
        if (mob == null) {
            return 0;
        }
        return mob;
    }

    public final Map<Integer, Integer> getMobKills() {
        return this.killedMobs;
    }

    public final long getCompletionTime()
    {
        return this.completionTime;
    }

    public final void setCompletionTime(final long completionTime)
    {
        this.completionTime = completionTime;
    }

    public final void setCustomData(final String customData) {
        this.customData = customData;
    }

    public final String getCustomData() {
        return customData;
    }
}
