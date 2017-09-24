/*
 * TMS 113 scripting/PortalPlayerInteraction.java
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
import server.MaplePortal;

public class PortalPlayerInteraction extends AbstractPlayerInteraction
{
    private final MaplePortal portal;

    PortalPlayerInteraction(final MapleClient c, final MaplePortal portal)
    {
        super(c);

        this.portal = portal;
    }

    public final MaplePortal getPortal()
    {
        return this.portal;
    }

    public final void inFreeMarket()
    {
        if (getPlayer().getLevel() < 10) {
            this.playerMessage(5, "你需要10級才可以進入自由市場");
        } else {
            this.saveLocation("FREE_MARKET");
            this.playPortalSE();
            this.warp(910000000, "st00");
        }
    }

    @Override
    public final void spawnMonster(final int mobId)
    {
        this.spawnMonster(mobId, 1, this.portal.getPosition());
    }

    @Override
    public final void spawnMonster(final int mobId, final int quantity)
    {
        this.spawnMonster(mobId, quantity, this.portal.getPosition());
    }
}
