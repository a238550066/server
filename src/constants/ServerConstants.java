/*
 * TMS 113 constants/ServerConstants.java
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
package constants;

public class ServerConstants
{
    // Start of Poll
    public static final boolean PollEnabled = false;
    public static final String Poll_Question = "Are you mudkiz?";
    public static final String[] Poll_Answers = {"test1", "test2", "test3"};
    // End of Poll
    public static final short MAPLE_VERSION = 113;
    public static final String MAPLE_PATCH = "1";
    public static final boolean Use_Fixed_IV = false;

    public static final int MIN_MTS = 110;
    public static final int MTS_BASE = 100; //+1000 to everything in MSEA but cash is costly here
    public static final int MTS_TAX = 10; //+% to everything
    public static final int MTS_MESO = 5000; //mesos needed

    public enum PlayerGMRank
    {
        NORMAL('@', 0),
        INTERN('!', 1),
        GM('!', 2),
        ADMIN('!', 3);

        private final char prefix;
        private final int level;

        PlayerGMRank(final char ch, final int level)
        {
            this.prefix = ch;
            this.level = level;
        }

        public final char getPrefix()
        {
            return this.prefix;
        }

        public final int getLevel()
        {
            return this.level;
        }
    }

    public enum CommandType
    {
        NORMAL(0),
        TRADE(1);

        private final int level;

        CommandType(final int level)
        {
            this.level = level;
        }

        public final int getType()
        {
            return this.level;
        }
    }
}
