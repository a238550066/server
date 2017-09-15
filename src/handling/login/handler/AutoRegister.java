/*
 * TMS 113 handling/login/handler/AutoRegister.java
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

import client.LoginCrypto;
import database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AutoRegister
{
    public static boolean success = false;

    /**
     * 確認帳號是否重複
     */
    static boolean isAccountExists(String login)
    {
        try {
            final Connection con = DatabaseConnection.getConnection();

            PreparedStatement ps = con.prepareStatement("SELECT `name` FROM `accounts` WHERE `name` = ?");

            ps.setString(1, login);

            ResultSet rs = ps.executeQuery();

            if (rs.first()) {
                return true;
            }
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }

        return false;
    }

    /**
     * 自動創建帳號
     */
    static void createAccount(String username, String password, String ipAddress)
    {
        try {
            final Connection con = DatabaseConnection.getConnection();

            PreparedStatement ps = con.prepareStatement("INSERT INTO `accounts` (`name`, `password`, `email`, `birthday`, `macs`, `SessionIP`) VALUES (?, ?, ?, ?, ?, ?)");

            ps.setString(1, username);
            ps.setString(2, LoginCrypto.hexSha1(password));
            ps.setString(3, "autoregister@mail.com");
            ps.setString(4, "2008-04-07");
            ps.setString(5, "00-00-00-00-00-00");
            ps.setString(6, ipAddress.substring(1, ipAddress.lastIndexOf(':')));

            ps.executeUpdate();

            ps.close();

            success = true;
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }
    }
}
