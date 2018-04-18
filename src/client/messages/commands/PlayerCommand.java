package client.messages.commands;

import client.inventory.IItem;
import client.inventory.MapleInventoryType;
import constants.GameConstants;
import client.MapleClient;
import constants.ServerConstants.PlayerGMRank;
import handling.world.World;
import scripting.NPCScriptManager;
import server.MapleInventoryManipulator;
import tools.ArrayMap;
import tools.MaplePacketCreator;
import tools.Pair;

import java.util.Map;

/**
 * @author Emilyx3
 * @author freedom
 */
public class PlayerCommand {

    public static PlayerGMRank getPlayerLevelRequired()
    {
        return PlayerGMRank.NORMAL;
    }

    /**
     * 清除物品欄
     */
    public static class ClearInv extends CommandExecute
    {
        @Override
        public int execute(MapleClient c, String[] splitted)
        {
            String errMsg = "格式錯誤： @clearinv <eqp/eq/u/s/e/c> (穿著裝備/裝備/消耗/裝飾/其他/特殊)";

            if (2 != splitted.length) {
                c.getPlayer().dropMessage(6, errMsg);

                return 0;
            }

            MapleInventoryType inventory = null;

            switch (splitted[1]) {
                case "eqp": inventory = MapleInventoryType.EQUIPPED; break;
                case "eq": inventory = MapleInventoryType.EQUIP; break;
                case "u": inventory = MapleInventoryType.USE; break;
                case "s": inventory = MapleInventoryType.SETUP; break;
                case "e": inventory = MapleInventoryType.ETC; break;
                case "c": inventory = MapleInventoryType.CASH; break;
                default: c.getPlayer().dropMessage(6, errMsg); break;
            }

            if (null == inventory) {
                return 0;
            }

            java.util.Map<Pair<Short, Short>, MapleInventoryType> eqs = new ArrayMap<Pair<Short, Short>, MapleInventoryType>();

            for (IItem item : c.getPlayer().getInventory(inventory)) {
                eqs.put(new Pair<Short, Short>(item.getPosition(), item.getQuantity()), inventory);
            }

            for (Map.Entry<Pair<Short, Short>, MapleInventoryType> eq : eqs.entrySet()) {
                MapleInventoryManipulator.removeFromSlot(c, eq.getValue(), eq.getKey().left, eq.getKey().right, false, false);
            }

            c.getPlayer().dropMessage(6, "清除成功");

            return 0;
        }
    }

    /**
     * 丟棄點數商品
     */
    public static class DropCash extends CommandExecute
    {
        private MapleClient client;

        private int endSession()
        {
            client.getPlayer().dropMessage(1, "此地圖無法使用指此令");

            return 0;
        }

        @Override
        public int execute(MapleClient c, String[] splitted)
        {
            client = c;

            for (int i : GameConstants.blockedMaps) {
                if (c.getPlayer().getMapId() == i) {
                    return endSession();
                }
            }

            if (c.getPlayer().getMap().getSquadByMap() != null || c.getPlayer().getEventInstance() != null || c.getPlayer().getMap().getEMByMap() != null || c.getPlayer().getMapId() >= 990000000/* || FieldLimitType.VipRock.check(c.getPlayer().getMap().getFieldLimit())*/) {
                return endSession();
            }

            if ((c.getPlayer().getMapId() >= 680000210 && c.getPlayer().getMapId() <= 680000502) || (c.getPlayer().getMapId() / 1000 == 980000 && c.getPlayer().getMapId() != 980000000) || (c.getPlayer().getMapId() / 100 == 1030008) || (c.getPlayer().getMapId() / 100 == 922010) || (c.getPlayer().getMapId() / 10 == 13003000)) {
                return endSession();
            }

            NPCScriptManager.getInstance().start(c, 9010017);

            return 0;
        }
    }

    /**
     * 解除卡定狀態
     */
    public static class EA extends CommandExecute
    {
        @Override
        public int execute(MapleClient c, String[] splitted)
        {
            NPCScriptManager.getInstance().dispose(c);

            c.getSession().write(MaplePacketCreator.enableActions());

            c.getPlayer().dropMessage(1, "解卡完畢");

            return 0;
        }
    }

    /**
     * 顯示伺服器線上人數
     */
    public static class Online extends CommandExecute
    {
        @Override
        public int execute(MapleClient c, String[] splitted)
        {
            c.getPlayer().dropMessage(6, "目前在線人數：" + World.getConnected().get(0) + " 人");

            return 0;
        }
    }

    /**
     * 顯示伺服器倍率
     */
    public static class Rate extends CommandExecute
    {
        @Override
        public int execute(MapleClient c, String[] splitted)
        {
            c.getPlayer().dropMessage(6, "經驗值倍率：" + c.getChannelServer().getExpRate() + " 掉寶倍率：" + c.getChannelServer().getDropRate() + " 楓幣倍率：" + c.getChannelServer().getMesoRate());

            return 0;
        }
    }

    /**
     * 儲存玩家資料
     */
    public static class Save extends CommandExecute
    {
        @Override
        public int execute(MapleClient c, String[] splitted)
        {
            c.getPlayer().saveToDB(false, true);

            c.getPlayer().dropMessage(6, "儲存完畢");

            return 0;
        }
    }

    /**
     * 顯示目前所在地圖代號及角色座標
     */
    public static class WhereAmI extends CommandExecute
    {
        @Override
        public int execute(MapleClient c, String[] splitted)
        {
            c.getPlayer().dropMessage(5, "目前地圖 " + c.getPlayer().getMap().getId() + " 座標 (" + String.valueOf(c.getPlayer().getPosition().x) + " , " + String.valueOf(c.getPlayer().getPosition().y) + ")");

            return 1;
        }
    }

    /**
     * 顯示玩家指令
     */
    public static class Help extends CommandExecute
    {
        @Override
        public int execute(MapleClient c, String[] splitted)
        {
            c.getPlayer().dropMessage(5, "指令列表 :");
            c.getPlayer().dropMessage(5, "@clearinv <all/eqp/eq/u/s/e/c> - 清除物品欄 (全部/穿著裝備/裝備/消耗/裝飾/其他/特殊)");
            c.getPlayer().dropMessage(5, "@ea - 解除卡定狀態");
            c.getPlayer().dropMessage(5, "@dropcash - 丟棄點裝");
            c.getPlayer().dropMessage(5, "@online - 查看在線人數");
            c.getPlayer().dropMessage(5, "@rate - 查看倍率");
            c.getPlayer().dropMessage(5, "@save - 存檔");
            c.getPlayer().dropMessage(5, "@whereami - 顯示目前所在地圖 id 及座標");

            return 0;
        }
    }
}
