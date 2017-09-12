/*
 * TMS 113 database/DatabaseConnection.java
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
package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Properties;
import server.ServerProperties;

/**
 * All OdinMS servers maintain a Database Connection. This class therefore
 * "singletonices" the connection per process.
 *
 *
 * @author Frz
 */
public class DatabaseConnection
{
    private static ThreadLocal<Connection> con = new ThreadLocalConnection();

    public static final int CLOSE_CURRENT_RESULT = 1;

    /**
     * The constant indicating that the current <code>ResultSet</code> object
     * should not be closed when calling <code>getMoreResults</code>.
     *
     * @since 1.4
     */
    public static final int KEEP_CURRENT_RESULT = 2;

    /**
     * The constant indicating that all <code>ResultSet</code> objects that have
     * previously been kept open should be closed when calling
     * <code>getMoreResults</code>.
     *
     * @since 1.4
     */
    public static final int CLOSE_ALL_RESULTS = 3;

    /**
     * The constant indicating that a batch statement executed successfully but
     * that no count of the number of rows it affected is available.
     *
     * @since 1.4
     */
    public static final int SUCCESS_NO_INFO = -2;

    /**
     * The constant indicating that an error occured while executing a batch
     * statement.
     *
     * @since 1.4
     */
    public static final int EXECUTE_FAILED = -3;

    /**
     * The constant indicating that generated keys should be made available for
     * retrieval.
     *
     * @since 1.4
     */
    public static final int RETURN_GENERATED_KEYS = 1;

    /**
     * The constant indicating that generated keys should not be made available
     * for retrieval.
     *
     * @since 1.4
     */
    public static final int NO_GENERATED_KEYS = 2;

    public static Connection getConnection()
    {
        final Connection _con = con.get();

        try {
            // check the connection is valid or not
            if (_con.isValid(1)) {
                return _con;
            }

            // close the connection
            _con.close();

            // remove the connection from collection
            ThreadLocalConnection.allConnections.remove(_con);
        } catch (SQLException ignored) {
        }

        // create and return a new connection
        con = new ThreadLocalConnection();

        return con.get();
    }

    public static void closeAll() throws SQLException
    {
        for (final Connection con : ThreadLocalConnection.allConnections) {
            con.close();
        }
    }

    private static final class ThreadLocalConnection extends ThreadLocal<Connection>
    {
        static final Collection<Connection> allConnections = new LinkedList<>();

        @Override
        protected final Connection initialValue()
        {
            try {
                Class.forName("com.mysql.jdbc.Driver"); // touch the mysql driver
            } catch (final ClassNotFoundException e) {
                System.err.println("ERROR" + e);
            }

            try {
                Properties props = new Properties();

                props.put("user", ServerProperties.getProperty("tms.User"));
                props.put("password", ServerProperties.getProperty("tms.Pass"));
                props.put("useUnicode", "yes");
                props.put("characterEncoding", "BIG5");

                final Connection con = DriverManager.getConnection(ServerProperties.getProperty("tms.Url"), props);

                allConnections.add(con);

                return con;
            } catch (SQLException e) {
                System.err.println("ERROR" + e);

                return null;
            }
        }
    }
}
