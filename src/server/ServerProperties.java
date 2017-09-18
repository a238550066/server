package server;

import java.io.FileReader;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import database.DatabaseConnection;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 *
 * @author Emilyx3
 */
public class ServerProperties {

    private static final Properties props = new Properties();

    private static final String[] toLoad = {
        "config.ini"
    };

    private ServerProperties() {
    }

    static {
        for (String s : toLoad) {
            InputStreamReader fr;
            try {
                fr = new InputStreamReader(new FileInputStream(s), "UTF-8");
                props.load(fr);
                fr.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public static String get(String key)
    {
        return props.getProperty(key);
    }

    public static int getInt(String key)
    {
        return Integer.parseInt(props.getProperty(key));
    }

    public static int getInt(String key, int def)
    {
        String val = props.getProperty(key);

        if (val == null) {
            return def;
        }

        return Integer.parseInt(val);
    }

    public static boolean getBool(String key)
    {
        return Boolean.parseBoolean(props.getProperty(key));
    }

    public static String getProperty(String s) {
        return props.getProperty(s);
    }

    public static void setProperty(String prop, String newInf) {
        props.setProperty(prop, newInf);
    }

    public static String getProperty(String s, String def) {
        return props.getProperty(s, def);
    }
}
