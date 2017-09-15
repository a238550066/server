package server;

import client.SkillFactory;
import handling.MapleServerHandler;
import handling.channel.ChannelServer;
import handling.channel.MapleGuildRanking;
import handling.login.LoginServer;
import handling.cashshop.CashShopServer;
import handling.login.LoginInformationProvider;
import handling.world.World;
import java.sql.SQLException;
import database.DatabaseConnection;
import handling.world.family.MapleFamilyBuff;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.PreparedStatement;
import server.Timer.*;
import server.events.MapleOxQuizFactory;
import server.life.MapleLifeFactory;
import server.quest.MapleQuest;

public class Start
{
    public static void main(final String args[])
    {
        if (Boolean.parseBoolean(ServerProperties.getProperty("tms.Admin"))) {
            System.out.println("[!!! 管理員模式 !!!]");
        }

        if (Boolean.parseBoolean(ServerProperties.getProperty("tms.AutoPickUpMeso"))) {
            System.out.println("開啟自動拾取楓幣模式 :::");
        }

        if (Boolean.parseBoolean(ServerProperties.getProperty("tms.AutoRegister"))) {
            System.out.println("開啟自動註冊模式 :::");
        }

        try {
            final PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement("UPDATE accounts SET loggedin = 0");

            ps.executeUpdate();

            ps.close();
        } catch (SQLException ex) {
            throw new RuntimeException("[EXCEPTION] 資料庫連線失敗，請確認資料庫是否正常運作");
        }

        World.init();

        WorldTimer.getInstance().start();
        EtcTimer.getInstance().start();
        MapTimer.getInstance().start();
        MobTimer.getInstance().start();
        CloneTimer.getInstance().start();
        EventTimer.getInstance().start();
        BuffTimer.getInstance().start();

        LoginInformationProvider.getInstance();

        MapleQuest.initQuests();
        MapleLifeFactory.loadQuestCounts();

        ItemMakerFactory.getInstance();

        MapleItemInformationProvider.getInstance().load();
        MapleItemInformationProvider.getInstance().loadStyles(false);

        RandomRewards.getInstance();

        SkillFactory.getSkill(99999999);

        MapleOxQuizFactory.getInstance().initialize();
        MapleCarnivalFactory.getInstance();
        MapleGuildRanking.getInstance().getRank();
        MapleFamilyBuff.getBuffEntry();
        MapleServerHandler.registerMBean();

        RankingWorker.getInstance().run();
        MTSStorage.load();
        CashItemFactory.getInstance().initialize();
        ChannelServer.startChannel_Main();
        CashShopServer.run_startup_configurations();
        CheatTimer.getInstance().register(AutobanManager.getInstance(), 60000);
        LoginServer.runStartupConfigurations();

        Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown()));

        try {
            SpeedRunner.getInstance().loadSpeedRuns();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        World.registerRespawn();
        LoginServer.setOn();
        System.out.println("Server Started :::");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.gc();
        PingTimer.getInstance().register(System::gc, 1800000);
    }

    public static class Shutdown implements Runnable
    {
        @Override
        public void run() {
            new Thread(ShutdownServer.getInstance()).start();
        }
    }
}
