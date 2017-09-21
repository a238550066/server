/*
 * TMS 113 tools/DateTimeUtil.java
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
package tools;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeUtil
{
    public static String now()
    {
        return java.sql.Timestamp.valueOf(LocalDateTime.now()).toString();
    }

    public static String now(final LocalDateTime datetime)
    {
        return java.sql.Timestamp.valueOf(datetime).toString();
    }

    public static String now(final long timestamp)
    {
        return java.sql.Timestamp.valueOf(LocalDateTime.parse("" + timestamp, formatter())).toString();
    }

    public static String dueyExpiredAt(final boolean isQuick)
    {
        final LocalDateTime expiredAt = LocalDateTime.now().plusDays(29).plusHours(20);

        if (isQuick) {
            return java.sql.Timestamp.valueOf(expiredAt).toString();
        }

        return java.sql.Timestamp.valueOf(expiredAt.plusHours(12)).toString();
    }

    public static boolean isAfter(final String other)
    {
        return LocalDateTime.now().isAfter(LocalDateTime.parse(normalize(other), formatter()));
    }

    public static long toTimestamp(final String datetime)
    {
        return java.sql.Timestamp.valueOf(datetime).getTime();
    }

    private static DateTimeFormatter formatter()
    {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    }

    private static String normalize(final String datetime)
    {
        return  java.sql.Timestamp.valueOf(datetime).toString();
    }
}
