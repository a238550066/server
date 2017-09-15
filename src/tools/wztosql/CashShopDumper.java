/*
 * TMS 113 tools/wztosql/CashShopDumper.java
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
package tools.wztosql;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import client.inventory.MapleInventoryType;
import database.DatabaseConnection;
import provider.MapleData;
import provider.MapleDataProviderFactory;
import provider.MapleDataTool;
import server.CashItemFactory;
import server.MapleItemInformationProvider;

public class CashShopDumper
{
    public static void main(String[] args)
    {
        final Connection con = DatabaseConnection.getConnection();

        CashItemFactory.getInstance().initialize();

        for (MapleData field : MapleDataProviderFactory.getDataProvider(new File(System.getProperty("net.sf.odinms.wzpath") + "/Etc.wz")).getData("Commodity.img").getChildren()) {
            try {
                final int sn = MapleDataTool.getIntConvert("SN", field, -1);
                final int itemId = MapleDataTool.getIntConvert("ItemId", field, -1);
                final int count = MapleDataTool.getIntConvert("Count", field, -1);
                final int price = MapleDataTool.getIntConvert("Price", field, -1);
                final int period = MapleDataTool.getIntConvert("Period", field, -1);
                final int priority = MapleDataTool.getIntConvert("Priority", field, -1);
                final int gender = MapleDataTool.getIntConvert("Gender", field, -1);

                if (sn == -1 || itemId == -1 || count == -1 || price == -1 || period == -1 || priority == -1 || gender == -1) {
                    continue;
                }

                final String name = MapleItemInformationProvider.getInstance().getName(itemId);

                // 略過名稱不存在的物品
                if (name == null) {
                    continue;
                }

                if (MapleItemInformationProvider.getInstance().getInventoryType(itemId) == MapleInventoryType.EQUIP) {
                    if (!MapleItemInformationProvider.getInstance().isCashItem(itemId) || period > 0) {
                        // 略過非點商裝備或有期限的裝備
                        continue;
                    }
                }

                final PreparedStatement ps = con.prepareStatement("INSERT INTO `cashshop_items` (`name`, `sn`, `item_id`, `count`, `price`, `period`, `priority`, `gender`) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");

                ps.setString(1, name);
                ps.setInt(2, sn);
                ps.setInt(3, itemId);
                ps.setInt(4, count);
                ps.setInt(5, price);
                ps.setInt(6, period);
                ps.setInt(7, priority);
                ps.setInt(8, gender);

                ps.executeUpdate();
                ps.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
