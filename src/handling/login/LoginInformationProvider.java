/*
 * TMS 113 handling/login/LoginInformationProvider.java
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
package handling.login;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

import provider.MapleData;
import provider.MapleDataProviderFactory;
import provider.MapleDataTool;

public class LoginInformationProvider
{
    private final static LoginInformationProvider instance = new LoginInformationProvider();
    private final List<String> forbiddenName = new ArrayList<>();

    public static LoginInformationProvider getInstance()
    {
        return instance;
    }

    private LoginInformationProvider()
    {
        System.out.println("載入禁用名稱列表...");

        final String wzPath = System.getProperty("net.sf.odinms.wzpath");
        final MapleData nameData = MapleDataProviderFactory.getDataProvider(new File(wzPath + "/Etc.wz")).getData("ForbiddenName.img");

        for (final MapleData data : nameData.getChildren()) {
            this.forbiddenName.add(MapleDataTool.getString(data));
        }
    }

    /**
     * 檢查是否為禁用名稱
     */
    public final boolean isForbiddenName(final String in)
    {
        for (final String name : this.forbiddenName) {
            if (in.contains(name)) {
                return true;
            }
        }

        return false;
    }
}
