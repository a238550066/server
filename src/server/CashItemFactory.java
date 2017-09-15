/*
 * TMS 113 server/CashItemFactory.java
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
package server;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;

import database.DatabaseConnection;
import provider.MapleData;
import provider.MapleDataProviderFactory;
import provider.MapleDataTool;

public class CashItemFactory {

    private final static CashItemFactory instance = new CashItemFactory();
    private final static int[] topItems = new int[]{50401007, 40001014, 40001029, 40101002, 40101006};
    private boolean initialized = false;
    private final Map<Integer, CashItemInfo> items = new HashMap<>();
    private final Map<Integer, List<CashItemInfo>> packages = new HashMap<>();

    public static CashItemFactory getInstance()
    {
        return instance;
    }

    public void initialize()
    {
        System.out.println("載入購物商場物品...");

        try {
            final Connection con = DatabaseConnection.getConnection();
            final PreparedStatement ps = con.prepareStatement("SELECT * FROM `cashshop_items`");
            final ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                final int sn = rs.getInt("sn");
                final int itemId = rs.getInt("item_id");

                final CashItemInfo stats = new CashItemInfo(
                    sn,
                    itemId,
                    rs.getInt("count"),
                    rs.getInt("price"),
                    rs.getInt("period"),
                    rs.getInt("priority"),
                    rs.getInt("gender"),
                    rs.getInt("mark"),
                    rs.getInt("show_up"),
                    rs.getInt("package"),
                    rs.getInt("meso"),
                    rs.getInt("unk_1"),
                    rs.getInt("unk_2"),
                    rs.getInt("unk_3")
                );

                this.items.put(sn, stats);
            }

            for (CashItemInfo item : this.items.values()) {
                this.getPackageItems(item.getId());
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        this.initialized = true;
    }

    /**
     * item 對應 sn 列表
     */
    public final List<CashItemInfo> getPackageItems(int itemId)
    {
        if (this.packages.get(itemId) != null) {
            return this.packages.get(itemId);
        }

        final List<CashItemInfo> packageItems = new ArrayList<>();
        final MapleData b = MapleDataProviderFactory.getDataProvider(new File(System.getProperty("net.sf.odinms.wzpath") + "/Etc.wz")).getData("CashPackage.img");

        if (b == null || b.getChildByPath(itemId + "/SN") == null) {
            return null;
        }

        for (MapleData d : b.getChildByPath(itemId + "/SN").getChildren()) {
            packageItems.add(this.items.get(MapleDataTool.getIntConvert(d)));
        }

        this.packages.put(itemId, packageItems);

        return packageItems;
    }

    /**
     * 商品資訊
     */
    public final CashItemInfo getItem(int sn)
    {
        final CashItemInfo item = this.items.get(sn);

        if (item == null || !item.getShowUp()) {
            return null;
        }

        return item;
    }

    /**
     * 所有商品資訊
     */
    public final Collection<CashItemInfo> getAllItems()
    {
        if (!this.initialized) {
            initialize();
        }

        return this.items.values();
    }

    /**
     * 人氣商品
     */
    public final int[] getTopItems()
    {
        return topItems;
    }
}
