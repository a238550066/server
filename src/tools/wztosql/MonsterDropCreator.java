/*
 * TMS 113 tools/wztosql/MonsterDropCreator.java
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
import java.io.IOException;
import java.rmi.NotBoundException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;

import database.DatabaseConnection;
import provider.MapleData;
import provider.MapleDataProvider;
import provider.MapleDataProviderFactory;
import provider.MapleDataTool;
import tools.Pair;
import tools.StringUtil;

public class MonsterDropCreator
{
    private static List<Pair<Integer, String>> itemNameCache = new ArrayList<>();
    private static List<Pair<Integer, MobInfo>> mobCache = new ArrayList<>();
    private static Map<Integer, Boolean> bossCache = new HashMap<>();
    private static final MapleDataProvider wzData = MapleDataProviderFactory.getDataProvider(new File(System.getProperty("net.sf.odinms.wzpath") + "/String.wz"));

    public static void main(String[] args) throws IOException, NotBoundException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException, MalformedObjectNameException
    {
        final Connection con = DatabaseConnection.getConnection();

        getAllItems();
        getAllMobs();

        System.out.println("匯入怪物圖鑑...");

        for (MapleData monster : wzData.getData("MonsterBook.img").getChildren()) {
            int monsterId = Integer.parseInt(monster.getName());

            if (monster.getChildByPath("reward").getChildren().isEmpty()) {
                continue;
            }

            for (MapleData drop : monster.getChildByPath("reward")) {
                final int itemId = MapleDataTool.getInt(drop);
                final int rate = getChance(itemId, monsterId, bossCache.containsKey(monsterId));
                final String name = getItemName(itemId);

                if (name == null) {
                    continue;
                }

                for (int i = 0; i < multipleDropsIncrement(itemId, monsterId); i++) {
                    try {
                        final PreparedStatement ps = con.prepareStatement("INSERT INTO `drop_data` (`dropper_id`, `item_id`, `minimum_quantity`, `maximum_quantity`, `quest_id`, `chance`, `name`) VALUES (?, ?, ?, ?, ?, ?, ?)");

                        ps.setInt(1, monsterId);
                        ps.setInt(2, itemId);
                        ps.setInt(3, 1);
                        ps.setInt(4, 1);
                        ps.setInt(5, 0);
                        ps.setInt(6, rate);
                        ps.setString(7, name);

                        ps.executeUpdate();
                        ps.close();
                    } catch (SQLException ex) {
                        System.err.println(ex.toString());
                    }
                }
            }
        }

        System.out.println("匯入怪物卡...");

        final int beginOfMonsterCardId = 2380000;
        final int endOfMonsterCardId = 2388070;

        for (Pair Pair : itemNameCache) {
            if (((Integer) Pair.getLeft() >= beginOfMonsterCardId) && ((Integer) Pair.getLeft() <= endOfMonsterCardId)) {
                String bookName = (String) Pair.getRight();

                if (bookName.contains("卡片")) {
                    bookName = bookName.substring(0, bookName.length() - 2);
                } else if (bookName.contains("卡")) {
                    bookName = bookName.substring(0, bookName.length() - 1);
                }

                for (Pair Pair_ : mobCache) {
                    if (((MobInfo) Pair_.getRight()).getName().equalsIgnoreCase(bookName)) {
                        try {
                            int chance = 1000;

                            if (((MobInfo) Pair_.getRight()).getBoss() > 0) {
                                chance *= 25;
                            }

                            final PreparedStatement ps = con.prepareStatement("INSERT INTO `drop_data` (`dropper_id`, `item_id`, `minimum_quantity`, `maximum_quantity`, `quest_id`, `chance`, `name`) VALUES (?, ?, ?, ?, ?, ?, ?)");

                            ps.setInt(1, (Integer) Pair_.getLeft());
                            ps.setInt(2, (Integer) Pair.getLeft());
                            ps.setInt(3, 1);
                            ps.setInt(4, 1);
                            ps.setInt(5, 0);
                            ps.setInt(6, chance);
                            ps.setString(7, (String) Pair.getRight());

                            ps.executeUpdate();
                            ps.close();
                        } catch (SQLException ex) {
                            System.err.println(ex.toString());
                        }

                        break;
                    }
                }
            }
        }

        System.out.println("更新物品名稱...");

        try {
            final PreparedStatement ps = con.prepareStatement("SELECT * FROM `drop_data` WHERE `name` IS NULL");

            final ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                final Integer id = rs.getInt("id");
                final Integer itemId = rs.getInt("item_id");

                final String name = getItemName(itemId);

                if (name == null) {
                    System.out.println("物品名稱不存在，" + "item id：" + itemId + " id：" + id);
                } else {
                    final PreparedStatement pss = con.prepareStatement("UPDATE `drop_data` SET `name` = ? WHERE `id` = ?");

                    pss.setString(1, name);
                    pss.setInt(2, id);

                    pss.executeUpdate();
                    pss.close();
                }
            }

            ps.close();
            rs.close();
        } catch (SQLException ex) {
            System.err.println(ex.toString());
        }
    }

    private static String getItemName(int itemId)
    {
        for (Pair Pair : itemNameCache) {
            if ((Integer) Pair.getLeft() == itemId) {
                return (String) Pair.getRight();
            }
        }

        return null;
    }

    /**
     * 物品重複數量
     */
    private static int multipleDropsIncrement(int itemId, int mobId) {
        switch (itemId)  {
            case 1002357:
            case 1002390:
            case 1002430:
            case 1002926:
            case 1002927:
                return 3;
            case 1122000:
                return 2;
            case 4021010:
                return 3;
            case 1002972:
                return 2;
            case 4000172:
                if (mobId == 7220001) {
                    return 4;
                }
                return 1;
            case 4000000:
            case 4000003:
            case 4000005:
            case 4000016:
            case 4000018:
            case 4000019:
            case 4000021:
            case 4000026:
            case 4000029:
            case 4000031:
            case 4000032:
            case 4000033:
            case 4000043:
            case 4000044:
            case 4000073:
            case 4000074:
            case 4000113:
            case 4000114:
            case 4000115:
            case 4000117:
            case 4000118:
            case 4000119:
            case 4000166:
            case 4000167:
            case 4000195:
            case 4000268:
            case 4000269:
            case 4000270:
            case 4000283:
            case 4000284:
            case 4000285:
            case 4000289:
            case 4000298:
            case 4000329:
            case 4000330:
            case 4000331:
            case 4000356:
            case 4000364:
            case 4000365:
                if ((mobId == 2220000) || (mobId == 3220000) || (mobId == 3220001) || (mobId == 4220000) || (mobId == 5220000) || (mobId == 5220002) || (mobId == 5220003) || (mobId == 6220000) || (mobId == 4000119) || (mobId == 7220000) || (mobId == 7220002) || (mobId == 8220000) || (mobId == 8220002) || (mobId == 8220003)) {
                    return 3;
                }
                return 1;
        }
        return 1;
    }

    private static int getChance(int itemId, int mobId, boolean boss)
    {
        switch (itemId / 10000) {
            case 100:
                switch (itemId) {
                    case 1002357:
                    case 1002390:
                    case 1002430:
                    case 1002905:
                    case 1002906:
                    case 1002926:
                    case 1002927:
                    case 1002972:
                        return 300000;
                }
                return 1500;
            case 103:
                switch (itemId) {
                    case 1032062:
                        return 100;
                }
                return 1000;
            case 105:
            case 109:
                switch (itemId) {
                    case 1092049:
                        return 100;
                }
                return 700;
            case 104:
            case 106:
            case 107:
                switch (itemId) {
                    case 1072369:
                        return 300000;
                }
                return 800;
            case 108:
            case 110:
                return 1000;
            case 112:
                switch (itemId) {
                    case 1122000:
                        return 300000;
                    case 1122011:
                    case 1122012:
                        return 800000;
                }
            case 130:
            case 131:
            case 132:
            case 137:
                switch (itemId) {
                    case 1372049:
                        return 999999;
                }
                return 700;
            case 138:
            case 140:
            case 141:
            case 142:
            case 144:
                return 700;
            case 133:
            case 143:
            case 145:
            case 146:
            case 147:
            case 148:
            case 149:
                return 500;
            case 204:
                switch (itemId) {
                    case 2049000:
                        return 150;
                }
                return 300;
            case 205:
                return 50000;
            case 206:
                return 30000;
            case 228:
                return 30000;
            case 229:
                switch (itemId) {
                    case 2290096:
                        return 800000;
                    case 2290125:
                        return 100000;
                }
                return 500;
            case 233:
                switch (itemId) {
                    case 2330007:
                        return 50;
                }
                return 500;
            case 400:
                switch (itemId) {
                    case 4000021:
                        return 50000;
                    case 4001094:
                        return 999999;
                    case 4001000:
                        return 5000;
                    case 4000157:
                        return 100000;
                    case 4001023:
                    case 4001024:
                        return 999999;
                    case 4000244:
                    case 4000245:
                        return 2000;
                    case 4001005:
                        return 5000;
                    case 4001006:
                        return 10000;
                    case 4000017:
                    case 4000082:
                        return 40000;
                    case 4000446:
                    case 4000451:
                    case 4000456:
                        return 10000;
                    case 4000459:
                        return 20000;
                    case 4000030:
                        return 60000;
                    case 4000339:
                        return 70000;
                    case 4000313:
                    case 4007000:
                    case 4007001:
                    case 4007002:
                    case 4007003:
                    case 4007004:
                    case 4007005:
                    case 4007006:
                    case 4007007:
                    case 4031456:
                        return 100000;
                    case 4001126:
                        return 500000;
                }
                switch (itemId / 1000) {
                    case 4000:
                    case 4001:
                        return 600000;
                    case 4003:
                        return 200000;
                    case 4004:
                    case 4006:
                        return 10000;
                    case 4005:
                        return 1000;
                    case 4002:
                }
            case 401:
            case 402:
                switch (itemId) {
                    case 4020009:
                        return 5000;
                    case 4021010:
                        return 300000;
                }
                return 9000;
            case 403:
                switch (itemId) {
                    case 4032024:
                        return 50000;
                    case 4032181:
                        return boss ? 999999 : 300000;
                    case 4032025:
                    case 4032155:
                    case 4032156:
                    case 4032159:
                    case 4032161:
                    case 4032163:
                        return 600000;
                    case 4032166:
                    case 4032167:
                    case 4032168:
                        return 10000;
                    case 4032151:
                    case 4032158:
                    case 4032164:
                    case 4032180:
                        return 2000;
                    case 4032152:
                    case 4032153:
                    case 4032154:
                        return 4000;
                }
                return 300;
            case 413:
                return 6000;
            case 416:
                return 6000;
        }

        switch (itemId / 1000000) {
            case 1:
                return 999999;
            case 2:
                switch (itemId) {
                    case 2000004:
                    case 2000005:
                        return boss ? 999999 : 20000;
                    case 2000006:
                        return mobId == 9420540 ? 50000 : boss ? 999999 : 20000;
                    case 2022345:
                        return boss ? 999999 : 3000;
                    case 2012002:
                        return 6000;
                    case 2020013:
                    case 2020015:
                        return boss ? 999999 : 20000;
                    case 2060000:
                    case 2060001:
                    case 2061000:
                    case 2061001:
                        return 25000;
                    case 2070000:
                    case 2070001:
                    case 2070002:
                    case 2070003:
                    case 2070004:
                    case 2070008:
                    case 2070009:
                    case 2070010:
                        return 500;
                    case 2070005:
                        return 400;
                    case 2070006:
                    case 2070007:
                        return 200;
                    case 2070012:
                    case 2070013:
                        return 1500;
                    case 2070019:
                        return 100;
                    case 2210006:
                        return 999999;
                }
                return 20000;
            case 3:
                switch (itemId) {
                    case 3010007:
                    case 3010008:
                        return 500;
                }
                return 2000;
        }

        System.out.println("未處理的數據, ID : " + itemId);

        return 999999;
    }

    /**
     * 載入物品名稱
     */
    private static void getAllItems()
    {
        System.out.println("載入物品名稱...");

        final List<Pair<Integer, String>> itemPairs = new ArrayList<>();

        final String types[] = {"Cash.img", "Consume.img", "Ins.img", "Pet.img", "Eqp.img", "Etc.img"};

        for (final String type : types) {
            List<MapleData> outer = new ArrayList<>();

            switch (type) {
                case "Eqp.img":
                    outer = wzData.getData(type).getChildByPath("Eqp").getChildren();
                    break;
                case "Etc.img":
                    outer.add(wzData.getData(type).getChildByPath("Etc"));
                    break;
                default:
                    outer.add(wzData.getData(type));
                    break;
            }

            for (MapleData inner : outer) {
                for (final MapleData folder : inner.getChildren()) {
                    itemPairs.add(new Pair<>(
                        Integer.parseInt(folder.getName()),
                        MapleDataTool.getString("name", folder, "NO-NAME")
                    ));
                }
            }
        }

        itemNameCache.addAll(itemPairs);
    }

    /**
     * 載入怪物資料
     */
    private static void getAllMobs()
    {
        System.out.println("載入怪物資料...");

        final List<Pair<Integer, MobInfo>> itemPairs = new ArrayList<>();

        final MapleDataProvider mobData = MapleDataProviderFactory.getDataProvider(new File(System.getProperty("net.sf.odinms.wzpath") + "/Mob.wz"));

        for (MapleData folder : wzData.getData("Mob.img").getChildren()) {
            try  {
                final int id = Integer.parseInt(folder.getName());

                final MapleData monsterData = mobData.getData(StringUtil.getLeftPaddedStr(Integer.toString(id) + ".img", '0', 11));

                final int boss = MapleDataTool.getIntConvert("boss", monsterData.getChildByPath("info"), 0);

                if (boss > 0) {
                    bossCache.put(id, Boolean.TRUE);
                }

                MobInfo mobInfo = new MobInfo(boss, MapleDataTool.getString("name", folder, "NO-NAME"));

                itemPairs.add(new Pair<>(id, mobInfo));
            } catch (Exception ignored) {
            }
        }

        mobCache.addAll(itemPairs);
    }

    public static class MobInfo
    {
        public final int boss;
        public final String name;

        MobInfo(final int boss, final String name)
        {
            this.boss = boss;
            this.name = name;
        }

        public final int getBoss()
        {
            return this.boss;
        }

        public final String getName()
        {
            return this.name;
        }
    }
}
