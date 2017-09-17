package server;

import java.sql.SQLException;

import database.DatabaseConnection;
import handling.cashshop.CashShopServer;
import handling.channel.ChannelServer;
import handling.login.LoginServer;
import handling.world.World;
import server.Timer.*;

public class ShutdownServer implements Runnable {

    private static final ShutdownServer instance = new ShutdownServer();
    public static boolean running = false;

    public static ShutdownServer getInstance() {
        return instance;
    }

    @Override
    public void run() {
        synchronized (this) {
            if (running) { //Run once!
                return;
            }

            running = true;
        }

        World.isShutDown = true;

        try {
            LoginServer.shutdown();
            CashShopServer.shutdown();

            for (int i : ChannelServer.getAllInstance().toArray(new Integer[0])) {
                try {
                    ChannelServer.getInstance(i).shutdown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            World.Guild.save();
            World.Alliance.save();
            World.Family.save();
            DatabaseConnection.closeAll();

            WorldTimer.getInstance().stop();
            EventTimer.getInstance().stop();
            MapTimer.getInstance().stop();
            MobTimer.getInstance().stop();
            CheatTimer.getInstance().stop();
            BuffTimer.getInstance().stop();
            CloneTimer.getInstance().stop();
            EtcTimer.getInstance().stop();
            PingTimer.getInstance().stop();
        } catch (SQLException e) {
            System.err.println("THROW" + e);
        }

        System.exit(0);
    }
}
