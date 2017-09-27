/*
 * TMS 113 provider/MapleDataProviderFactory.java
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

import provider.WzXML.XMLWZFile;

import java.io.File;

public class MapleDataProviderFactory
{
    private static MapleDataProvider getWZ(final Object in)
    {
        if (in instanceof File) {
            final File fileIn = (File) in;

            return new XMLWZFile(fileIn);
        }

        throw new IllegalArgumentException("Can't create data provider for input " + in);
    }

    public static MapleDataProvider getDataProvider(final Object in)
    {
        return getWZ(in);
    }

    public static File fileInWZPath(final String filename)
    {
        return new File("wz", filename);
    }
}
