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
import tools.MaplePacketCreator;
import tools.data.input.SeekableLittleEndianAccessor;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DueyHandler
{
    /*
     * 12：楓幣不足！
     * 13：此為不正確的要求！
     * 14：請重新確認收件人的名稱！
     * 15：無法送件給同一帳號內的角色！
     * 16：收件人的宅配保管箱已滿！
     * 17：該角色無法收取宅配！
     * 18：對方宅配保管箱內，有數量限制的道具！
     */
    public static void operate(final SeekableLittleEndianAccessor slea, final MapleClient c)
    {
        final byte operation = slea.readByte();

        switch (operation) {
            case 1: // 開啟宅配人員對話
                // @todo fix
                final String password = slea.readMapleAsciiString();

                c.getSession().write(MaplePacketCreator.sendDuey((byte) 10, retrievePackages(c.getPlayer())));

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
                boolean quickDelivery = slea.readByte() > 0;

                final int finalCost = mesos + GameConstants.getTaxAmount(mesos) + (quickDelivery ? 0 : 5000);

                if (mesos < 0 || mesos > 100000000 || c.getPlayer().getMeso() < finalCost) { // 楓幣不足
                    c.getSession().write(MaplePacketCreator.sendDuey((byte) 12, null));
                } else {
                    final int accId = MapleCharacterUtil.getIdByName(recipient);

                    if (accId == -1) { // 收件人不存在
                        c.getSession().write(MaplePacketCreator.sendDuey((byte) 14, null));
                    } else if (accId == c.getAccID()) { // 收件人為同一帳號內的角色
                        c.getSession().write(MaplePacketCreator.sendDuey((byte) 15, null));
                    } else {
                        if (inventId == 0) { // 運送楓幣
                            if (!addMesoToDB(mesos, c.getPlayer().getName(), accId)) { // 運送失敗
                                c.getSession().write(MaplePacketCreator.sendDuey((byte) 17, null));
                            } else {
                                c.getPlayer().gainMeso(-finalCost, false);
                                c.getSession().write(MaplePacketCreator.sendDuey((byte) 19, null));
                            }
                        } else { // 運送物品
                            final MapleInventoryType inv = MapleInventoryType.getByType(inventId);
                            final IItem item = c.getPlayer().getInventory(inv).getItem((byte) itemPos);

                            if (item == null) { // 物品不存在
                                c.getSession().write(MaplePacketCreator.sendDuey((byte) 13, null));
                                return;
                            }

                            final byte flag = item.getFlag();

                            if (ItemFlag.UNTRADEABLE.check(flag) || ItemFlag.LOCK.check(flag)) {
                                c.getSession().write(MaplePacketCreator.enableActions());
                                return;
                            }

                            if (c.getPlayer().getItemQuantity(item.getItemId(), false) < amount) { // 物品數量不正確
                                c.getSession().write(MaplePacketCreator.sendDuey((byte) 13, null));
                            } else {
                                final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();

                                if (ii.isDropRestricted(item.getItemId()) || ii.isAccountShared(item.getItemId())) { // 不可運送的物品
                                    c.getSession().write(MaplePacketCreator.sendDuey((byte) 13, null));
                                } else {
                                    if (!addItemToDB(item, amount, mesos, c.getPlayer().getName(), accId)) { // 運送失敗
                                        c.getSession().write(MaplePacketCreator.sendDuey((byte) 17, null));
                                    } else {
                                        if (GameConstants.isThrowingStar(item.getItemId()) || GameConstants.isBullet(item.getItemId())) {
                                            MapleInventoryManipulator.removeFromSlot(c, inv, (byte) itemPos, item.getQuantity(), true);
                                        } else {
                                            MapleInventoryManipulator.removeFromSlot(c, inv, (byte) itemPos, amount, true, false);
                                        }

                                        c.getPlayer().gainMeso(-finalCost, false);
                                        c.getSession().write(MaplePacketCreator.sendDuey((byte) 19, null));
                                    }
                                }
                            }
                        }
                    }
                }

                break;
            case 5: { // 收取包裹
                if (c.getPlayer().getConversation() != 2) {
                    return;
                }

                final int id = slea.readInt();
                final MapleDueyActions dp = retrievePackage(id, c.getPlayer().getId());

                if (dp == null) {
                    return;
                } else if (dp.getItem() != null && !MapleInventoryManipulator.checkSpace(c, dp.getItem().getItemId(), dp.getItem().getQuantity(), dp.getItem().getOwner())) {
                    c.getSession().write(MaplePacketCreator.sendDuey((byte) 16, null)); // Not enough Space
                    return;
                } else if (dp.getMesos() < 0 || (dp.getMesos() + c.getPlayer().getMeso()) < 0) {
                    c.getSession().write(MaplePacketCreator.sendDuey((byte) 17, null)); // Unsuccessfull
                    return;
                }

                removePackage(id, c.getPlayer().getId());

                if (dp.getItem() != null) {
                    MapleInventoryManipulator.addFromDrop(c, dp.getItem(), false);
                }

                if (dp.getMesos() != 0) {
                    c.getPlayer().gainMeso(dp.getMesos(), false);
                }

                c.getSession().write(MaplePacketCreator.removeItemFromDuey(false, id));

                break;
            }
            case 6: // Remove package
                if (c.getPlayer().getConversation() != 2) {
                    return;
                }

                final int id = slea.readInt();

                removePackage(id, c.getPlayer().getId());

                c.getSession().write(MaplePacketCreator.removeItemFromDuey(true, id));

                break;
            case 8: // Close Duey
                c.getPlayer().setConversation(0);

                break;
            default:
                System.out.println("Unhandled Duey operation : " + slea.toString());

                break;
        }
    }

    /**
     * 儲存宅配楓幣資訊到資料庫
     */
    private static boolean addMesoToDB(final int mesos, final String senderName, final int recipientID)
    {
        try {
            final Connection con = DatabaseConnection.getConnection();
            final PreparedStatement ps = con.prepareStatement("INSERT INTO `duey_packages` (`receiver_id`, `sender_name`, `mesos`, `expired_at`) VALUES (?, ?, ?, ?)");

            ps.setInt(1, recipientID);
            ps.setString(2, senderName);
            ps.setInt(3, mesos);
            ps.setString(4, expiredAtDateTimeString());

            ps.executeUpdate();
            ps.close();

            return true;
        } catch (SQLException se) {
            se.printStackTrace();
            return false;
        }
    }

    /**
     * 儲存宅配物品資訊到資料庫
     */
    private static boolean addItemToDB(final IItem item, final int quantity, final int mesos, final String senderName, final int recipientID)
    {
        try {
            final Connection con = DatabaseConnection.getConnection();
            final PreparedStatement ps = con.prepareStatement("INSERT INTO `duey_packages` (`receiver_id`, `sender_name`, `mesos`, `type`, `expired_at`) VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);

            ps.setInt(1, recipientID);
            ps.setString(2, senderName);
            ps.setInt(3, mesos);
            ps.setInt(4, item.getType());
            ps.setString(5, expiredAtDateTimeString());

            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();

            if (rs.next()) {
                saveItem(item, quantity, rs.getInt(1));
            }

            rs.close();
            ps.close();

            return true;
        } catch (SQLException se) {
            se.printStackTrace();
            return false;
        }
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

                if (currentDateTime().isAfter(LocalDateTime.parse(rs.getString("expired_at"), dateTimeFormat()))) {
                    removePackage(id, chr.getId());
                } else {
                    final MapleDueyActions pack = getItemByPackageId(id);

                    pack.setSender(rs.getString("sender_name"));
                    pack.setMesos(rs.getInt("mesos"));
                    pack.setSentAt(dateTimeToTimestamp(rs.getString("expired_at")));

                    packages.add(pack);
                }
            }

            rs.close();
            ps.close();

            return packages;
        } catch (SQLException ex) {
            ex.printStackTrace();
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
                if (currentDateTime().isAfter(LocalDateTime.parse(rs.getString("expired_at"), dateTimeFormat()))) {
                    removePackage(id, charId);
                } else {
                    pack = getItemByPackageId(id);

                    pack.setSender(rs.getString("sender_name"));
                    pack.setMesos(rs.getInt("mesos"));
                    pack.setSentAt(dateTimeToTimestamp(rs.getString("expired_at")));
                }
            }

            rs.close();
            ps.close();

            return pack;
        } catch (SQLException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private static MapleDueyActions getItemByPackageId(final int id)
    {
        try {
            IItem item = loadItem(id);

            return new MapleDueyActions(id, item);
        } catch (Exception se) {
            se.printStackTrace();
        }

        return new MapleDueyActions(id);
    }

    /**
     * 移除包裹
     */
    private static void removePackage(final int id, final int charId)
    {
        try {
            final Connection con = DatabaseConnection.getConnection();
            final PreparedStatement ps = con.prepareStatement("UPDATE `duey_packages` SET `received_at` = ? WHERE `id` = ? AND `receiver_id` = ?");

            ps.setString(1, currentDateTimeString());
            ps.setInt(2, id);
            ps.setInt(3, charId);

            ps.executeUpdate();
            ps.close();
        } catch (SQLException se) {
            se.printStackTrace();
        }
    }

    public static void reciveMsg(final int recipientId)
    {
        try {
            final Connection con = DatabaseConnection.getConnection();
            final PreparedStatement ps = con.prepareStatement("UPDATE `duey_packages` SET `checked` = 0 where `received_at` = ?");

            ps.setInt(1, recipientId);

            ps.executeUpdate();
            ps.close();
        } catch (SQLException se) {
            se.printStackTrace();
        }
    }

    private static IItem loadItem(int id) throws SQLException
    {
        final Connection con = DatabaseConnection.getConnection();
        final PreparedStatement ps = con.prepareStatement("SELECT * FROM `dueyitems` LEFT JOIN `dueyequipment` USING(`inventoryitemid`) WHERE `type` = 6 AND `packageid` = ?");

        ps.setInt(1, id);

        final ResultSet rs = ps.executeQuery();

        IItem item = null;

        if (rs.next()) {
            MapleInventoryType mit = MapleInventoryType.getByType(rs.getByte("inventorytype"));

            if (mit.equals(MapleInventoryType.EQUIP) || mit.equals(MapleInventoryType.EQUIPPED)) {
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

    private static LocalDateTime currentDateTime()
    {
        return LocalDateTime.now();
    }

    private static String currentDateTimeString()
    {
        return LocalDateTime.now().format(dateTimeFormat());
    }

    private static String expiredAtDateTimeString()
    {
        return LocalDateTime.now().plusDays(15).format(dateTimeFormat());
    }

    private static long dateTimeToTimestamp(final String datetime)
    {
        return java.sql.Timestamp.valueOf(datetime).getTime();
    }

    private static DateTimeFormatter dateTimeFormat()
    {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    }
}
