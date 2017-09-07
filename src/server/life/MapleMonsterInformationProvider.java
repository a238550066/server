/*
 This file is part of the OdinMS Maple Story Server
 Copyright (C) 2008 ~ 2010 Patrick Huy <patrick.huy@frz.cc> 
 Matthias Butz <matze@odinms.de>
 Jan Christian Meyer <vimes@odinms.de>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License version 3
 as published by the Free Software Foundation. You may not use, modify
 or distribute this program under any other version of the
 GNU Affero General Public License.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package server.life;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import database.DatabaseConnection;

public class MapleMonsterInformationProvider
{
    private static final MapleMonsterInformationProvider instance = new MapleMonsterInformationProvider();

    private final Map<Integer, List<MonsterDropEntry>> drops = new HashMap<>();
    private final List<MonsterGlobalDropEntry> globalDrops = new ArrayList<>();

    private MapleMonsterInformationProvider()
    {
        this.retrieveGlobal();
    }

    public static MapleMonsterInformationProvider getInstance()
    {
        return instance;
    }

    public final List<MonsterGlobalDropEntry> getGlobalDrop()
    {
        return globalDrops;
    }

    private void retrieveGlobal()
    {
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = DatabaseConnection.getConnection().prepareStatement("SELECT * FROM drop_data_global WHERE chance > 0");

            rs = ps.executeQuery();

            while (rs.next()) {
                this.globalDrops.add(
                    new MonsterGlobalDropEntry(
                        rs.getInt("item_id"),
                        rs.getInt("chance"),
                        rs.getInt("continent"),
                        rs.getByte("drop_type"),
                        rs.getInt("minimum_quantity"),
                        rs.getInt("maximum_quantity"),
                        rs.getShort("quest_id")
                    )
                );
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving drop" + e);
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }

                if (rs != null) {
                    rs.close();
                }
            } catch (SQLException ignore) {
            }
        }
    }

    public final List<MonsterDropEntry> retrieveDrop(final int monsterId)
    {
        if (this.drops.containsKey(monsterId)) {
            return this.drops.get(monsterId);
        }

        final List<MonsterDropEntry> ret = new LinkedList<>();

        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            ps = DatabaseConnection.getConnection().prepareStatement("SELECT * FROM drop_data WHERE dropper_id = ?");

            ps.setInt(1, monsterId);

            rs = ps.executeQuery();

            while (rs.next()) {
                ret.add(
                    new MonsterDropEntry(
                        rs.getInt("item_id"),
                        rs.getInt("chance"),
                        rs.getInt("minimum_quantity"),
                        rs.getInt("maximum_quantity"),
                        rs.getShort("quest_id")
                    )
                );
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving drop" + e);

            return ret;
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }

                if (rs != null) {
                    rs.close();
                }
            } catch (SQLException ignore) {
            }
        }

        this.drops.put(monsterId, ret);

        return ret;
    }

    public final void clearDrops()
    {
        this.drops.clear();
        this.globalDrops.clear();

        this.retrieveGlobal();
    }
}
