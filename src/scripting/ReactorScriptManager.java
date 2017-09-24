/*
 * TMS 113 scripting/ReactorScriptManager.java
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
import database.DatabaseConnection;
import server.maps.MapleReactor;
import server.maps.ReactorDropEntry;
import tools.FileoutputUtil;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ReactorScriptManager extends AbstractScriptManager
{
    private final Map<Integer, List<ReactorDropEntry>> drops = new HashMap<>();
    private final static ReactorScriptManager instance = new ReactorScriptManager();

    public static ReactorScriptManager getInstance()
    {
        return instance;
    }

    public final void act(final MapleClient c, final MapleReactor reactor)
    {
        try {
            final Invocable iv = getInvocable("reactor/" + reactor.getReactorId() + ".js", c);

            if (iv == null) {
                return;
            }

            final ScriptEngine scriptengine = (ScriptEngine) iv;
            final ReactorActionManager rm = new ReactorActionManager(c, reactor);

            scriptengine.put("rm", rm);

            iv.invokeFunction("act");
        } catch (Exception e) {
            final String msg = "Reactor " + reactor.getReactorId() + " " + reactor.getName() + " 腳本錯誤 " + e;

            System.err.println(msg);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, msg);
        }
    }

    final List<ReactorDropEntry> getDrops(final int rid)
    {
        List<ReactorDropEntry> ret = this.drops.get(rid);

        if (ret != null) {
            return ret;
        }

        ret = new LinkedList<>();

        try {
            final Connection con = DatabaseConnection.getConnection();
            final PreparedStatement ps = con.prepareStatement("SELECT * FROM `reactordrops` WHERE `reactorid` = ?");

            ps.setInt(1, rid);

            final ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                ret.add(new ReactorDropEntry(
                    rs.getInt("itemid"),
                    rs.getInt("chance"),
                    rs.getInt("questid")
                ));
            }

            rs.close();
            ps.close();
        } catch (final SQLException e) {
            e.printStackTrace();
            return ret;
        }

        this.drops.put(rid, ret);

        return ret;
    }

    public final void clearDrops()
    {
        this.drops.clear();
    }
}
