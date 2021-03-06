/*
 * TMS 113 handling.cashshop.handler/CashShopOperation.java
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
package handling.cashshop.handler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.HashMap;

import constants.GameConstants;
import client.MapleClient;
import client.MapleCharacter;
import client.MapleCharacterUtil;
import client.inventory.MapleInventoryType;
import client.inventory.MapleRing;
import client.inventory.MapleInventoryIdentifier;
import client.inventory.IItem;
import database.DatabaseConnection;
import handling.cashshop.CashShopServer;
import handling.channel.ChannelServer;
import handling.world.CharacterTransfer;
import handling.world.World;
import java.util.List;
import server.CashItemFactory;
import server.CashItemInfo;
import server.MTSStorage;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import server.RandomRewards;
import tools.MaplePacketCreator;
import tools.packet.MTSCSPacket;
import tools.Pair;
import tools.data.input.SeekableLittleEndianAccessor;

public class CashShopOperation
{
    public static void enterCashShop(final int playerId, final MapleClient c)
    {
        boolean isMTS = false;

        CharacterTransfer transfer = CashShopServer.getPlayerStorage().getPendingCharacter(playerId);

        if (transfer == null) {
            transfer = CashShopServer.getPlayerStorageMTS().getPendingCharacter(playerId);

            if (transfer == null) {
                c.getSession().close();
                return;
            }

            isMTS = true;
        }

        MapleCharacter chr = MapleCharacter.reconstructChr(transfer, c, false);

        c.setPlayer(chr);
        c.setAccID(chr.getAccountID());

        if (!c.checkIPAddress()) { // Remote hack
            c.getSession().close();
            return;
        }

        final int state = c.getLoginState();

        if (state == MapleClient.LOGIN_SERVER_TRANSITION || state == MapleClient.CHANGE_CHANNEL) {
            if (World.isCharacterListConnected(c.loadCharacterNames(c.getWorld()))) {
                c.setPlayer(null);
                c.getSession().close();
                return;
            }
        }

        c.updateLoginState(MapleClient.LOGIN_LOGGEDIN, c.getSessionIPAddress());

        if (isMTS) {
            CashShopServer.getPlayerStorageMTS().registerPlayer(chr);

            c.getSession().write(MTSCSPacket.startMTS(chr, c));

            MTSOperation.MTSUpdate(MTSStorage.getInstance().getCart(c.getPlayer().getId()), c);
        } else {
            CashShopServer.getPlayerStorage().registerPlayer(chr);

            c.getSession().write(MTSCSPacket.warpCS(c));

            cashShopUpdate(c);
        }
    }

    public static void leaveCashShop(final SeekableLittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr)
    {
        CashShopServer.getPlayerStorageMTS().deregisterPlayer(chr);
        CashShopServer.getPlayerStorage().deregisterPlayer(chr);
        c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION, c.getSessionIPAddress());

        try {

            World.ChannelChange_Data(new CharacterTransfer(chr), chr.getId(), c.getChannel());
            c.getSession().write(MaplePacketCreator.getChannelChange(Integer.parseInt(ChannelServer.getInstance(c.getChannel()).getIP().split(":")[1])));
        } finally {
            chr.saveToDB(false, true);
            c.setPlayer(null);
            c.setReceiving(false);
        }
    }

    public static void cashShopUpdate(final MapleClient c)
    {
        c.getSession().write(MTSCSPacket.getCSGifts(c));
        c.getSession().write(MTSCSPacket.showAcc(c));
        doCSPackets(c);
        c.getSession().write(MTSCSPacket.sendWishList(c.getPlayer(), false));
    }

    public static void couponCode(final String code, final MapleClient c)
    {
        final Connection con = DatabaseConnection.getConnection();

        PreparedStatement ps;

        boolean validCode = false;
        int type = -1;
        int item = -1;

        try {
            ps = con.prepareStatement("SELECT `valid`, `type`, `item` FROM `nxcode` WHERE `code` = ?");

            ps.setString(1, code);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                validCode = rs.getInt("valid") > 0;
                type = rs.getInt("type");
                item = rs.getInt("item");
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (!validCode) {
            c.getSession().write(MTSCSPacket.sendCSFail(0xD4));
        } else {
            if (type != 4) {
                try {
                    ps = con.prepareStatement("UPDATE `nxcode` SET `user` = ?, `valid` = 0 WHERE `code` = ?");

                    ps.setString(1, c.getPlayer().getName());
                    ps.setString(2, code);

                    ps.execute();

                    ps.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }

            /*
             * Explanation of type!
             * Basically, this makes coupon codes do
             * different things!
             *
             * Type 1: A-Cash,
             * Type 2: Maple Points
             * Type 3: Item.. use SN
             * Type 4: A-Cash Coupon that can be used over and over
             * Type 5: Mesos
             */
            Map<Integer, IItem> itemz = new HashMap<>();

            int maplePoints = 0, mesos = 0;

            switch (type) {
                case 1:
                case 2:
                    c.getPlayer().modifyCSPoints(type, item, false);
                    maplePoints = item;
                    break;
                case 3:
                    CashItemInfo itez = CashItemFactory.getInstance().getItem(item);
                    if (itez == null) {
                        c.getSession().write(MTSCSPacket.sendCSFail(0));
                        doCSPackets(c);
                        return;
                    }
                    byte slot = MapleInventoryManipulator.addId(c, itez.getId(), (short) 1, "");
                    if (slot <= -1) {
                        c.getSession().write(MTSCSPacket.sendCSFail(0));
                        doCSPackets(c);
                        return;
                    } else {
                        itemz.put(item, c.getPlayer().getInventory(GameConstants.getInventoryType(item)).getItem(slot));
                    }
                    break;
                case 4:
                    c.getPlayer().modifyCSPoints(1, item, false);
                    maplePoints = item;
                    break;
                case 5:
                    c.getPlayer().gainMeso(item, false);
                    mesos = item;
                    break;
            }

            c.getSession().write(MTSCSPacket.showCouponRedeemedItem(itemz, mesos, maplePoints, c));
        }

        doCSPackets(c);
    }

    public static void buyCashItem(final SeekableLittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr)
    {
        final int action = slea.readByte();

        if (action == 0) {
            slea.skip(2);
            couponCode(slea.readMapleAsciiString(), c);
        } else if (action == 3) {
            final byte type = slea.readByte() ==0 ?(byte)1 : (byte)2;
            //slea.skip(1);
            final CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());

            if (item != null && chr.getCSPoints(type) >= item.getPrice()) {
                if (!item.genderEquals(c.getPlayer().getGender())) {
                    c.getSession().write(MTSCSPacket.sendCSFail(0xA6));
                    doCSPackets(c);
                    return;
                } else if (c.getPlayer().getCashInventory().getItemsSize() >= 100) {
                    c.getSession().write(MTSCSPacket.sendCSFail(0xB1));
                    doCSPackets(c);
                    return;
                }
                chr.modifyCSPoints(type, -item.getPrice(), false);
                IItem itemz = chr.getCashInventory().toItem(item);
                if (itemz != null && itemz.getUniqueId() > 0 && itemz.getItemId() == item.getId() && itemz.getQuantity() == item.getCount()) {
                    chr.getCashInventory().addToInventory(itemz);
                    //c.getSession().write(MTSCSPacket.confirmToCSInventory(itemz, c.getAccID(), item.getSN()));
                    c.getSession().write(MTSCSPacket.showBoughtCSItem(itemz, item.getSN(), c.getAccID()));
                } else {
                    c.getSession().write(MTSCSPacket.sendCSFail(0));
                }
            } else {
                c.getSession().write(MTSCSPacket.sendCSFail(0));
            }
        } else if (action == 4 || action == 38) { //gift, package
            slea.readMapleAsciiString(); // as13
            final CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
            String partnerName = slea.readMapleAsciiString();
            String msg = slea.readMapleAsciiString();
            if (item == null || c.getPlayer().getCSPoints(1) < item.getPrice() || msg.length() > 73 || msg.length() < 1) { //dont want packet editors gifting random stuff =P
                c.getSession().write(MTSCSPacket.sendCSFail(0));
                doCSPackets(c);
                return;
            }
            Pair<Integer, Pair<Integer, Integer>> info = MapleCharacterUtil.getInfoByName(partnerName, c.getPlayer().getWorld());
            if (info == null || info.getLeft().intValue() <= 0 || info.getLeft().intValue() == c.getPlayer().getId() || info.getRight().getLeft().intValue() == c.getAccID()) {
                c.getSession().write(MTSCSPacket.sendCSFail(0xA2)); //9E v75
                doCSPackets(c);
                return;
            } else if (!item.genderEquals(info.getRight().getRight().intValue())) {
                c.getSession().write(MTSCSPacket.sendCSFail(0xB0));
                doCSPackets(c);
                return;
            } else {
                c.getPlayer().getCashInventory().gift(info.getLeft().intValue(), c.getPlayer().getName(), msg, item.getSN(), MapleInventoryIdentifier.getInstance());
                c.getPlayer().modifyCSPoints(1, -item.getPrice(), false);
                c.getSession().write(MTSCSPacket.sendGift(item.getPrice(), item.getId(), item.getCount(), partnerName ,action==4?false:true));
            }
        } else if (action == 5) { // Wishlist
            chr.clearWishlist();
            if (slea.available() < 40) {
                c.getSession().write(MTSCSPacket.sendCSFail(0));
                doCSPackets(c);
                return;
            }
            int[] wishlist = new int[10];
            for (int i = 0; i < 10; i++) {
                wishlist[i] = slea.readInt();
            }
            chr.setWishlist(wishlist);
            c.getSession().write(MTSCSPacket.sendWishList(chr, true));

        } else if (action == 6) { // Increase inv
            final byte useCash = slea.readByte() ==0 ?(byte)1 : (byte)2;
            final boolean coupon = slea.readByte() > 0;
            if (coupon) {
                final MapleInventoryType type = getInventoryType(slea.readInt());

                if (chr.getCSPoints(useCash) >= 100 && chr.getInventory(type).getSlotLimit() < 89) {
                    chr.modifyCSPoints(useCash, -100, false);
                    chr.getInventory(type).addSlot((byte) 8);
                    chr.dropMessage(1, "Slots has been increased to " + chr.getInventory(type).getSlotLimit());
                } else {
                    c.getSession().write(MTSCSPacket.sendCSFail(0xA4));
                }
            } else {
		byte inv = slea.readByte();
                final MapleInventoryType type = MapleInventoryType.getByType(inv);

                if (chr.getCSPoints(useCash) >= 100 && chr.getInventory(type).getSlotLimit() < 93) {
                    chr.modifyCSPoints(useCash, -100, false);
                    chr.getInventory(type).addSlot((byte) 4);
                    c.getSession().write(MTSCSPacket.increasedInvSlots(inv, chr.getInventory(type).getSlotLimit()));
                } else {
                    c.getSession().write(MTSCSPacket.sendCSFail(0xA4));
                }
            }

        } else if (action == 7) { // Increase slot space
            final byte useCash = slea.readByte() ==0 ?(byte)1 : (byte)2;
            if (chr.getCSPoints(useCash) >= 100 && chr.getStorage().getSlots() < 45) {
                chr.modifyCSPoints(useCash, -100, false);
                chr.getStorage().increaseSlots((byte) 4);
                chr.getStorage().saveToDB();
                //c.getSession().write(MTSCSPacket.increasedStorageSlots(chr.getStorage().getSlots()));
                chr.dropMessage(1, "倉庫欄位增加至 " +chr.getStorage().getSlots());
            } else {
                c.getSession().write(MTSCSPacket.sendCSFail(0xA4));
            }
        } else if (action == 8) { //...9 = pendant slot expansion
            slea.readByte();
            CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
            int slots = c.getCharacterSlots();
            if (item == null || c.getPlayer().getCSPoints(1) < item.getPrice() || slots > 15) {
                c.getSession().write(MTSCSPacket.sendCSFail(0));
                doCSPackets(c);
                return;
            }
            c.getPlayer().modifyCSPoints(1, -item.getPrice(), false);
            if (c.gainCharacterSlot()) {
                c.getSession().write(MTSCSPacket.increasedStorageSlots(slots + 1));
            } else {
                c.getSession().write(MTSCSPacket.sendCSFail(0));
            }
        } else if (action == 13) { //get item from csinventory
            //uniqueid, 00 01 01 00, type->position(short)
            IItem item = c.getPlayer().getCashInventory().findByCashId((int) slea.readLong());
            if (item != null && item.getQuantity() > 0 && MapleInventoryManipulator.checkSpace(c, item.getItemId(), item.getQuantity(), item.getOwner())) {
                IItem item_ = item.copy();
                short pos = MapleInventoryManipulator.addbyItem(c, item_, true);
                if (pos >= 0) {
                    if (item_.getPet() != null) {
                        item_.getPet().setInventoryPosition(pos);
                        c.getPlayer().addPet(item_.getPet());
                    }
                    c.getPlayer().getCashInventory().removeFromInventory(item);
                    c.getSession().write(MTSCSPacket.confirmFromCSInventory(item_, pos));
                } else {
                    c.getSession().write(MTSCSPacket.sendCSFail(0xB1));
                }
            } else {
                c.getSession().write(MTSCSPacket.sendCSFail(0xB1));
            }
        } else if (action == 14) { //put item in cash inventory
            int uniqueid = (int) slea.readLong();
            MapleInventoryType type = MapleInventoryType.getByType(slea.readByte());
            IItem item = c.getPlayer().getInventory(type).findByUniqueId(uniqueid);
            if (item != null && item.getQuantity() > 0 && item.getUniqueId() > 0 && c.getPlayer().getCashInventory().getItemsSize() < 100) {
                IItem item_ = item.copy();
                //MapleInventoryManipulator.removeFromSlot(c, type, item.getPosition(), item.getQuantity(), false);
		c.getPlayer().getInventory(type).removeItem(item.getPosition(), item.getQuantity(), false);
                if (item_.getPet() != null) {
                    c.getPlayer().removePetCS(item_.getPet());
                }
                item_.setPosition((byte) 0);
                c.getPlayer().getCashInventory().addToInventory(item_);
                //warning: this d/cs
                c.getSession().write(MTSCSPacket.confirmToCSInventory(item, c.getAccID(), 0 /* c.getPlayer().getCashInventory().getSNForItem(item)*/));
            } else {
                c.getSession().write(MTSCSPacket.sendCSFail(0xB1));
            }
        } else if (action == 26){ // 26 = sell cash item.
            slea.readMapleAsciiString(); // as13
            IItem item = c.getPlayer().getCashInventory().findByCashId((int) slea.readLong());
            if (item != null && item.getQuantity() > 0 && MapleInventoryManipulator.checkSpace(c, item.getItemId(), item.getQuantity(), item.getOwner())) {
                chr.modifyCSPoints(2, 5, false);
                c.getPlayer().getCashInventory().removeFromInventory(item);
                c.getPlayer().dropMessage(1, "獲得了 5 點楓葉點數");
                
            }
            doCSPackets(c);
            return ;
        } else if (action == 29 || action == 35) { //35 = friendship, 29 = crush
            //c.getSession().write(MTSCSPacket.sendCSFail(0));
            slea.readMapleAsciiString(); // as13
            final CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
            final String partnerName = slea.readMapleAsciiString();
            final String msg = slea.readMapleAsciiString();
            if (item == null || !GameConstants.isEffectRing(item.getId()) || c.getPlayer().getCSPoints(1) < item.getPrice() || msg.length() > 73 || msg.length() < 1) {
                c.getSession().write(MTSCSPacket.sendCSFail(0));
                doCSPackets(c);
                return;
            } else if (!item.genderEquals(c.getPlayer().getGender())) {
                c.getSession().write(MTSCSPacket.sendCSFail(0xA6));
                doCSPackets(c);
                return;
            } else if (c.getPlayer().getCashInventory().getItemsSize() >= 100) {
                c.getSession().write(MTSCSPacket.sendCSFail(0xB1));
                doCSPackets(c);
                return;
            }
            Pair<Integer, Pair<Integer, Integer>> info = MapleCharacterUtil.getInfoByName(partnerName, c.getPlayer().getWorld());
            if (info == null || info.getLeft().intValue() <= 0 || info.getLeft().intValue() == c.getPlayer().getId()) {
                c.getSession().write(MTSCSPacket.sendCSFail(0xB4)); //9E v75
                doCSPackets(c);
                return;
            } else if (info.getRight().getLeft().intValue() == c.getAccID()) {
                c.getSession().write(MTSCSPacket.sendCSFail(0xA3)); //9D v75
                doCSPackets(c);
                return;
            } else {
                if (info.getRight().getRight().intValue() == c.getPlayer().getGender() && action == 30) {
                    c.getSession().write(MTSCSPacket.sendCSFail(0xA1)); //9B v75
                    doCSPackets(c);
                    return;
                }

                int err = MapleRing.createRing(item.getId(), c.getPlayer(), partnerName, msg, info.getLeft().intValue(), item.getSN());

                if (err != 1) {
                    c.getSession().write(MTSCSPacket.sendCSFail(0)); //9E v75
                    doCSPackets(c);
                    return;
                }
                c.getPlayer().modifyCSPoints(1, -item.getPrice(), false);
                //c.getSession().write(MTSCSPacket.showBoughtCSItem(itemz, item.getSN(), c.getAccID()));
                c.getSession().write(MTSCSPacket.sendGift(item.getPrice(), item.getId(), item.getCount(), partnerName,false));
            }

        } else if (action == 30) {
            slea.skip(1);
            final CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
            List<CashItemInfo> ccc = null;
            if (item != null) {
                ccc = CashItemFactory.getInstance().getPackageItems(item.getId());
            }
            if (item == null || ccc == null || c.getPlayer().getCSPoints(1) < item.getPrice()) {
                c.getSession().write(MTSCSPacket.sendCSFail(0));
                doCSPackets(c);
                return;
            } else if (!item.genderEquals(c.getPlayer().getGender())) {
                c.getSession().write(MTSCSPacket.sendCSFail(0xA6));
                doCSPackets(c);
                return;
            } else if (c.getPlayer().getCashInventory().getItemsSize() >= (100 - ccc.size())) {
                c.getSession().write(MTSCSPacket.sendCSFail(0xB1));
                doCSPackets(c);
                return;
            }
            Map<Integer, IItem> ccz = new HashMap<Integer, IItem>();
            for (CashItemInfo i : ccc) {
                IItem itemz = c.getPlayer().getCashInventory().toItem(i);
                if (itemz == null || itemz.getUniqueId() <= 0 || itemz.getItemId() != i.getId()) {
                    continue;
                }
                ccz.put(i.getSN(), itemz);
                c.getPlayer().getCashInventory().addToInventory(itemz);
            }
            chr.modifyCSPoints(1, -item.getPrice(), false);
            c.getSession().write(MTSCSPacket.showBoughtCSPackage(ccz, c.getAccID()));

        } else if (action == 32) {
            final CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
            if (item == null || !MapleItemInformationProvider.getInstance().isQuestItem(item.getId())) {
                c.getSession().write(MTSCSPacket.sendCSFail(0));
                doCSPackets(c);
                return;
            } else if (c.getPlayer().getMeso() < item.getPrice()) {
                c.getSession().write(MTSCSPacket.sendCSFail(0xB8));
                doCSPackets(c);
                return;
            } else if (c.getPlayer().getInventory(GameConstants.getInventoryType(item.getId())).getNextFreeSlot() < 0) {
                c.getSession().write(MTSCSPacket.sendCSFail(0xB1));
                doCSPackets(c);
                return;
            }
            byte pos = MapleInventoryManipulator.addId(c, item.getId(), (short) item.getCount(), null);
            if (pos < 0) {
                c.getSession().write(MTSCSPacket.sendCSFail(0xB1));
                doCSPackets(c);
                return;
            }
            chr.gainMeso(-item.getPrice(), false);
            c.getSession().write(MTSCSPacket.showBoughtCSQuestItem(item.getPrice(), (short) item.getCount(), pos, item.getId()));
        } else {
            c.getSession().write(MTSCSPacket.sendCSFail(0));

        }
        doCSPackets(c);
    }
	
	public static final void UseXmaxsSurprise(final SeekableLittleEndianAccessor slea, final MapleClient c){
		int CashId = (int) slea.readLong();
		IItem item = c.getPlayer().getCashInventory().findByCashId(CashId);
        if (item != null && item.getItemId() == 5222000 && item.getQuantity() > 0 && MapleInventoryManipulator.checkSpace(c, item.getItemId(), item.getQuantity(), item.getOwner())) {
            final int RewardIemId = RandomRewards.getInstance().getXmasreward();
            final CashItemInfo rewardItem = CashItemFactory.getInstance().getItem(RewardIemId);

            if (rewardItem == null) {

                c.getSession().write(MTSCSPacket.sendCSFail(0));
                doCSPackets(c);
                return;
            }

            IItem itemz = c.getPlayer().getCashInventory().toItem(rewardItem);
            if(itemz != null){

                if (c.getPlayer().getCashInventory().getItemsSize() >= 100) {
                    c.getSession().write(MTSCSPacket.showXmasSurprise( true , CashId , itemz ,c.getAccID()));
                    doCSPackets(c);
                    return;
                }
                c.getPlayer().getCashInventory().addToInventory(itemz);
                c.getSession().write(MTSCSPacket.showXmasSurprise( false , CashId , itemz ,c.getAccID()));
                c.getPlayer().getCashInventory().removeFromInventory(item);
			} else {
                c.getSession().write(MTSCSPacket.sendCSFail(0));
            }
              
        }
//		doCSPackets(c);
		return;
	}

    private static final MapleInventoryType getInventoryType(final int id) {
        switch (id) {
            case 50200075:
                return MapleInventoryType.EQUIP;
            case 50200074:
                return MapleInventoryType.USE;
            case 50200073:
                return MapleInventoryType.ETC;
            default:
                return MapleInventoryType.UNDEFINED;
        }
    }

    private static final void doCSPackets(MapleClient c) {
        c.getSession().write(MTSCSPacket.getCSInventory(c));
        c.getSession().write(MTSCSPacket.showNXMapleTokens(c.getPlayer()));
        c.getSession().write(MTSCSPacket.enableCSUse());
        c.getPlayer().getCashInventory().checkExpire(c);
    }
}
