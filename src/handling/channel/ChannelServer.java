/*
 * TMS 113 handling/channel/ChannelServer.java
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
package handling.channel;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import client.MapleCharacter;
import handling.ByteArrayMaplePacket;
import handling.MaplePacket;
import handling.MapleServerHandler;
import handling.login.LoginServer;
import handling.mina.MapleCodecFactory;
import handling.world.CheaterData;
import scripting.EventScriptManager;
import server.events.MapleCoconut;
import server.events.MapleEvent;
import server.events.MapleEventType;
import server.events.MapleFitness;
import server.events.MapleOla;
import server.events.MapleOxQuiz;
import server.events.MapleSnowball;
import server.life.PlayerNPC;
import server.MapleSquad;
import server.MapleSquad.MapleSquadType;
import server.maps.MapleMapFactory;
import server.ServerProperties;
import server.shops.HiredMerchant;
import server.shops.HiredFishing;
import tools.CollectionUtil;
import tools.ConcurrentEnumMap;
import tools.MaplePacketCreator;
import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.SimpleByteBufferAllocator;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;
import org.apache.mina.transport.socket.nio.SocketAcceptor;

public class ChannelServer implements Serializable
{
    public static long serverStartTime;
    private int expRate = 2, mesoRate = 1, dropRate = 1, cashRate = 1;
    private short port = 8585;
    private static final short DEFAULT_PORT = 8585;
    private int channel, runningMerchants = 0, runningFishings = 0, flags = 0;
    private String serverMessage;
    private String ip;
    private boolean shutdown = false, finishedShutdown = false, MegaphoneMuteState = false, adminOnly = false;
    private PlayerStorage players;
    private MapleServerHandler serverHandler;
    private IoAcceptor acceptor;
    private final MapleMapFactory mapFactory;
    private EventScriptManager eventSM;
    private static final Map<Integer, ChannelServer> instances = new HashMap<>();
    private final Map<MapleSquadType, MapleSquad> mapleSquads = new ConcurrentEnumMap<>(MapleSquadType.class);
    private final Map<Integer, HiredMerchant> merchants = new HashMap<>();
    private final Map<Integer, HiredFishing> fishings = new HashMap<>();
    private final Map<Integer, PlayerNPC> playerNPCs = new HashMap<>();
    private final ReentrantReadWriteLock merchantLock = new ReentrantReadWriteLock(); //merchant
    private final ReentrantReadWriteLock fishingLock = new ReentrantReadWriteLock(); //fishing
    private int eventMap = -1;
    private final Map<MapleEventType, MapleEvent> events = new EnumMap<>(MapleEventType.class);

    private ChannelServer(final int channel)
    {
        this.channel = channel;
        this.mapFactory = new MapleMapFactory();
        this.mapFactory.setChannel(channel);
    }

    public static Set<Integer> getAllInstance()
    {
        return new HashSet<>(instances.keySet());
    }

    public static void startChannel()
    {
        serverStartTime = System.currentTimeMillis();

        for (int i = 1; i < ServerProperties.getInt("tms.Count", 0) + 1; i++) {
            new ChannelServer(i).runStartupConfigurations();
        }
    }

    private void runStartupConfigurations()
    {
        this.setChannel(this.channel); //instances.put

        try {
            this.serverMessage = ServerProperties.get("tms.ServerMessage");

            flags = Integer.parseInt(ServerProperties.getProperty("tms.WFlags", "0"));
            adminOnly = Boolean.parseBoolean(ServerProperties.getProperty("tms.Admin", "false"));
            eventSM = new EventScriptManager(this, ServerProperties.getProperty("tms.Events").split(","));
            port = Short.parseShort(ServerProperties.getProperty("tms.Port" + channel, String.valueOf(DEFAULT_PORT + channel)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ip = ServerProperties.getProperty("tms.IP") + ":" + port;

        ByteBuffer.setUseDirectBuffers(false);
        ByteBuffer.setAllocator(new SimpleByteBufferAllocator());

        acceptor = new SocketAcceptor();
        final SocketAcceptorConfig acceptor_config = new SocketAcceptorConfig();
        acceptor_config.getSessionConfig().setTcpNoDelay(true);
        acceptor_config.setDisconnectOnUnbind(true);
        acceptor_config.getFilterChain().addLast("codec", new ProtocolCodecFilter(new MapleCodecFactory()));
        players = new PlayerStorage(channel);
        loadEvents();

        try {
            this.serverHandler = new MapleServerHandler(channel, false);
            acceptor.bind(new InetSocketAddress(port), serverHandler, acceptor_config);
            System.out.println("Channel " + channel + " Started: Listening on port " + port + "");
            eventSM.init();
        } catch (IOException e) {
            System.out.println("Binding to port " + port + " failed (ch: " + getChannel() + ")" + e);
        }
    }

    private void loadEvents()
    {
        if (this.events.size() != 0) {
            return;
        }

        this.events.put(MapleEventType.CokePlay, new MapleCoconut(this.channel, MapleEventType.CokePlay.mapids));
        this.events.put(MapleEventType.Coconut, new MapleCoconut(this.channel, MapleEventType.Coconut.mapids));
        this.events.put(MapleEventType.Fitness, new MapleFitness(this.channel, MapleEventType.Fitness.mapids));
        this.events.put(MapleEventType.OlaOla, new MapleOla(this.channel, MapleEventType.OlaOla.mapids));
        this.events.put(MapleEventType.OxQuiz, new MapleOxQuiz(this.channel, MapleEventType.OxQuiz.mapids));
        this.events.put(MapleEventType.Snowball, new MapleSnowball(this.channel, MapleEventType.Snowball.mapids));
    }

    public final void shutdown()
    {
        if (this.finishedShutdown) {
            return;
        }

        broadcastPacket(MaplePacketCreator.serverNotice(0, "這個頻道正在關閉中."));
        // dc all clients by hand so we get sessionClosed...
        this.shutdown = true;

        System.out.println("Channel " + this.channel + ", Saving hired merchants...");

        this.closeAllMerchant();

        System.out.println("Channel " + this.channel + ", Saving hired fishings...");

        this.closeAllFishing();

        System.out.println("Channel " + this.channel + ", Saving characters...");

        this.getPlayerStorage().disconnectAll();

        System.out.println("Channel " + this.channel + ", Unbinding...");

        this.acceptor.unbindAll();
        this.acceptor = null;

        //temporary while we don not have !addChannel
        instances.remove(this.channel);

        LoginServer.removeChannel(this.channel);

        this.setFinishShutdown();
    }

    public final MapleMapFactory getMapFactory()
    {
        return this.mapFactory;
    }

    public static ChannelServer getInstance(final int channel)
    {
        return instances.get(channel);
    }

    public final PlayerStorage getPlayerStorage()
    {
        return this.players;
    }

    public final void addPlayer(final MapleCharacter chr)
    {
        this.getPlayerStorage().registerPlayer(chr);

        chr.getClient().getSession().write(MaplePacketCreator.serverMessage(this.serverMessage));
    }

    public final void removePlayer(final MapleCharacter chr)
    {
        this.getPlayerStorage().deregisterPlayer(chr);
    }

    public final void removePlayer(final int id, final String name)
    {
        this.getPlayerStorage().deregisterPlayer(id, name);
    }

    public final void setServerMessage(final String newMessage)
    {
        this.serverMessage = newMessage;
        broadcastPacket(MaplePacketCreator.serverMessage(this.serverMessage));
    }

    public final int getChannel()
    {
        return channel;
    }

    public final void setChannel(final int channel)
    {
        instances.put(channel, this);
        LoginServer.addChannel(channel);
    }

    public static Collection<ChannelServer> getAllInstances()
    {
        return Collections.unmodifiableCollection(instances.values());
    }

    /**
     * 新增釣魚精靈
     */
    public final int addFishing(final HiredFishing fishing)
    {
        try {
            this.fishingLock.writeLock().lock();

            this.fishings.put(this.runningFishings, fishing);

            return runningFishings++;
        } finally {
            this.fishingLock.writeLock().unlock();
        }
    }

    /**
     * 移除釣魚精靈
     */
    public final void removeFishing(final HiredFishing fishing)
    {
        try {
            this.fishingLock.writeLock().lock();

            this.fishings.remove(fishing.getStoreId());
        } finally {
            this.fishingLock.writeLock().unlock();
        }
    }

    /**
     * 頻道中是否有指定帳號的釣魚精靈
     */
    public final HiredFishing containsFishing(final int accId)
    {
        try {
            this.fishingLock.readLock().lock();

            for (HiredFishing fishing : this.fishings.values()) {
                if (fishing.getOwnerAccId() == accId) {
                    return fishing;
                }
            }

            return null;
        } finally {
            this.fishingLock.readLock().unlock();
        }
    }

    /**
     * 關閉頻道中所有釣魚精靈
     */
    private void closeAllFishing()
    {
        try {
            this.fishingLock.writeLock().lock();

            final Iterator<HiredFishing> fishings = this.fishings.values().iterator();

            while (fishings.hasNext()) {
                fishings.next().closeShop(true, false);
                fishings.remove();
            }
        } finally {
            this.fishingLock.writeLock().unlock();
        }
    }

    public Map<MapleSquadType, MapleSquad> getAllMapleSquads()
    {
        return Collections.unmodifiableMap(this.mapleSquads);
    }

    public final MapleSquad getMapleSquad(final String type)
    {
        return this.getMapleSquad(MapleSquadType.valueOf(type.toLowerCase()));
    }

    public final MapleSquad getMapleSquad(final MapleSquadType type)
    {
        return this.mapleSquads.get(type);
    }

    public final boolean addMapleSquad(final MapleSquad squad, final String type)
    {
        final MapleSquadType types = MapleSquadType.valueOf(type.toLowerCase());

        if (!this.mapleSquads.containsKey(types)) {
            this.mapleSquads.put(types, squad);
            squad.scheduleRemoval();
            return true;
        }

        return false;
    }

    public final void removeMapleSquad(final MapleSquadType types)
    {
        if (types != null && mapleSquads.containsKey(types)) {
            mapleSquads.remove(types);
        }
    }

    /**
     * 新增精靈商人
     */
    public final int addMerchant(final HiredMerchant merchant)
    {
        try {
            this.merchantLock.writeLock().lock();

            this.merchants.put(this.runningMerchants, merchant);

            return this.runningMerchants++;
        } finally {
            this.merchantLock.writeLock().unlock();
        }
    }

    /**
     * 移除精靈商人
     */
    public final void removeMerchant(final HiredMerchant merchant)
    {
        try {
            this.merchantLock.writeLock().lock();

            this.merchants.remove(merchant.getStoreId());
        } finally {
            this.merchantLock.writeLock().unlock();
        }
    }

    /**
     * 頻道中是否有指定帳號的精靈商人
     */
    public final boolean containsMerchant(final int accId)
    {
        try {
            this.merchantLock.readLock().lock();

            for (HiredMerchant merchant : this.merchants.values()) {
                if (merchant.getOwnerAccId() == accId) {
                    return true;
                }
            }

            return false;
        } finally {
            this.merchantLock.readLock().unlock();
        }
    }

    /**
     * 於精靈商人中搜尋物品
     */
    public final List<HiredMerchant> searchMerchant(final int item)
    {
        try {
            this.merchantLock.readLock().lock();

            final List<HiredMerchant> list = new LinkedList<>();

            for (HiredMerchant merchant : this.merchants.values()) {
                if (merchant.searchItem(item).size() > 0) {
                    list.add(merchant);
                }
            }

            return list;
        } finally {
            this.merchantLock.readLock().unlock();
        }
    }

    /**
     * 關閉頻道中所有精靈商人
     */
    private void closeAllMerchant()
    {
        try {
            this.merchantLock.writeLock().lock();

            final Iterator<HiredMerchant> merchants = this.merchants.values().iterator();

            while (merchants.hasNext()) {
                merchants.next().closeShop(true, false);
                merchants.remove();
            }
        } finally {
            this.merchantLock.writeLock().unlock();
        }
    }

    public final Collection<PlayerNPC> getAllPlayersNPC()
    {
        return this.playerNPCs.values();
    }

    public final void addPlayerNPC(final PlayerNPC npc)
    {
        if (this.playerNPCs.containsKey(npc.getId())) {
            this.removePlayerNPC(npc);
        }

        this.playerNPCs.put(npc.getId(), npc);
        this.getMapFactory().getMap(npc.getMapId()).addMapObject(npc);
    }

    public final void removePlayerNPC(final PlayerNPC npc)
    {
        if (this.playerNPCs.containsKey(npc.getId())) {
            this.playerNPCs.remove(npc.getId());
            this.getMapFactory().getMap(npc.getMapId()).removeMapObject(npc);
        }
    }

    public final void toggleMegaphoneMuteState()
    {
        this.MegaphoneMuteState = !this.MegaphoneMuteState;
    }

    public final boolean getMegaphoneMuteState()
    {
        return this.MegaphoneMuteState;
    }

    private void setFinishShutdown() {
        this.finishedShutdown = true;
        System.out.println("Channel " + channel + " has finished shutdown.");
    }

    public final boolean isAdminOnly() {
        return adminOnly;
    }

    public final boolean isShutdown()
    {
        return this.shutdown;
    }

    public final int getTempFlag() {
        return flags;
    }

    /**
     * 伺服器 IP
     */
    public final String getIP()
    {
        return ip;
    }

    /**
     * 頻道 Port
     */
    public final int getPort()
    {
        return this.port;
    }

    /**
     * 經驗倍率
     */
    public final int getExpRate()
    {
        return this.expRate;
    }

    /**
     * 經驗倍率
     */
    public final void setExpRate(final int expRate)
    {
        this.expRate = expRate;
    }

    /**
     * 楓幣倍率
     */
    public final int getMesoRate()
    {
        return this.mesoRate;
    }

    /**
     * 楓幣倍率
     */
    public final void setMesoRate(final int mesoRate)
    {
        this.mesoRate = mesoRate;
    }

    /**
     * 掉寶倍率
     */
    public final int getDropRate()
    {
        return this.dropRate;
    }

    /**
     * 掉寶倍率
     */
    public final void setDropRate(final int dropRate)
    {
        this.dropRate = dropRate;
    }

    /**
     * 點數倍率
     */
    public final int getCashRate()
    {
        return this.cashRate;
    }

    /**
     * 點數倍率
     */
    public final void setCashRate(final int cashRate)
    {
        this.cashRate = cashRate;
    }

    public final EventScriptManager getEventSM()
    {
        return this.eventSM;
    }

    public final void reloadEvents()
    {
        this.eventSM.cancel();
        this.eventSM = new EventScriptManager(this, ServerProperties.getProperty("tms.Events").split(","));
        this.eventSM.init();
    }

    public int getEvent()
    {
        return this.eventMap;
    }

    public MapleEvent getEvent(final MapleEventType t)
    {
        return this.events.get(t);
    }

    public final void setEvent(final int ze)
    {
        this.eventMap = ze;
    }

    public static Set<Integer> getChannelServer()
    {
        return new HashSet<>(instances.keySet());
    }

    public static int getChannelCount()
    {
        return instances.size();
    }

    public final MapleServerHandler getServerHandler()
    {
        return this.serverHandler;
    }

    public static Map<Integer, Integer> getChannelLoad()
    {
        Map<Integer, Integer> ret = new HashMap<>();

        for (ChannelServer cs : instances.values()) {
            ret.put(cs.getChannel(), cs.getConnectedClients());
        }

        return ret;
    }

    public int getConnectedClients()
    {
        return this.getPlayerStorage().getConnectedClients();
    }

    public List<CheaterData> getCheaters()
    {
        List<CheaterData> cheaters = this.getPlayerStorage().getCheaters();

        Collections.sort(cheaters);

        return CollectionUtil.copyFirst(cheaters, 20);
    }

    public void broadcastMessage(byte[] message)
    {
        this.broadcastPacket(new ByteArrayMaplePacket(message));
    }

    public void broadcastSmega(byte[] message)
    {
        this.broadcastSmegaPacket(new ByteArrayMaplePacket(message));
    }

    public void broadcastGMMessage(byte[] message)
    {
        this.broadcastGMPacket(new ByteArrayMaplePacket(message));
    }

    public final void broadcastPacket(final MaplePacket data)
    {
        this.getPlayerStorage().broadcastPacket(data);
    }

    private void broadcastSmegaPacket(final MaplePacket data)
    {
        this.getPlayerStorage().broadcastSmegaPacket(data);
    }

    private void broadcastGMPacket(final MaplePacket data)
    {
        this.getPlayerStorage().broadcastGMPacket(data);
    }

    public void saveAll() // used in auto save script
    {
        int ppl = 0;

        for (MapleCharacter chr : this.players.getAllCharacters()) {
            chr.saveToDB(false, false);

            ++ppl;
        }

        System.out.println("[自動存檔] 已經將頻道 " + this.channel + " 的 " + ppl + " 個玩家保存到數據中.");
    }
 }
