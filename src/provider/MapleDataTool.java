/*
 * TMS 113 provider/MapleDataTool.java
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
package provider;

import provider.WzXML.MapleDataType;

import java.awt.Point;

public class MapleDataTool
{
    public static String getString(final MapleData data)
    {
        return ((String) data.getData());
    }


    public static String getString(final MapleData data, final String def)
    {
        if (data == null || data.getData() == null) {
            return def;
        }

        return ((String) data.getData());
    }

    public static String getString(final String path, final MapleData data)
    {
        return getString(data.getChildByPath(path));
    }

    public static String getString(final String path, final MapleData data, final String def)
    {
        return getString(data.getChildByPath(path), def);
    }

    public static double getDouble(final MapleData data)
    {
        return (Double) data.getData();
    }

    public static float getFloat(final MapleData data)
    {
        return (Float) data.getData();
    }

    public static float getFloat(final MapleData data, float def)
    {
        if (data == null || data.getData() == null) {
            return def;
        }

        return (Float) data.getData();
    }

    public static int getInt(final MapleData data)
    {
        return (Integer) data.getData();
    }

    public static int getInt(final MapleData data, final int def)
    {
        if (data == null || data.getData() == null) {
            return def;
        } else if (data.getType() == MapleDataType.STRING) {
            return Integer.parseInt(getString(data));
        } else if (data.getType() == MapleDataType.SHORT) {
            return Integer.valueOf((Short) data.getData());
        }

        return (Integer) data.getData();
    }

    public static int getInt(final String path, final MapleData data)
    {
        return getInt(data.getChildByPath(path));
    }

    public static int getIntConvert(final MapleData data)
    {
        if (data.getType() == MapleDataType.STRING) {
            return Integer.parseInt(getString(data));
        }

        return getInt(data);
    }

    public static int getIntConvert(final String path, final MapleData data)
    {
        final MapleData d = data.getChildByPath(path);

        if (d.getType() == MapleDataType.STRING) {
            return Integer.parseInt(getString(d));
        }

        return getInt(d);
    }

    public static int getInt(final String path, final MapleData data, final int def)
    {
        return getInt(data.getChildByPath(path), def);
    }

    public static int getIntConvert(final String path, final MapleData data, final int def)
    {
        if (data == null) {
            return def;
        }

        final MapleData d = data.getChildByPath(path);

        if (d == null) {
            return def;
        } else if (d.getType() == MapleDataType.STRING) {
            try {
                return Integer.parseInt(getString(d));
            } catch (final NumberFormatException nfe) {
                return def;
            }
        }

        return getInt(d, def);
    }

    private static Point getPoint(final MapleData data)
    {
        return ((Point) data.getData());
    }

    public static Point getPoint(final String path, final MapleData data)
    {
        return getPoint(data.getChildByPath(path));
    }
}
