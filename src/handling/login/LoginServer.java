/*
 * TMS 113 handling/login/LoginServer.java
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import handling.MapleServerHandler;
import handling.mina.MapleCodecFactory;
import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.SimpleByteBufferAllocator;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import server.ServerProperties;

public class LoginServer
{
    private static IoAcceptor acceptor;
    private static Map<Integer, Integer> load = new HashMap<>();
    private static String serverName, eventMessage, bubbleMessage, bubbleMessagePos ;
    private static byte flag;
    private static int maxCharacters, userLimit, usersOn = 0;
    private static boolean finishedShutdown = true, adminOnly = false;

    public static void runStartupConfigurations()
    {
        userLimit = Integer.parseInt(ServerProperties.getProperty("tms.UserLimit"));
        serverName = ServerProperties.getProperty("tms.ServerName");
        eventMessage = ServerProperties.getProperty("tms.EventMessage");
        bubbleMessage = ServerProperties.getProperty("tms.BubbleMessage");
        bubbleMessagePos = ServerProperties.getProperty("tms.BubbleMessagePos");
        flag = Byte.parseByte(ServerProperties.getProperty("tms.Flag"));
        adminOnly = Boolean.parseBoolean(ServerProperties.getProperty("tms.Admin", "false"));
        maxCharacters = Integer.parseInt(ServerProperties.getProperty("tms.MaxCharacters"));

        ByteBuffer.setUseDirectBuffers(false);
        ByteBuffer.setAllocator(new SimpleByteBufferAllocator());

        final int PORT = Integer.parseInt(ServerProperties.getProperty("tms.LPort", "8484"));

        try {
            final InetSocketAddress inetSocketAddress = new InetSocketAddress(PORT);
            final SocketAcceptorConfig cfg = new SocketAcceptorConfig();

            cfg.getSessionConfig().setTcpNoDelay(true);
            cfg.setDisconnectOnUnbind(true);
            cfg.getFilterChain().addLast("codec", new ProtocolCodecFilter(new MapleCodecFactory()));

            acceptor = new SocketAcceptor();
            acceptor.bind(inetSocketAddress, new MapleServerHandler(-1, false), cfg);

            System.out.println("登入伺服器已啟動，位於 " + PORT + " Port");
        } catch (IOException e) {
            System.err.println("Binding to port " + PORT + " failed" + e);
        }
    }

    public static void addChannel(final int channel)
    {
        load.put(channel, 0);
    }

    public static void removeChannel(final int channel)
    {
        load.remove(channel);
    }

    public static void shutdown()
    {
        if (finishedShutdown) {
            return;
        }

        System.out.println("登入伺服器關閉中...");

        acceptor.unbindAll();

        finishedShutdown = true;
    }

    public static String getServerName()
    {
        return serverName;
    }

    public static String getEventMessage()
    {
        return eventMessage;
    }

    public static void setEventMessage(final String newMessage)
    {
        eventMessage = newMessage;
    }

    public static String getBubbleMessage()
    {
        return bubbleMessage;
    }

    public static String getBubbleMessagePos()
    {
        return bubbleMessagePos;
    }

    public static byte getFlag()
    {
        return flag;
    }

    public static void setFlag(final byte newFlag)
    {
        flag = newFlag;
    }

    public static int getMaxCharacters()
    {
        return maxCharacters;
    }

    public static Map<Integer, Integer> getLoad()
    {
        return load;
    }

    static void setLoad(final Map<Integer, Integer> _load, final int _usersOn) {
        load = _load;
        usersOn = _usersOn;
    }

    public static int getUserLimit()
    {
        return userLimit;
    }

    public static void setUserLimit(final int newLimit)
    {
        userLimit = newLimit;
    }

    public static int getUsersOn()
    {
        return usersOn;
    }

    static boolean isAdminOnly()
    {
        return adminOnly;
    }

    public static boolean isShutdown()
    {
        return finishedShutdown;
    }

    public static void setOn()
    {
        finishedShutdown = false;
    }
}
