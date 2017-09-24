/*
 * TMS 113 scripting/ReactorActionManager.java
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
package scripting;

import client.MapleClient;
import client.inventory.Equip;
import client.inventory.IItem;
import client.inventory.Item;
import client.inventory.MapleInventoryType;
import constants.GameConstants;
import handling.channel.ChannelServer;
import server.MapleCarnivalFactory;
import server.MapleItemInformationProvider;
import server.Randomizer;
import server.life.MapleMonster;
import server.maps.MapleReactor;
import server.maps.ReactorDropEntry;

import java.awt.*;
import java.util.LinkedList;
import java.util.List;

public class ReactorActionManager extends AbstractPlayerInteraction
{
    private final MapleReactor reactor;

    ReactorActionManager(final MapleClient c, final MapleReactor reactor)
    {
        super(c);

        this.reactor = reactor;
    }

    // only used for meso = false, really. No minItems because meso is used to fill the gap
    public final void dropItems()
    {
        this.dropItems(false, 0, 0, 0, 0);
    }

    public final void dropItems(final boolean meso, final int mesoChance, final int minMeso, final int maxMeso)
    {
        this.dropItems(meso, mesoChance, minMeso, maxMeso, 0);
    }

    public final void dropItems(final boolean meso, final int mesoChance, final int minMeso, final int maxMeso, final int minItems)
    {
        final List<ReactorDropEntry> chances = ReactorScriptManager.getInstance().getDrops(this.reactor.getReactorId());
        final List<ReactorDropEntry> items = new LinkedList<>();

        if (meso) {
            if (Math.random() < (1 / (double) mesoChance)) {
                items.add(new ReactorDropEntry(0, mesoChance, -1));
            }
        }

        int numItems = 0;

        for (final ReactorDropEntry entry : chances) {
            if (Math.random() < (1 / (double) entry.chance) && (entry.questid <= 0 || this.getPlayer().getQuestStatus(entry.questid) == 1)) {
                items.add(entry);
                numItems++;
            }
        }

        // if a minimum number of drops is required, add meso
        while (items.size() < minItems)
        {
            items.add(new ReactorDropEntry(0, mesoChance, -1));
            numItems++;
        }

        final Point dropPos = this.reactor.getPosition();

        dropPos.x -= (12 * numItems);

        int range, mesoDrop;
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();

        for (final ReactorDropEntry entry : items)
        {
            if (entry.itemId == 0) {
                range = maxMeso - minMeso;
                mesoDrop = Randomizer.nextInt(range) + minMeso * ChannelServer.getInstance(this.getClient().getChannel()).getMesoRate();
                this.reactor.getMap().spawnMesoDrop(mesoDrop, dropPos, this.reactor, this.getPlayer(), false, (byte) 0);
            } else {
                final IItem drop;

                if (GameConstants.getInventoryType(entry.itemId) != MapleInventoryType.EQUIP) {
                    drop = new Item(entry.itemId, (byte) 0, (short) 1, (byte) 0);
                } else {
                    drop = ii.randomizeStats((Equip) ii.getEquipById(entry.itemId));
                }

                this.reactor.getMap().spawnItemDrop(this.reactor, this.getPlayer(), drop, dropPos, false, false);
            }

            dropPos.x += 25;
        }
    }

    @Override
    public final void spawnNpc(final int npcId)
    {
        this.spawnNpc(npcId, this.getPosition());
    }

    // returns slightly above the reactor's position for monster spawns
    public final Point getPosition()
    {
        final Point pos = this.reactor.getPosition();

        pos.y -= 10;

        return pos;
    }

    public final MapleReactor getReactor()
    {
        return this.reactor;
    }

    public final void spawnZakum()
    {
        this.reactor.getMap().spawnZakum(this.getPosition().x, this.getPosition().y);
    }

    public final void killMonster(final int monsId)
    {
        this.reactor.getMap().killMonster(monsId);
    }

    @Override
    public final void spawnMonster(final int mobId)
    {
        this.spawnMonster(mobId, 1, this.getPosition());
    }

    @Override
    public final void spawnMonster(final int mobId, final int quantity)
    {
        this.spawnMonster(mobId, quantity, this.getPosition());
    }

    public final void dispelAllMonsters(final int num)
    {
        final MapleCarnivalFactory.MCSkill skill = MapleCarnivalFactory.getInstance().getGuardian(num);

        if (skill != null) {
            for (final MapleMonster mons : this.getMap().getAllMonstersThreadsafe()) {
                mons.dispelSkill(skill.getSkill());
            }
        }
    }
}
