/*
 * TMS 113 server/quests/MapleQuestActionType.java
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

public enum MapleQuestActionType
{
    UNDEFINED(-1),
    ask(0),
    buffItemID(1),
    dayByDay(2),
    end(3),
    exp(4),
    info(5),
    infoNumber(6),
    interval(7),
    item(8),
    job(9),
    lvmax(10),
    lvmin(11),
    map(12),
    message(13),
    money(14),
    nextQuest(15),
    no(16),
    npc(17),
    npcAct(18),
    petskill(19),
    petspeed(20),
    pettameness(21),
    pop(22),
    quest(23),
    skill(24),
    start(26),
    stop(27),
    yes(28);

    final byte type;

    MapleQuestActionType(final int type)
    {
        this.type = (byte) type;
    }

    public byte getType()
    {
        return this.type;
    }

    public static MapleQuestActionType getByType(final byte target)
    {
        for (final MapleQuestActionType type : MapleQuestActionType.values()) {
            if (type.getType() == target) {
                return type;
            }
        }

        return null;
    }

    public static MapleQuestActionType getByWZName(final String name)
    {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return UNDEFINED;
        }
    }
}
