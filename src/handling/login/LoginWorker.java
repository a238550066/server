/*
 * TMS 113 handling/login/LoginWorker.java
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
package handling.login;

import java.util.Map;
import java.util.Map.Entry;

import client.MapleClient;
import handling.channel.ChannelServer;
import tools.packet.LoginPacket;
import tools.MaplePacketCreator;

public class LoginWorker
{
    private static long lastUpdate = 0;

    public static void registerClient(final MapleClient c)
    {
        if (LoginServer.isAdminOnly() && !c.isGm()) {
            c.getSession().write(MaplePacketCreator.serverNotice(1, "目前伺服器維修中，\r\n請稍候再嘗試登入。"));
            c.getSession().write(LoginPacket.getLoginFailed(7));
            return;
        }

        if (System.currentTimeMillis() - lastUpdate > 600000) { // Update once every 10 minutes
            lastUpdate = System.currentTimeMillis();

            final Map<Integer, Integer> load = ChannelServer.getChannelLoad();

            if (load == null || load.size() <= 0) { // In an unfortunate event that client logged in before load
                lastUpdate = 0;
                c.getSession().write(LoginPacket.getLoginFailed(7));
                return;
            }

            final double loadFactor = 1200 / ((double) LoginServer.getUserLimit() / load.size());

            int usersOn = 0;

            for (Entry<Integer, Integer> entry : load.entrySet()) {
                usersOn += entry.getValue();

                load.put(entry.getKey(), Math.min(1200, (int) (entry.getValue() * loadFactor)));
            }

            LoginServer.setLoad(load, usersOn);

            lastUpdate = System.currentTimeMillis();
        }

        if (c.finishLogin() != 0) {
            c.getSession().write(LoginPacket.getLoginFailed(7));
        } else {
            if (c.getSecondPassword() == null) {
                c.getSession().write(LoginPacket.getGenderNeeded(c));
            } else {
                c.getSession().write(LoginPacket.getAuthSuccessRequest(c));
                c.getSession().write(LoginPacket.getServerList(0, LoginServer.getServerName(), LoginServer.getLoad()));
                c.getSession().write(LoginPacket.getEndOfServerList());
            }
        }
    }
}
