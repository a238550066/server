/*
 * TMS 113 server/maps/EventDojoAgent.java
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
package server.maps;

import java.awt.Point;

import client.MapleCharacter;
import handling.channel.ChannelServer;
import handling.world.MaplePartyCharacter;
import server.Randomizer;
import server.Timer.MapTimer;
import server.life.MapleLifeFactory;
import tools.MaplePacketCreator;

public class EventDojoAgent
{
    /**
     * 開始武陵道場挑戰
     */
    public static boolean start(final MapleCharacter c, final boolean party)
    {
        final ChannelServer ch = c.getClient().getChannelServer();

        final int stage = 1;

        int mapId = 925020000 + (stage * 100);

        boolean canEnter = false;

        for (int x = 0; x < 15; x++) { //15 maps each stage
            boolean isClear = true;

            for (int i = 1; i < 39; i++) { //only 32 stages, but 38 maps
                final MapleMap map = ch.getMapFactory().getMap(925020000 + 100 * i + x);

                if (map.getCharactersSize() > 0) {
                    isClear = false;
                    break;
                }

                clearMap(map, false);
            }

            if (isClear) {
                canEnter = true;
                mapId += x;
                break;
            }
        }

        final MapleMap nextMap = ch.getMapFactory().getMap(mapId);

        if (canEnter) {
            if (!party || c.getParty() == null) {
                c.changeMap(nextMap, nextMap.getPortal(0));
            } else {
                final MapleMap currentMap = c.getMap();

                for (MaplePartyCharacter mem : c.getParty().getMembers()) {
                    MapleCharacter chr = currentMap.getCharacterById(mem.getId());

                    if (chr != null) {
                        chr.changeMap(nextMap, nextMap.getPortal(0));
                    }
                }
            }

            spawnMonster(nextMap, stage);
        }

        return canEnter;
    }

    /**
     * 傳送至下一關武陵道場
     *
     * Resting Rooms：
     * 925020600 ~ 925020609
     * 925021200 ~ 925021209
     * 925021800 ~ 925021809
     * 925022400 ~ 925022409
     * 925023000 ~ 925023009
     * 925023600 ~ 925023609
     */
    public static boolean next(final MapleCharacter c, final boolean fromResting, final MapleMap currentMap)
    {
        try {
            final ChannelServer ch = c.getClient().getChannelServer();

            final MapleMap deadMap = ch.getMapFactory().getMap(925020002);

            final int currentStage = ((currentMap.getId() - 925000000) / 100) - (((currentMap.getId() - 925000000) / 10000) * 100);
            final int points = getPoints(currentStage);

            if (!c.isAlive()) { //shouldn't happen
                c.changeMap(deadMap, deadMap.getPortal(0));
                return true;
            }

            final MapleMap map = ch.getMapFactory().getMap(currentMap.getId() + 100);

            if (!fromResting && map != null ) {
                clearMap(currentMap, true);

                if (c.getParty() == null || c.getParty().getMembers().size() == 1) {
                    final int point = (points + 1) * 5;

                    c.setDojo(c.getDojo() + point);
                    c.getClient().getSession().write(MaplePacketCreator.Mulung_Pts(point, c.getDojo()));
                } else {
                    final int point = (points) * 5;

                    for (MaplePartyCharacter mem : c.getParty().getMembers()) {
                        MapleCharacter chr = currentMap.getCharacterById(mem.getId());

                        if (chr != null) {
                            chr.setDojo(chr.getDojo() + point);
                            chr.getClient().getSession().write(MaplePacketCreator.Mulung_Pts(point, chr.getDojo()));
                        }
                    }
                }

            }

            if (currentMap.getId() >= 925023800 && currentMap.getId() <= 925023814) {
                final MapleMap lastMap = ch.getMapFactory().getMap(925020003);

                if (c.getParty() == null || c.getParty().getMembers().size() == 1) {
                    c.changeMap(lastMap, lastMap.getPortal(1));
                } else {
                    for (MaplePartyCharacter mem : c.getParty().getMembers()) {
                        MapleCharacter chr = currentMap.getCharacterById(mem.getId());

                        if (chr != null) {
                            if (!chr.isAlive()) {
                                chr.addHP(50);
                            }

                            chr.changeMap(lastMap, lastMap.getPortal(1));
                        }
                    }
                }

                return true;
            }
            
            if (map != null) {
                clearMap(map, false);

                if (c.getParty() == null) {
                    c.changeMap(map, map.getPortal(0));
                } else {
                    for (MaplePartyCharacter mem : c.getParty().getMembers()) {
                        MapleCharacter chr = currentMap.getCharacterById(mem.getId());

                        if (chr != null) {
                            if (!chr.isAlive()) {
                                chr.addHP(50);
                            }

                            chr.changeMap(map, map.getPortal(0));
                        }
                    }
                }

                spawnMonster(map, currentStage + 1);

                return true;
            }

            final MapleMap errorMap = ch.getMapFactory().getMap(925020001);

            if (c.getParty() == null) {
                c.dropMessage(5, "發生未知的錯誤，傳送至入口處");
                c.changeMap(errorMap, errorMap.getPortal(0));
            } else {
                for (MaplePartyCharacter mem : c.getParty().getMembers()) {
                    MapleCharacter chr = currentMap.getCharacterById(mem.getId());

                    if (chr != null) {
                        chr.dropMessage(5, "發生未知的錯誤，傳送至入口處");
                        chr.changeMap(errorMap, errorMap.getPortal(0));
                    }
                }
            }
        } catch (Exception rm) {
            rm.printStackTrace();
        }

        return false;
    }

    /**
     * 重置地圖
     */
    private static void clearMap(final MapleMap map, final boolean check)
    {
        if (check) {
            if (map.getCharactersSize() > 0) {
                return;
            }
        }

        map.resetFully();
    }

    /**
     * 關卡點數
     */
    private static int getPoints(final int stage)
    {
        switch (stage) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return 1;
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
                return 1;
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
                return 2;
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
                return 2;
            case 25:
            case 26:
            case 27:
            case 28:
            case 29:
                return 3;
            case 31:
            case 32:
            case 33:
            case 34:
            case 35:
                return 3;
            case 37:
            case 38:
                return 4;
            default:
                return 0;
        }
    }

    /**
     * 關卡怪物
     */
    private static void spawnMonster(final MapleMap map, final int stage)
    {
        final int mobId;

        switch (stage) {
            case 1:
                mobId = 9300184; // 紅寶王
                break;
            case 2:
                mobId = 9300185; // 樹妖王
                break;
            case 3:
                mobId = 9300186; // 仙人長老
                break;
            case 4:
                mobId = 9300187; // 超級綠水靈
                break;
            case 5:
                mobId = 9300188; // 蜈蚣大王
                break;
            case 7:
                mobId = 9300189; // 殭屍猴王
                break;
            case 8:
                mobId = 9300190; // 巨居蟹
                break;
            case 9:
                mobId = 9300191; // 蘑菇王
                break;
            case 10:
                mobId = 9300192; // 巨型戰鬥機
                break;
            case 11:
                mobId = 9300193; // 菇菇鐘
                break;
            case 13:
                mobId = 9300194; // 沼澤巨鱷
                break;
            case 14:
                mobId = 9300195; // 精靈老爹
                break;
            case 15:
                mobId = 9300196; // 殭屍蘑菇王
                break;
            case 16:
                mobId = 9300197; // 葛雷金剛
                break;
            case 17:
                mobId = 9300198; // 金勾海賊王
                break;
            case 19:
                mobId = 9300199; // 九尾妖狐
                break;
            case 20:
                mobId = 9300200; // 肯德熊
                break;
            case 21:
                mobId = 9300201; // 毒石巨人
                break;
            case 22:
                mobId = 9300202; // 喵怪仙人
                break;
            case 23:
                mobId = 9300203; // 巴洛古
                break;
            case 25:
                mobId = 9300204; // 艾利傑
                break;
            case 26:
                mobId = 9300205; // 法郎肯洛伊德
                break;
            case 27:
                mobId = 9300206; // 奇美拉
                break;
            case 28:
                mobId = 9300207; // 黑輪王
                break;
            case 29:
                mobId = 9300208; // 雪毛怪人
                break;
            case 31:
                mobId = 9300209; // 藍色蘑菇王
                break;
            case 32:
                mobId = 9300210; // 地獄巴洛古
                break;
            case 33:
                mobId = 9300211; // 噴火龍
                break;
            case 34:
                mobId = 9300212; // 格瑞芬多
                break;
            case 35:
                mobId = 9300213; // 寒霜冰龍
                break;
            case 37:
                mobId = 9300214; // 拉圖斯
                break;
            case 38:
                mobId = 9300215; // 武公
                break;
            default:
                return;
        }

        final int rand = Randomizer.nextInt(3);

        final Point point = new Point(rand == 0 ? 140 : (rand == 1 ? -193 : 355), 0);

        MapTimer.getInstance().schedule(() -> map.spawnMonsterWithEffect(MapleLifeFactory.getMonster(mobId), 15, point), 2500);
    }
}
