/*
 * TMS 113 handling/login/handler/CharLoginHandler.java
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
package handling.login.handler;

import java.util.List;
import java.util.Calendar;

import client.inventory.IItem;
import client.inventory.Item;
import client.MapleClient;
import client.MapleCharacter;
import client.MapleCharacterUtil;
import client.inventory.MapleInventory;
import client.inventory.MapleInventoryType;
import handling.channel.ChannelServer;
import handling.login.LoginInformationProvider;
import handling.login.LoginServer;
import handling.login.LoginWorker;
import server.MapleItemInformationProvider;
import server.quest.MapleQuest;
import server.ServerProperties;
import tools.MaplePacketCreator;
import tools.packet.LoginPacket;
import tools.KoreanDateUtil;
import tools.data.input.SeekableLittleEndianAccessor;

public class CharLoginHandler
{
    /**
     * 歡迎訊息
     */
    public static void welcome(final MapleClient c)
    {
       c.getSession().write(MaplePacketCreator.serverNotice(1, "歡迎來到楓之谷"));
    }

    /**
     * 登入遊戲
     */
    public static void login(final SeekableLittleEndianAccessor slea, final MapleClient c)
    {
        final String username = slea.readMapleAsciiString();
        final String password = slea.readMapleAsciiString();

        c.setAccountName(username);

        final boolean ipBan = c.hasBannedIP();
        final boolean macBan = c.hasBannedMac();

        if (Boolean.parseBoolean(ServerProperties.getProperty("tms.AutoRegister"))) {
            if (!ipBan && !macBan && !AutoRegister.isAccountExists(username)) {
                AutoRegister.createAccount(username, password, c.getSession().getRemoteAddress().toString());
            }
        }

        int loginOk = c.login(username, password);

        final Calendar tempBannedTill = c.getTempBanCalendar();

        if (loginOk == 0 && (ipBan || macBan) && !c.isGm()) {
            loginOk = 3;

            // 當此 mac 位址被封鎖時，ip 也一併封鎖
            if (macBan) {
                MapleCharacter.ban(
                    c.getSession().getRemoteAddress().toString().split(":")[0],
                    "Enforcing account ban, account: " + username,
                    false,
                    4,
                    false
                );
            }
        }

        if (loginOk != 0) {
            if (!tooManyFailLogin(c)) {
                c.getSession().write(LoginPacket.getLoginFailed(loginOk));
            }
        } else if (tempBannedTill.getTimeInMillis() != 0) {
            if (!tooManyFailLogin(c)) {
                c.getSession().write(LoginPacket.getTempBan(KoreanDateUtil.getTempBanTimestamp(tempBannedTill.getTimeInMillis()), c.getBanReason()));
            }
        } else {
            c.loginAttempt = 0;

            LoginWorker.registerClient(c);
        }
    }

    /**
     * 取得伺服器列表
     */
    public static void serverListRequest(final MapleClient c)
    {
        c.getSession().write(LoginPacket.getServerList(0, LoginServer.getServerName(), LoginServer.getLoad()));
        c.getSession().write(LoginPacket.getEndOfServerList());
    }

    /**
     * 取得伺服器狀況
     */
    public static void serverStatusRequest(final MapleClient c)
    {
        // 0 = Select world normally
        // 1 = "Since there are many users, you may encounter some..."
        // 2 = "The concurrent users in this world have reached the max"
        final int numPlayer = LoginServer.getUsersOn();
        final int userLimit = LoginServer.getUserLimit();

        if (numPlayer >= userLimit) {
            c.getSession().write(LoginPacket.getServerStatus(2));
        } else if (numPlayer * 2 >= userLimit) {
            c.getSession().write(LoginPacket.getServerStatus(1));
        } else {
            c.getSession().write(LoginPacket.getServerStatus(0));
        }
    }

    /**
     * 取得角色列表
     */
    public static void charListRequest(final SeekableLittleEndianAccessor slea, final MapleClient c)
    {
        slea.readByte();

        final int server = slea.readByte();
        final int channel = slea.readByte() + 1;

        c.setWorld(server);
        c.setChannel(channel);

        final List<MapleCharacter> chars = c.loadCharacters(server);

        if (chars != null) {
            c.getSession().write(LoginPacket.getCharList(chars, c.getCharacterSlots()));
        } else {
            c.getSession().close();
        }
    }

    /**
     * 檢查角色名稱是否可用
     */
    public static void checkCharName(final String name, final MapleClient c)
    {
        final boolean deny = !MapleCharacterUtil.canCreateChar(name) || LoginInformationProvider.getInstance().isForbiddenName(name);

        c.getSession().write(LoginPacket.charNameResponse(name, deny));
    }

    /**
     * 創建角色
     */
    public static void createChar(final SeekableLittleEndianAccessor slea, final MapleClient c)
    {
        final String name = slea.readMapleAsciiString();

        // 0 = 皇家騎士團
        // 1 = 冒險家
        // 2 = 狂狼勇士
        final int jobType = slea.readInt();

        final int face = slea.readInt();
        final int hair = slea.readInt();
        final int hairColor = 0;
        final byte skinColor = 0;
        final int top = slea.readInt();
        final int bottom = slea.readInt();
        final int shoes = slea.readInt();
        final int weapon = slea.readInt();

        final byte gender = c.getGender();

        if (gender == 0) {
            if (face != 20100 && face != 20401 && face != 20402) {
                return;
            }

            if (hair != 30030 && hair != 30027 && hair != 30000) {
                return;
            }

            if (top != 1040002 && top != 1040006 && top != 1040010 && top != 1042167) {
                return;
            }

            if (bottom != 1060002 && bottom != 1060006 && bottom != 1062115) {
                return;
            }
        } else if (gender == 1) {
            if (face != 21002 && face != 21700 && face != 21201) {
                return;
            }
            if (hair != 31002 && hair != 31047 && hair != 31057) {
                return;
            }
            if (top != 1041002 && top != 1041006 && top != 1041010 && top != 1041011 && top != 1042167) {
                return;
            }
            if (bottom != 1061002 && bottom != 1061008 && bottom != 1062115) {
                return;
            }
        } else {
            return;
        }

        if (shoes != 1072001 && shoes != 1072005 && shoes != 1072037 && shoes != 1072038 && shoes != 1072383) {
            return;
        }
        if (weapon != 1302000 && weapon != 1322005 && weapon != 1312004 && weapon != 1442079 ) {
            return;
        }

        MapleCharacter newChar = MapleCharacter.getDefault(c, jobType);

        newChar.setWorld((byte) c.getWorld());
        newChar.setFace(face);
        newChar.setHair(hair + hairColor);
        newChar.setGender(gender);
        newChar.setName(name);
        newChar.setSkinColor(skinColor);

        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        MapleInventory equip = newChar.getInventory(MapleInventoryType.EQUIPPED);

        IItem item = ii.getEquipById(top);
        item.setPosition((byte) -5);
        equip.addFromDB(item);

        item = ii.getEquipById(bottom);
        item.setPosition((byte) -6);
        equip.addFromDB(item);

        item = ii.getEquipById(shoes);
        item.setPosition((byte) -7);
        equip.addFromDB(item);

        item = ii.getEquipById(weapon);
        item.setPosition((byte) -11);
        equip.addFromDB(item);

        //blue/red pots
        switch (jobType) {
            case 0: // 皇家騎士團
                newChar.setQuestAdd(MapleQuest.getInstance(20000), (byte) 1, null); //>_>_>_> ugh
                newChar.setQuestAdd(MapleQuest.getInstance(20010), (byte) 1, null); //>_>_>_> ugh
                newChar.setQuestAdd(MapleQuest.getInstance(20015), (byte) 1, null); //>_>_>_> ugh
                newChar.setQuestAdd(MapleQuest.getInstance(20020), (byte) 1, null); //>_>_>_> ugh
                newChar.setQuestAdd(MapleQuest.getInstance(20022), (byte) 1, "1");

                newChar.getInventory(MapleInventoryType.ETC).addItem(new Item(4161047, (byte) 0, (short) 1, (byte) 0));
                break;
            case 1: // 冒險家
                newChar.getInventory(MapleInventoryType.ETC).addItem(new Item(4161001, (byte) 0, (short) 1, (byte) 0));
                break;
            case 2: // 狂狼勇士
                newChar.getInventory(MapleInventoryType.ETC).addItem(new Item(4161048, (byte) 0, (short) 1, (byte) 0));
                break;
        }

        if (!MapleCharacterUtil.canCreateChar(name) || LoginInformationProvider.getInstance().isForbiddenName(name)) {
            c.getSession().write(LoginPacket.addNewCharEntry(newChar, false));
            return;
        }

        MapleCharacter.saveNewCharToDB(newChar, jobType);

        c.getSession().write(LoginPacket.addNewCharEntry(newChar, true));

        c.createdChar(newChar.getId());
    }

    /**
     * 刪除角色
     */
    public static void deleteChar(final SeekableLittleEndianAccessor slea, final MapleClient c)
    {
        slea.readByte();

        final String secondPassword = slea.readMapleAsciiString();
        final int charId = slea.readInt();

        // 嘗試刪除其他人的角色
        if (!c.login_Auth(charId)) {
            invalidCharDeleteRequest(c);
            return;
        }

        byte state = 0;

        if (c.getSecondPassword() != null) { // On the server, there's a second password
            if (secondPassword == null) { // Client's hacking
                c.getSession().close();
                return;
            } else if (!c.checkSecondPassword(secondPassword)) { // Wrong Password
                state = 16;
            }
        }

        if (state == 0) {
            state = (byte) c.deleteCharacter(charId);
        }

        c.getSession().write(LoginPacket.deleteCharResponse(charId, state));
    }

    /**
     * 選擇角色
     */
    public static void selectChar(final SeekableLittleEndianAccessor slea, final MapleClient c)
    {
        final int charId = slea.readInt();

        if (!c.login_Auth(charId)) {
            if (c.getPlayer() == null) {
                System.out.println("Invalid char select request: " + c.getSession().getRemoteAddress().toString());
                c.getSession().close();
            }

            return;
        }

        if (c.getIdleTask() != null) {
            c.getIdleTask().cancel(true);
        }

        c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION, c.getSessionIPAddress());

        c.getSession().write(MaplePacketCreator.getServerIP(Integer.parseInt(ChannelServer.getInstance(c.getChannel()).getIP().split(":")[1]), charId));
    }

    /**
     * 驗證第二組密碼
     */
    public static void authSecondPassword(final SeekableLittleEndianAccessor slea, final MapleClient c)
    {
        final String password = slea.readMapleAsciiString();
        final int charId = slea.readInt();

        if (tooManyFailLogin(c) || c.getSecondPassword() == null) {
            c.getSession().close();
            return;
        } else if (!c.login_Auth(charId)) {
            invalidCharDeleteRequest(c);
            return;
        }

        if (!c.checkSecondPassword(password)) {
            c.getSession().write(LoginPacket.secondPwError((byte) 0x14));
        } else {
            c.updateMacs(slea.readMapleAsciiString());

            if (c.getIdleTask() != null) {
                c.getIdleTask().cancel(true);
            }

            c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION, c.getSessionIPAddress());

            c.getSession().write(MaplePacketCreator.getServerIP(Integer.parseInt(ChannelServer.getInstance(c.getChannel()).getIP().split(":")[1]), charId));
        }
    }

    /**
     * 設置性別及第二組密碼
     */
    public static void setGenderRequest(final SeekableLittleEndianAccessor slea, final MapleClient c)
    {
        final String username = slea.readMapleAsciiString();
        final String password = slea.readMapleAsciiString();

        if (!c.getAccountName().equals(username) || c.getSecondPassword() != null) {
            c.getSession().close();
        } else {
            c.setGender(slea.readByte());
            c.setSecondPassword(password);

            c.updateSecondPassword();
            c.updateGender();

            c.getSession().write(LoginPacket.getGenderChanged(c));

            c.updateLoginState(MapleClient.LOGIN_NOTLOGGEDIN, c.getSessionIPAddress());
        }
    }

    /**
     * 判斷是否登入失敗五次以上
     */
    private static boolean tooManyFailLogin(final MapleClient c)
    {
        return ++(c.loginAttempt) > 5;
    }

    /**
     * 記錄並結束非法連線
     */
    private static void invalidCharDeleteRequest(final MapleClient c)
    {
        System.out.println("Invalid char delete request: " + c.getSession().getRemoteAddress().toString());

        c.getSession().close();
    }
}
