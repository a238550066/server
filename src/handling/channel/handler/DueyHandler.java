/*
 * TMS 113 handling/channel/handler/DueyHandler.java
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
package handling.channel.handler;

import client.MapleCharacter;
import client.MapleCharacterUtil;
import client.MapleClient;
import client.inventory.*;
import constants.GameConstants;
import database.DatabaseConnection;
import server.MapleDueyActions;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import tools.DateTimeUtil;
import tools.MaplePacketCreator;
import tools.data.input.SeekableLittleEndianAccessor;

import java.sql.*;
import java.util.*;

public class DueyHandler
{
    private static final byte
        INCORRECT_SECOND_PASSWORD = 9,
        RETRIEVE_PACKAGES = 10,
        MESO_NOT_ENOUGH = 12,
        INVALID_REQUEST = 13,
        INVALID_CHARACTER_NAME = 14,
        SAME_ACCOUNT = 15,
        BOX_ALREADY_FULL = 16,
        TARGET_CANT_RECEIVE = 17,
        SUCCESSFUL_DELIVER = 19;

    private static final int quickDeliveryTicket = 5330000;
    private static boolean isQuickDelivery = false;

    /*
     * 9：第二組密碼錯誤
     * 12：楓幣不足！
     * 13：此為不正確的要求！
     * 14：請重新確認收件人的名稱！
     * 15：無法送件給同一帳號內的角色！
     * 16：宅配保管箱已滿！
     * 17：角色無法收取宅配！
     * 18：對方宅配保管箱內，有數量限制的道具！
     */
    public static void operate(final SeekableLittleEndianAccessor slea, final MapleClient c)
    {
        final byte operation = slea.readByte();

        switch (operation) {
            case 1: // 開啟宅配人員對話
                final String password = slea.readMapleAsciiString();

                if (c.getSecondPassword() == null) { // 第二組密碼預設只在登入階段保留，因此需重新載入
                    c.reloadSecondPassword();
                }

                if (!c.checkSecondPassword(password)) { // 驗證第二組密碼
                    c.getSession().write(MaplePacketCreator.sendDuey(INCORRECT_SECOND_PASSWORD, null));
                } else { // 驗證成功，讀取保管箱物品
                    c.getSession().write(MaplePacketCreator.sendDuey(RETRIEVE_PACKAGES, retrievePackages(c.getPlayer())));
                }

                break;
            case 3: // 寄送包裹
                if (c.getPlayer().getConversation() != 2) {
                    return;
                }

                final byte inventId = slea.readByte();
                final short itemPos = slea.readShort();
                final short amount = slea.readShort();
                final int mesos = slea.readInt();
                final String recipient = slea.readMapleAsciiString();
                isQuickDelivery = slea.readByte() > 0;
                final String message = isQuickDelivery ? slea.readMapleAsciiString() : null;

                final int cost = mesos + GameConstants.getTaxAmount(mesos) + (isQuickDelivery ? 0 : 5000);
                final int recipientID = MapleCharacterUtil.getIdByName(recipient);

                // 檢查是否擁有快遞使用券
                if (isQuickDelivery && !c.getPlayer().haveItem(quickDeliveryTicket, 1, false, true)) {
                    c.getSession().write(MaplePacketCreator.sendDuey(INVALID_REQUEST, null));
                    return;
                }

                // 檢查楓幣是否足夠
                if (mesos < 0 || mesos > 100000000 || c.getPlayer().getMeso() < cost) {
                    c.getSession().write(MaplePacketCreator.sendDuey(MESO_NOT_ENOUGH, null));
                    return;
                }

                // 檢查收件人是否存在
                if (recipientID == -1) {
                    c.getSession().write(MaplePacketCreator.sendDuey(INVALID_CHARACTER_NAME, null));
                    return;
                }

                // 檢查收件人是否為同一帳號
                if (recipientID == c.getAccID()) {
                    c.getSession().write(MaplePacketCreator.sendDuey(SAME_ACCOUNT, null));
                    return;
                }

                if (inventId == 0) { // 僅運送楓幣
                    if (!addMesoToDB(mesos, c.getPlayer().getName(), recipientID, message)) { // 運送失敗
                        c.getSession().write(MaplePacketCreator.sendDuey(TARGET_CANT_RECEIVE, null));
                    } else {
                        consumeQuickDeliveryTicket(c);
                        c.getPlayer().gainMeso(-cost, false);
                        c.getSession().write(MaplePacketCreator.sendDuey(SUCCESSFUL_DELIVER, null));
                    }

                    return;
                }

                // 運送物品
                final MapleInventoryType inv = MapleInventoryType.getByType(inventId);
                final IItem item = c.getPlayer().getInventory(inv).getItem((byte) itemPos);

                if (item == null) { // 物品不存在
                    c.getSession().write(MaplePacketCreator.sendDuey(INVALID_REQUEST, null));
                    return;
                }

                final byte flag = item.getFlag();

                // 檢查是否為不可交易或上鎖的物品
                if (ItemFlag.UNTRADEABLE.check(flag) || ItemFlag.LOCK.check(flag)) {
                    c.getSession().write(MaplePacketCreator.enableActions());
                    return;
                }

                // 檢查物品數量是否足夠
                if (c.getPlayer().getItemQuantity(item.getItemId(), false) < amount) {
                    c.getSession().write(MaplePacketCreator.sendDuey(INVALID_REQUEST, null));
                    return;
                }

                final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();

                // 檢查是否為不可丟棄或僅限帳號分享的物品
                if (ii.isDropRestricted(item.getItemId()) || ii.isAccountShared(item.getItemId())) {
                    c.getSession().write(MaplePacketCreator.sendDuey(INVALID_REQUEST, null));
                    return;
                }

                if (!addItemToDB(item, amount, mesos, c.getPlayer().getName(), recipientID, message)) { // 運送失敗
                    c.getSession().write(MaplePacketCreator.sendDuey(TARGET_CANT_RECEIVE, null));
                } else {
                    final short quantity = GameConstants.isRechargable(item.getItemId()) ? item.getQuantity() : amount;

                    MapleInventoryManipulator.removeFromSlot(c, inv, (byte) itemPos, quantity, true);
                    consumeQuickDeliveryTicket(c);
                    c.getPlayer().gainMeso(-cost, false);
                    c.getSession().write(MaplePacketCreator.sendDuey(SUCCESSFUL_DELIVER, null));
                }

                break;
            case 5: { // 收取包裹
                if (c.getPlayer().getConversation() != 2) {
                    return;
                }

                final int id = slea.readInt();
                final MapleDueyActions pack = retrievePackage(id, c.getPlayer().getId());

                // 檢查包裹是否存在
                if (pack == null) {
                    return;
                }

                // 如有物品，檢查是否有空間收取
                if (pack.getItem() != null && !MapleInventoryManipulator.checkSpace(c, pack.getItem().getItemId(), pack.getItem().getQuantity(), pack.getItem().getOwner())) {
                    c.getSession().write(MaplePacketCreator.sendDuey(BOX_ALREADY_FULL, null));
                    return;
                }

                // 楓幣數量錯誤或加總後超過整數上限
                if (pack.getMesos() < 0 || (pack.getMesos() + c.getPlayer().getMeso()) < 0) {
                    c.getSession().write(MaplePacketCreator.sendDuey(TARGET_CANT_RECEIVE, null));
                    return;
                }

                removePackage(id, c.getPlayer().getId());

                if (pack.getItem() != null) {
                    MapleInventoryManipulator.addFromDrop(c, pack.getItem(), false);
                }

                if (pack.getMesos() > 0) {
                    c.getPlayer().gainMeso(pack.getMesos(), false);
                }

                c.getSession().write(MaplePacketCreator.removeItemFromDuey(false, id));

                break;
            }
            case 6: // 刪除包裹
                if (c.getPlayer().getConversation() != 2) {
                    return;
                }

                final int id = slea.readInt();

                removePackage(id, c.getPlayer().getId());

                c.getSession().write(MaplePacketCreator.removeItemFromDuey(true, id));

                break;
            case 8: // 關閉對話
                c.getPlayer().setConversation(0);

                break;
            default:
                System.err.println("Unhandled Duey operation : " + slea.toString());

                break;
        }
    }

    /**
     * 儲存宅配楓幣資訊到資料庫
     */
    private static boolean addMesoToDB(final int mesos, final String senderName, final int recipientID, final String message)
    {
        try {
            final Connection con = DatabaseConnection.getConnection();
            final PreparedStatement ps = con.prepareStatement("INSERT INTO `duey_packages` (`receiver_id`, `sender_name`, `mesos`, `message`, `expired_at`) VALUES (?, ?, ?, ?, ?)");

            ps.setInt(1, recipientID);
            ps.setString(2, senderName);
            ps.setInt(3, mesos);
            ps.setString(4, message);
            ps.setString(5, DateTimeUtil.dueyExpiredAt(isQuickDelivery));

            ps.executeUpdate();
            ps.close();

            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 儲存宅配物品資訊到資料庫
     */
    private static boolean addItemToDB(final IItem item, final int quantity, final int mesos, final String senderName, final int recipientID, final String message)
    {
        try {
            final Connection con = DatabaseConnection.getConnection();
            final PreparedStatement ps = con.prepareStatement("INSERT INTO `duey_packages` (`receiver_id`, `sender_name`, `mesos`, `message`, `type`, `expired_at`) VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);

            ps.setInt(1, recipientID);
            ps.setString(2, senderName);
            ps.setInt(3, mesos);
            ps.setString(4, message);
            ps.setInt(5, item.getType());
            ps.setString(6, DateTimeUtil.dueyExpiredAt(isQuickDelivery));

            ps.executeUpdate();

            final ResultSet rs = ps.getGeneratedKeys();

            if (rs.next()) {
                saveItem(item, quantity, rs.getInt(1));
            }

            rs.close();
            ps.close();

            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 消耗快遞使用券
     */
    private static void consumeQuickDeliveryTicket(MapleClient c)
    {
        if (!isQuickDelivery) {
            return;
        }

        MapleInventoryManipulator.removeById(
            c,
            MapleInventoryType.CASH,
            quickDeliveryTicket,
            1,
            true,
            false
        );
    }

    /**
     * 取得該角色的所有包裹
     */
    private static List<MapleDueyActions> retrievePackages(final MapleCharacter chr)
    {
        try {
            final List<MapleDueyActions> packages = new LinkedList<>();
            final Connection con = DatabaseConnection.getConnection();
            final PreparedStatement ps = con.prepareStatement("SELECT * FROM `duey_packages` WHERE `receiver_id` = ? AND `received_at` IS NULL");

            ps.setInt(1, chr.getId());

            final ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                final int id = rs.getInt("id");

                if (DateTimeUtil.isAfter(rs.getString("expired_at"))) {
                    removePackage(id, chr.getId());
                } else {
                    final MapleDueyActions pack = getItemByPackageId(id);

                    pack.setSender(rs.getString("sender_name"));
                    pack.setMesos(rs.getInt("mesos"));
                    pack.setMessage(rs.getString("message"));
                    pack.setSentAt(DateTimeUtil.toTimestamp(rs.getString("expired_at")));

                    packages.add(pack);
                }
            }

            rs.close();
            ps.close();

            return packages;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 取回包裹
     */
    private static MapleDueyActions retrievePackage(final int id, final int charId)
    {
        try {
            final Connection con = DatabaseConnection.getConnection();
            final PreparedStatement ps = con.prepareStatement("SELECT * FROM `duey_packages` WHERE `id` = ? AND `receiver_id` = ? AND `received_at` IS NULL");

            ps.setInt(1, id);
            ps.setInt(2, charId);

            final ResultSet rs = ps.executeQuery();

            MapleDueyActions pack = null;

            if (rs.next()) {
                if (DateTimeUtil.isAfter(rs.getString("expired_at"))) {
                    removePackage(id, charId);
                } else {
                    pack = getItemByPackageId(id);

                    pack.setSender(rs.getString("sender_name"));
                    pack.setMesos(rs.getInt("mesos"));
                    pack.setMessage(rs.getString("message"));
                    pack.setSentAt(DateTimeUtil.toTimestamp(rs.getString("expired_at")));
                }
            }

            rs.close();
            ps.close();

            return pack;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static MapleDueyActions getItemByPackageId(final int id)
    {
        try {
            return new MapleDueyActions(id, loadItem(id));
        } catch (Exception e) {
            e.printStackTrace();
            return new MapleDueyActions(id);
        }
    }

    /**
     * 移除包裹
     */
    private static void removePackage(final int id, final int charId)
    {
        try {
            final Connection con = DatabaseConnection.getConnection();
            final PreparedStatement ps = con.prepareStatement("UPDATE `duey_packages` SET `received_at` = ? WHERE `id` = ? AND `receiver_id` = ?");

            ps.setString(1, DateTimeUtil.now());
            ps.setInt(2, id);
            ps.setInt(3, charId);

            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * @todo fix this
     */
    private static IItem loadItem(int id) throws SQLException
    {
        final Connection con = DatabaseConnection.getConnection();
        final PreparedStatement ps = con.prepareStatement("SELECT * FROM `dueyitems` LEFT JOIN `dueyequipment` USING(`inventoryitemid`) WHERE `type` = 6 AND `packageid` = ?");

        ps.setInt(1, id);

        final ResultSet rs = ps.executeQuery();

        IItem item = null;

        if (rs.next()) {
            MapleInventoryType mit = MapleInventoryType.getByType(rs.getByte("inventorytype"));

            if (mit.equals(MapleInventoryType.EQUIP)) {
                Equip equip = new Equip(rs.getInt("itemid"), rs.getShort("position"), rs.getInt("uniqueid"), rs.getByte("flag"));

                equip.setQuantity((short) 1);
                equip.setOwner(rs.getString("owner"));
                equip.setExpiration(rs.getLong("expiredate"));
                equip.setUpgradeSlots(rs.getByte("upgradeslots"));
                equip.setLevel(rs.getByte("level"));
                equip.setStr(rs.getShort("str"));
                equip.setDex(rs.getShort("dex"));
                equip.setInt(rs.getShort("int"));
                equip.setLuk(rs.getShort("luk"));
                equip.setHp(rs.getShort("hp"));
                equip.setMp(rs.getShort("mp"));
                equip.setWatk(rs.getShort("watk"));
                equip.setMatk(rs.getShort("matk"));
                equip.setWdef(rs.getShort("wdef"));
                equip.setMdef(rs.getShort("mdef"));
                equip.setAcc(rs.getShort("acc"));
                equip.setAvoid(rs.getShort("avoid"));
                equip.setHands(rs.getShort("hands"));
                equip.setSpeed(rs.getShort("speed"));
                equip.setJump(rs.getShort("jump"));
                equip.setViciousHammer(rs.getByte("ViciousHammer"));
                equip.setItemEXP(rs.getInt("itemEXP"));
                equip.setGMLog(rs.getString("GM_Log"));
                equip.setDurability(rs.getInt("durability"));
                equip.setEnhance(rs.getByte("enhance"));
                equip.setPotential1(rs.getShort("potential1"));
                equip.setPotential2(rs.getShort("potential2"));
                equip.setPotential3(rs.getShort("potential3"));
                equip.setHpR(rs.getShort("hpR"));
                equip.setMpR(rs.getShort("mpR"));
                equip.setGiftFrom(rs.getString("sender"));

                if (equip.getUniqueId() > -1) {
                    if (GameConstants.isEffectRing(rs.getInt("itemid"))) {
                        MapleRing ring = MapleRing.loadFromDb(equip.getUniqueId(), mit.equals(MapleInventoryType.EQUIPPED));
                        if (ring != null) {
                            equip.setRing(ring);
                        }
                    }
                }

                item = equip.copy();
            } else {
                Item _item = new Item(rs.getInt("itemid"), rs.getShort("position"), rs.getShort("quantity"), rs.getByte("flag"));
                _item.setUniqueId(rs.getInt("uniqueid"));
                _item.setOwner(rs.getString("owner"));
                _item.setExpiration(rs.getLong("expiredate"));
                _item.setGMLog(rs.getString("GM_Log"));
                _item.setGiftFrom(rs.getString("sender"));
                if (GameConstants.isPet(_item.getItemId())) {
                    if (_item.getUniqueId() > -1) {
                        MaplePet pet = MaplePet.loadFromDb(_item.getItemId(), _item.getUniqueId(), _item.getPosition());
                        if (pet != null) {
                            _item.setPet(pet);
                        }
                    } else {
                        //O_O hackish fix
                        final int new_unique = MapleInventoryIdentifier.getInstance();
                        _item.setUniqueId(new_unique);
                        _item.setPet(MaplePet.createPet(_item.getItemId(), new_unique));
                    }
                }

                item = _item;
            }
        }

        rs.close();
        ps.close();

        return item;
    }

    /**
     * @todo fix this
     */
    private static void saveItem(final IItem item, final int quantity, final int packageId) throws SQLException
    {
        final Connection con = DatabaseConnection.getConnection();
        final MapleInventoryType mit = GameConstants.getInventoryType(item.getItemId());

        PreparedStatement ps = con.prepareStatement("INSERT INTO `dueyitems` (`packageid`, `itemid`, `inventorytype`, `position`, `quantity`, `owner`, `GM_Log`, `uniqueid`, `expiredate`, `flag`, `type`, `sender`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);

        ps.setInt(1, packageId);
        ps.setInt(2, item.getItemId());
        ps.setInt(3, mit.getType());
        ps.setInt(4, item.getPosition());
        ps.setInt(5, quantity);
        ps.setString(6, item.getOwner());
        ps.setString(7, item.getGMLog());
        ps.setInt(8, item.getUniqueId());
        ps.setLong(9, item.getExpiration());
        ps.setByte(10, item.getFlag());
        ps.setByte(11, (byte) 6);
        ps.setString(12, item.getGiftFrom());

        ps.executeUpdate();

        if (mit.equals(MapleInventoryType.EQUIP) || mit.equals(MapleInventoryType.EQUIPPED)) {
            ResultSet rs = ps.getGeneratedKeys();

            if (!rs.next()) {
                System.err.println("Inserting item failed.");

                rs.close();
                ps.close();

                return;
            }

            final int dueyItemsId = rs.getInt(1);
            final IEquip equip = (IEquip) item;

            rs.close();
            ps.close();

            ps = con.prepareStatement("INSERT INTO `dueyequipment` VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

            ps.setInt(1, dueyItemsId);
            ps.setInt(2, equip.getUpgradeSlots());
            ps.setInt(3, equip.getLevel());
            ps.setInt(4, equip.getStr());
            ps.setInt(5, equip.getDex());
            ps.setInt(6, equip.getInt());
            ps.setInt(7, equip.getLuk());
            ps.setInt(8, equip.getHp());
            ps.setInt(9, equip.getMp());
            ps.setInt(10, equip.getWatk());
            ps.setInt(11, equip.getMatk());
            ps.setInt(12, equip.getWdef());
            ps.setInt(13, equip.getMdef());
            ps.setInt(14, equip.getAcc());
            ps.setInt(15, equip.getAvoid());
            ps.setInt(16, equip.getHands());
            ps.setInt(17, equip.getSpeed());
            ps.setInt(18, equip.getJump());
            ps.setInt(19, equip.getViciousHammer());
            ps.setInt(20, equip.getItemEXP());
            ps.setInt(21, equip.getDurability());
            ps.setByte(22, equip.getEnhance());
            ps.setInt(23, equip.getPotential1());
            ps.setInt(24, equip.getPotential2());
            ps.setInt(25, equip.getPotential3());
            ps.setInt(26, equip.getHpR());
            ps.setInt(27, equip.getMpR());
            ps.executeUpdate();
        }

        ps.close();
    }
}
