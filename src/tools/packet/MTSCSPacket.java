/*
 * TMS 113 tools.packet/MTSCSPacket.java
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
package tools.packet;

import java.sql.SQLException;
import java.sql.ResultSet;

import java.util.List;
import client.MapleClient;
import client.MapleCharacter;
import client.inventory.IItem;
import server.CashShop;
import server.CashItemFactory;
import server.CashItemInfo;
import handling.MaplePacket;
import handling.SendPacketOpcode;
import constants.ServerConstants;
import java.util.Collection;
import tools.Pair;
import java.util.Map;
import java.util.Map.Entry;
import server.MTSStorage.MTSItemInfo;
import tools.HexTool;
import tools.KoreanDateUtil;
import tools.data.output.MaplePacketLittleEndianWriter;

public class MTSCSPacket {

	private static final byte[] warpCS = HexTool.getByteArrayFromHexString("00 00 0A 00 50 10 27 00 00 00 5A 00 00 00 00 00 00 00 00 00 00 00 00 FF 00 00 00 00 00 00 00 00 00");
	private static final byte[] warpCS2 = HexTool.getByteArrayFromHexString("06 00 00 00 31 00 30 00 31 00 00 00 00 00 00 00 05 00 0E 00 05 00 08 06 A0 01 14 00 C8 FE 8D 06 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 03 00 13 00 0A 01 0C 06 06 00 00 00 31 00 30 00 31 00 00 00 00 00 00 00 03 00 16 00 0D 00 0C 06 90 01 14 00 F8 36 8C 06 31 00 00 00 00 00 00 00 03 00 19 00 10 01 0C 06 06 00 00 00 31 00 30 00");
  
    public static MaplePacket warpCS(MapleClient c) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPEN.getValue());

        PacketHelper.addCharacterInfo(mplew, c.getPlayer());
        mplew.writeMapleAsciiString(c.getAccountName());

        Collection<CashItemInfo> cmi = CashItemFactory.getInstance().getAllItems();
        mplew.writeInt(0);
        mplew.writeShort(cmi.size());
        for (CashItemInfo cm : cmi) {
            addModCashItemInfo(mplew, cm);
        }

        mplew.write(warpCS);
        mplew.write(warpCS2);

        int[] itemz = CashItemFactory.getInstance().getTopItems();
        for (int i = 1; i <= 8; i++) {
            for (int j = 0; j <= 1; j++) {
                for (int item : itemz) {
                    mplew.writeInt(i);
                    mplew.writeInt(j);
                    mplew.writeInt(item);
                }
            }
        }

        mplew.writeShort(0); //stock
        mplew.writeShort(0); //limited goods 1-> A2 35 4D 00 CE FD FD 02 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 02 00 00 00 FF FF FF FF FF FF FF FF 06 00 00 00 1F 1C 32 01 A7 3F 32 01 FF FF FF FF FF FF FF FF 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00
        mplew.write(0); //eventON

        return mplew.getPacket();
    }

    public static MaplePacket playCashSong(int itemid, String name) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CASH_SONG.getValue());
        mplew.writeInt(itemid);
        mplew.writeMapleAsciiString(name);
        return mplew.getPacket();
    }

	public static MaplePacket showAcc(MapleClient c)
	{
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
		mplew.writeShort(0x15F);
		mplew.write(1);
		mplew.writeMapleAsciiString(c.getAccountName());
		return mplew.getPacket();
	}
    public static MaplePacket useCharm(byte charmsleft, byte daysleft) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
        mplew.write(6);
        mplew.write(1);
        mplew.write(charmsleft);
        mplew.write(daysleft);

        return mplew.getPacket();
    }

    public static MaplePacket useWheel(int charmsleft) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
        mplew.write(21);
        mplew.writeLong(charmsleft);

        return mplew.getPacket();
    }

    public static MaplePacket itemExpired(int itemid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        // 1E 00 02 83 C9 51 00

        // 21 00 08 02
        // 50 62 25 00
        // 50 62 25 00
        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
        mplew.write(2);
        mplew.writeInt(itemid);

        return mplew.getPacket();
    }

    public static MaplePacket ViciousHammer(boolean start, int hammered) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.VICIOUS_HAMMER.getValue());
        if (start) {
            mplew.write(49);
            mplew.writeInt(0);
            mplew.writeInt(hammered);
        } else {
            mplew.write(53);
            mplew.writeInt(0);
        }

        return mplew.getPacket();
    }

    public static MaplePacket changePetFlag(int uniqueId, boolean added, int flagAdded) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PET_FLAG_CHANGE.getValue());

        mplew.writeLong(uniqueId);
        mplew.write(added ? 1 : 0);
        mplew.writeShort(flagAdded);

        return mplew.getPacket();
    }

    public static MaplePacket changePetName(MapleCharacter chr, String newname, int slot) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PET_NAMECHANGE.getValue());

        mplew.writeInt(chr.getId());
        mplew.write(0);
        mplew.writeMapleAsciiString(newname);
        mplew.write(slot);

        return mplew.getPacket();
    }

    public static MaplePacket showNotes(ResultSet notes, int count) throws SQLException {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_NOTES.getValue());
        mplew.write(3);
        mplew.write(count);
        for (int i = 0; i < count; i++) {
            mplew.writeInt(notes.getInt("id"));
            mplew.writeMapleAsciiString(notes.getString("from"));
            mplew.writeMapleAsciiString(notes.getString("message"));
            mplew.writeLong(PacketHelper.getKoreanTimestamp(notes.getLong("timestamp")));
            mplew.write(notes.getInt("gift"));
            notes.next();
        }

        return mplew.getPacket();
    }

    public static MaplePacket useChalkboard(final int charid, final String msg) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CHALKBOARD.getValue());

        mplew.writeInt(charid);
        if (msg == null || msg.length() <= 0) {
            mplew.write(0);
        } else {
            mplew.write(1);
            mplew.writeMapleAsciiString(msg);
        }

        return mplew.getPacket();
    }

    public static MaplePacket getTrockRefresh(MapleCharacter chr, boolean vip, boolean delete) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.TROCK_LOCATIONS.getValue());
        mplew.write(delete ? 2 : 3);
        mplew.write(vip ? 1 : 0);
        if (vip) {
            int[] map = chr.getRocks();
            for (int i = 0; i < 10; i++) {
                mplew.writeInt(map[i]);
            }
        } else {
            int[] map = chr.getRegRocks();
            for (int i = 0; i < 5; i++) {
                mplew.writeInt(map[i]);
            }
        }
        return mplew.getPacket();
    }

    public static MaplePacket sendWishList(MapleCharacter chr, boolean update) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(update?0x4C:0x4A); //+12
        int[] list = chr.getWishlist();
        for (int i = 0; i < 10; i++) {
            mplew.writeInt(list[i] != -1 ? list[i] : 0);
        }
        return mplew.getPacket();
    }

    public static MaplePacket showNXMapleTokens(MapleCharacter chr) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_UPDATE.getValue());
        mplew.writeInt(chr.getCSPoints(1)); // A-cash
        mplew.writeInt(chr.getCSPoints(2)); // MPoint

        return mplew.getPacket();
    }

    public static MaplePacket showBoughtCSPackage(Map<Integer, IItem> ccc, int accid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(0x80); //use to be 7a
        mplew.write(ccc.size());
        for (Entry<Integer, IItem> sn : ccc.entrySet()) {
            addCashItemInfo(mplew, sn.getValue(), accid, sn.getKey().intValue());
        }
        mplew.writeShort(0);

        return mplew.getPacket();
    }

    public static MaplePacket showBoughtCSItem(int itemid, int sn, int uniqueid, int accid, int quantity, String giftFrom, long expire) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(0x5A); //use to be 4a
        addCashItemInfo(mplew, uniqueid, accid, itemid, sn, quantity, giftFrom, expire);

        return mplew.getPacket();
    }

    public static MaplePacket showBoughtCSItem(IItem item, int sn, int accid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(0x4E);
        addCashItemInfo(mplew, item, accid, sn);

        return mplew.getPacket();
    }

    public static MaplePacket showXmasSurprise(boolean full,int idFirst, IItem item, int accid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.XMAS_SURPRISE.getValue());
        mplew.write(full ? 222 : 223);
		if(!full)
		{
            mplew.writeLong(idFirst); //uniqueid of the xmas surprise itself
            mplew.writeInt(0);
            addCashItemInfo(mplew, item, accid, 0); //info of the new item, but packet shows 0 for sn?
            mplew.writeInt(item.getItemId());
            mplew.write(1);
            mplew.write(1);
		}

        return mplew.getPacket();
    }

    public static final void addCashItemInfo(MaplePacketLittleEndianWriter mplew, IItem item, int accId, int sn) {
        addCashItemInfo(mplew, item, accId, sn, true);
    }

    public static final void addCashItemInfo(MaplePacketLittleEndianWriter mplew, IItem item, int accId, int sn, boolean isFirst) {
        addCashItemInfo(mplew, item.getUniqueId(), accId, item.getItemId(), sn, item.getQuantity(), item.getGiftFrom(), item.getExpiration(), isFirst); //owner for the lulz
    }

    public static final void addCashItemInfo(MaplePacketLittleEndianWriter mplew, int uniqueid, int accId, int itemid, int sn, int quantity, String sender, long expire) {
        addCashItemInfo(mplew, uniqueid, accId, itemid, sn, quantity, sender, expire, true);
    }

    public static final void addCashItemInfo(MaplePacketLittleEndianWriter mplew, int uniqueid, int accId, int itemid, int sn, int quantity, String sender, long expire, boolean isFirst) {
        mplew.writeLong(uniqueid > 0 ? uniqueid : 0);
        mplew.writeLong(accId);
        mplew.writeInt(itemid);
        mplew.writeInt(isFirst ? sn : 0);
        mplew.writeShort(quantity);
        mplew.writeAsciiString(sender, 15); //owner for the lulzlzlzl
        PacketHelper.addExpirationTime(mplew, expire);
        mplew.writeLong(isFirst ? 0 : sn);
        //if (isFirst && uniqueid > 0 && GameConstants.isEffectRing(itemid)) {
        //	MapleRing ring = MapleRing.loadFromDb(uniqueid);
        //	if (ring != null) { //or is this only for friendship rings, i wonder. and does isFirst even matter
        //		mplew.writeMapleAsciiString(ring.getPartnerName());
        //		mplew.writeInt(itemid);
        //		mplew.writeShort(quantity);
        //	}
        //}
    }

    private static void addModCashItemInfo(MaplePacketLittleEndianWriter mplew, CashItemInfo item)
    {
        int flags = item.flags;

        mplew.writeInt(item.sn);
        mplew.writeInt(flags);

        if ((flags & 0x1) != 0) {
            mplew.writeInt(item.itemId);
        }

        if ((flags & 0x2) != 0) {
            mplew.writeShort(item.count);
        }

        if ((flags & 0x10) != 0) {
            mplew.write(item.priority);
        }

        if ((flags & 0x4) != 0) {
            mplew.writeInt(item.price);
        }

        if ((flags & 0x8) != 0) {
            mplew.write(item.unk_1 - 1);
        }

        if ((flags & 0x20) != 0) {
            mplew.writeShort(item.period);
        }

        if ((flags & 0x40) != 0) {
            mplew.writeInt(0);
        }

        if ((flags & 0x80) != 0) {
            mplew.writeInt(item.meso);
        }

        if ((flags & 0x100) != 0) {
            mplew.write(item.unk_2 - 1);
        }

        if ((flags & 0x200) != 0) {
            mplew.write(item.gender);
        }

        if ((flags & 0x400) != 0) {
            mplew.write(item.showUp ? 1 : 0);
        }

        if ((flags & 0x800) != 0) {
            mplew.write(item.mark);
        }

        if ((flags & 0x1000) != 0) {
            mplew.write(item.unk_3 - 1);
        }

        if ((flags & 0x2000) != 0) {
            mplew.writeShort(0);
        }

        if ((flags & 0x4000) != 0) {
            mplew.writeShort(0);
        }

        if ((flags & 0x8000) != 0) {
            mplew.writeShort(0);
        }

        if ((flags & 0x10000) != 0) {
            List<CashItemInfo> pack = CashItemFactory.getInstance().getPackageItems(item.sn);

            if (pack == null) {
                mplew.write(0);
            } else {
                mplew.write(pack.size());

                for (CashItemInfo packItem : pack) {
                    mplew.writeInt(packItem.getSN());
                }
            }
        }
    }

    public static MaplePacket showBoughtCSQuestItem(int price, short quantity, byte position, int itemid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(0x8F);
        mplew.writeInt(price);
        mplew.writeShort(quantity);
        mplew.writeShort(position);
        mplew.writeInt(itemid);

        return mplew.getPacket();
    }

    public static MaplePacket sendCSFail(int err)
    {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        // err 對應表
        //
        // 0xA6 已超過工作時間。休息一下再繼續。
        // 0xA8 GASH 餘額不足。
        // 0xA9 未滿 14 歲的玩家不能贈送加值道具。
        // 0xAA 已超過可送禮物的限額。
        // 0xAB 無法送禮到相同的帳號！請利用該角色登入後購買。
        // 0xAC 請確認是否為錯誤的角色名稱！
        // 0xAD 此為有性別限制的道具！請確認收禮人的性別。
        // 0xAE 收禮人的保管箱已滿！無法送出禮物
        // 0xAF 請確認是否超過可以保有的加值道具數量。
        // 0xB0 請確認對方的伺服器、角色名稱是否正確；贈送的物品是否有性別限制，並請確認對方所擁有的加值道具是否已達上限。
        // 0xB3 此序號發生異常，請洽客服人員。
        // 0xB4 此序號已過有效期限！
        // 0xB5 此序號已被使用過！
        // 0xB6 只有在 Premium 網咖上可以使用的會員卡。請在 Premium 網咖上使用。
        // 0xB7 Premium 網咖專用會員卡 已經使用過的會員卡。
        // 0xB8 Premium 網咖 已經過期的會員卡。
        // 0xB9 這是 NexonCashCoupon 號碼！請上 Nexon.com(www.nexon.com) 的 MyPage > NexonCash > Menu 中登錄 Coupon 號碼。
        // 0xBA 你的性別無法使用這項道具。
        // 0xBB 此優待券為專用道具。因此無法贈送。
        // 0xBC 此優待券為楓葉點數專用！無法送禮給其他人。
        // 0xBD 請確認是否你的道具欄的空間不夠。
        // 0xBE 這種物品只在優秀會員網咖買得到。
        // 0xBF 戀人道具只能贈送給相同頻道的不同性別的角色。請確認你要送出禮物的角色在同一頻道且性別不同。
        // 0xC0 請你正確輸入要送禮物的角色名稱。
        // 0xC1 現在不是銷售時間。
        // 0xC2 這種商品已經賣完了。
        // 0xC3 亂碼
        // 0xC4 楓幣不足。
        // 0xC5 請確認第二組密碼 再重試。
        // 0xC6 亂碼
        // 0xC7 已經報名
        // 0xCD 該道具已超過一日購買上限，無法繼續購買。
        // 0xD0 已超過 Gash 帳號使用上限！詳細內容請參考序號
        // 0xD2 未滿 7 歲的玩家無法購買此道具。
        // 0xD3 未滿 7 歲的玩家無法領取禮物。
        // 0xD4 此序號不存在。
        // 0xD5 目前系統繁忙，請於一小時後再試。
        // 0xD6 請至楓之谷官網認證您的遊戲帳號，才能使用購物商場。
        // 0xD7 必須要有折價券才能可以買該道具。
        // 0xD8 限 20 級以上才能申請伺服器移民。
        // 0xD9 無法移民到相同的伺服器！
        // 0xDA 無法移民到最新開放的伺服器！
        // 0xDB 若所要移民的伺服器中，已無多餘的角色欄位，是無法進行伺服器移民！
        // 0xDC 無法將角色移民到您所指定的伺服器！
        // 0xDD 暱稱或帳號讀取失敗！
        // 0xE0 這道具是無法使用楓點購買。
        // 0xE1 不好意思。請再試一次。
        //
        // 預設：發生不明錯誤！購物商場使用失敗！

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(0x4D);
        mplew.write(err);

        return mplew.getPacket();
    }

    public static MaplePacket showCouponRedeemedItem(int itemid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.writeShort(0x50);
        mplew.writeInt(0);
        mplew.writeInt(1);
        mplew.writeShort(1);
        mplew.writeShort(0x1A);
        mplew.writeInt(itemid);
        mplew.writeInt(0);

        return mplew.getPacket();
    }

    public static MaplePacket showCouponRedeemedItem(Map<Integer, IItem> items, int mesos, int maplePoints, MapleClient c) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(0x52); //use to be 4c
        mplew.write(items.size());
        for (Entry<Integer, IItem> item : items.entrySet()) {
            addCashItemInfo(mplew, item.getValue(), c.getAccID(), item.getKey().intValue());
        }
        mplew.writeLong(maplePoints);
        mplew.writeInt(mesos);

        return mplew.getPacket();
    }

    public static MaplePacket enableCSUse() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(0x0A);
        mplew.write(1);
        mplew.writeInt(0);

        return mplew.getPacket();
    }

    public static MaplePacket getCSInventory(MapleClient c) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(0x46); // use to be 3e
        CashShop mci = c.getPlayer().getCashInventory();
        mplew.writeShort(mci.getItemsSize());
        for (IItem itemz : mci.getInventory()) {
            addCashItemInfo(mplew, itemz, c.getAccID(), 0); //test
        }
        mplew.writeShort(c.getPlayer().getStorage().getSlots());
        mplew.writeInt(c.getCharacterSlots());
        mplew.writeShort(4); //00 00 04 00 <-- added?

        return mplew.getPacket();
    }

    //work on this packet a little more
    public static MaplePacket getCSGifts(MapleClient c) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());

        mplew.write(0x48); //use to be 40
        List<Pair<IItem, String>> mci = c.getPlayer().getCashInventory().loadGifts();
        mplew.writeShort(mci.size());
        for (Pair<IItem, String> mcz : mci) {
            mplew.writeLong(mcz.getLeft().getUniqueId());
            mplew.writeInt(mcz.getLeft().getItemId());
            mplew.writeAsciiString(mcz.getLeft().getGiftFrom(), 15);
            mplew.writeAsciiString(mcz.getRight(), 74);
        }

        return mplew.getPacket();
    }

    public static MaplePacket cashItemExpired(int uniqueid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(0x71); //use to be 5d
        mplew.writeLong(uniqueid);
        return mplew.getPacket();
    }

    public static MaplePacket sendGift(int price, int itemid, int quantity, String receiver,boolean isPackage) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(isPackage? 0x82 : 0x55); //use to be 7C
        mplew.writeMapleAsciiString(receiver);
        mplew.writeInt(itemid);
        mplew.writeShort(quantity);
        mplew.writeShort(0); //maplePoints
        mplew.writeInt(price);

        return mplew.getPacket();
    }

    public static MaplePacket increasedInvSlots(int inv, int slots) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(0x57);
        mplew.write(inv);
        mplew.writeShort(slots);

        return mplew.getPacket();
    }

    //also used for character slots !
    public static MaplePacket increasedStorageSlots(int slots) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(0x5B);
        mplew.writeShort(slots);

        return mplew.getPacket();
    }

    public static MaplePacket confirmToCSInventory(IItem item, int accId, int sn) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(0x61);
        addCashItemInfo(mplew, item, accId, sn, false);

        return mplew.getPacket();
    }

    public static MaplePacket confirmFromCSInventory(IItem item, short pos) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(0x5F);
        mplew.writeShort(pos);
        PacketHelper.addItemInfo(mplew, item, true, true);

        return mplew.getPacket();
    }

    public static MaplePacket sendMesobagFailed() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MESOBAG_FAILURE.getValue());
        return mplew.getPacket();
    }

    public static MaplePacket sendMesobagSuccess(int mesos) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MESOBAG_SUCCESS.getValue());
        mplew.writeInt(mesos);
        return mplew.getPacket();
    }

//======================================MTS===========================================
    public static final MaplePacket startMTS(final MapleCharacter chr, MapleClient c) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MTS_OPEN.getValue());

        PacketHelper.addCharacterInfo(mplew, chr);

        mplew.writeMapleAsciiString(c.getAccountName());
        mplew.writeInt(ServerConstants.MTS_MESO);
        mplew.writeInt(ServerConstants.MTS_TAX);
        mplew.writeInt(ServerConstants.MTS_BASE);
        mplew.writeInt(24);
        mplew.writeInt(168);
        mplew.writeLong(PacketHelper.getTime(System.currentTimeMillis()));
        return mplew.getPacket();
    }

    public static final MaplePacket sendMTS(final List<MTSItemInfo> items, final int tab, final int type, final int page, final int pages) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(0x15); //operation
        mplew.writeInt(pages * 16); //total items
        mplew.writeInt(items.size()); //number of items on this page
        mplew.writeInt(tab);
        mplew.writeInt(type);
        mplew.writeInt(page);
        mplew.write(1);
        mplew.write(1);

        for (MTSItemInfo item : items) {
            addMTSItemInfo(mplew, item);
        }
        mplew.write(1); //0 or 1?

        return mplew.getPacket();
    }

    public static final MaplePacket showMTSCash(final MapleCharacter p) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.GET_MTS_TOKENS.getValue());
//        mplew.writeInt(p.getCSPoints(1));
        mplew.writeInt(p.getCSPoints(2));
        return mplew.getPacket();
    }

    public static final MaplePacket getMTSWantedListingOver(final int nx, final int items) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(0x3D);
        mplew.writeInt(nx);
        mplew.writeInt(items);
        return mplew.getPacket();
    }

    public static final MaplePacket getMTSConfirmSell() {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(0x1C);
        return mplew.getPacket();
    }

    public static final MaplePacket getMTSFailSell() {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(0x1D);
        mplew.write(0x42);
        return mplew.getPacket();
    }

    public static final MaplePacket getMTSConfirmBuy() {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(0x32);
        return mplew.getPacket();
    }

    public static final MaplePacket getMTSFailBuy() {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(0x33);
        mplew.write(0x42);
        return mplew.getPacket();
    }

    public static final MaplePacket getMTSConfirmCancel() {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(0x22);
        return mplew.getPacket();
    }

    public static final MaplePacket getMTSFailCancel() {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(0x23);
        mplew.write(0x42);
        return mplew.getPacket();
    }

    public static final MaplePacket getMTSConfirmTransfer(final int quantity, final int pos) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(0x24);
        mplew.writeInt(quantity);
        mplew.writeInt(pos);
        return mplew.getPacket();
    }

    private static final void addMTSItemInfo(final MaplePacketLittleEndianWriter mplew, final MTSItemInfo item) {
        PacketHelper.addItemInfo(mplew, item.getItem(), true, true);
        mplew.writeInt(item.getId()); //id
        mplew.writeInt(item.getTaxes()); //this + below = price
        mplew.writeInt(item.getPrice()); //price
        mplew.writeInt(item.getItem().getQuantity());// qiantity
        mplew.writeLong(0);
        mplew.writeLong(KoreanDateUtil.getQuestTimestamp(item.getEndingDate()));
        mplew.writeMapleAsciiString(item.getSeller()); //account name (what was nexon thinking?)
        mplew.writeMapleAsciiString(item.getSeller()); //char name
        mplew.writeZeroBytes(28);
    }

    public static final MaplePacket getNotYetSoldInv(final List<MTSItemInfo> items) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(0x20);
        
        mplew.writeInt(items.size());

        for (MTSItemInfo item : items) {
            addMTSItemInfo(mplew, item);
        }
		
        mplew.write(0); //0 or 1?

        return mplew.getPacket();
    }

    public static final MaplePacket getTransferInventory(final List<IItem> items, final boolean changed) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(0x1E);

        mplew.writeInt(items.size());
        int i = 0;
        for (IItem item : items) {
            PacketHelper.addItemInfo(mplew, item, true, true);
            mplew.writeInt(Integer.MAX_VALUE - i); //fake ID
            mplew.writeInt(110);
            mplew.writeInt(1011); //fake
            mplew.writeZeroBytes(52);
            i++;
        }
        mplew.writeInt(-47 + i - 1);
        mplew.write(changed ? 1 : 0);

        return mplew.getPacket();
    }

    public static final MaplePacket addToCartMessage(boolean fail, boolean remove) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        if (remove) {
            if (fail) {
                mplew.write(0x29);
                mplew.writeInt(-1);
            } else {
                mplew.write(0x28);
            }
        } else {
            if (fail) {
                mplew.write(0x27);
                mplew.writeInt(-1);
            } else {
                mplew.write(0x26);
            }
        }

        return mplew.getPacket();
    }
}
