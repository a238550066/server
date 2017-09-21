/*
 * TMS 113 server/quests/MapleQuestRequirementType.java
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

public enum MapleQuestRequirementType
{
    UNDEFINED(-1),
    dayByDay(0),
    dayOfWeek(1),
    end(2),
    endmeso(3),
    endscript(4),
    equipAllNeed(5),
    equipSelectNeed(6),
    fieldEnter(7),
    info(8),
    infoNumber(9),
    infoex(10),
    interval(11),
    item(12),
    job(13),
    level(14),
    lvmax(15),
    lvmin(16),
    mbcard(17),
    mbmin(18),
    mob(19),
    normalAutoStart(20),
    npc(21),
    partyQuest_S(22),
    pet(23),
    petAutoSpeakingLimit(24),
    petRecallLimit(25),
    pettamenessmax(26),
    pettamenessmin(27),
    pop(28),
    quest(29),
    questComplete(30),
    skill(31),
    start(32),
    startscript(33),
    tamingmoblevelmin(34),
    worldmax(35),
    worldmin(36);

    final byte type;

    MapleQuestRequirementType(final int type)
    {
        this.type = (byte) type;
    }

    public final byte getType()
    {
        return this.type;
    }

    public static MapleQuestRequirementType getByType(final byte target)
    {
        for (final MapleQuestRequirementType type : MapleQuestRequirementType.values()) {
            if (type.getType() == target) {
                return type;
            }
        }

        return null;
    }

    public static MapleQuestRequirementType getByWZName(final String name)
    {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return UNDEFINED;
        }
    }
}
