/*
 * TMS 113 scripting/PortalScriptManager.java
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
import tools.EncodingDetect;
import tools.FileoutputUtil;

import javax.script.*;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class PortalScriptManager
{
    private final Map<String, PortalScript> scripts = new HashMap<>();
    private final static ScriptEngineFactory sef = new ScriptEngineManager().getEngineByName("nashorn").getFactory();
    private final static PortalScriptManager instance = new PortalScriptManager();

    public static PortalScriptManager getInstance()
    {
        return instance;
    }

    public final void executePortalScript(final MaplePortal portal, final MapleClient c)
    {
        final PortalScript script = this.getPortalScript(portal.getScriptName());

        if (script == null) {
            final String msg = "為處理的傳送點 " + portal.getScriptName() + " ，位於 " + c.getPlayer().getMapId();

            System.err.println(msg);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, msg);
        } else {
            try {
                script.enter(new PortalPlayerInteraction(c, portal));
            } catch (Exception e) {
                System.err.println("傳送點 " + portal.getScriptName() + " 腳本錯誤 " + e);
            }
        }
    }

    private PortalScript getPortalScript(final String scriptName)
    {
        if (this.scripts.containsKey(scriptName))
        {
            return this.scripts.get(scriptName);
        }

        final String scriptPath = "script/portal/" + scriptName + ".js";
        final File scriptFile = new File(scriptPath);

        if (!scriptFile.exists())
        {
            this.scripts.put(scriptName, null);
            return null;
        }


        final ScriptEngine portal = sef.getScriptEngine();

        try {
            final InputStream in = new FileInputStream(scriptFile);
            final BufferedReader buffer = new BufferedReader(new InputStreamReader(in, EncodingDetect.getJavaEncode(scriptFile)));
            final String lines = "load('nashorn:mozilla_compat.js');" + buffer.lines().collect(Collectors.joining(System.lineSeparator()));
            final CompiledScript compiled = ((Compilable) portal).compile(lines);

            compiled.eval();

            buffer.close();
            in.close();
        } catch (final Exception e) {
            final String msg = "傳送點 " + scriptName + " 腳本錯誤 " + e;

            System.err.println(msg);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, msg);
        }

        final PortalScript script = ((Invocable) portal).getInterface(PortalScript.class);

        this.scripts.put(scriptName, script);

        return script;
    }

    public final void clearScripts()
    {
        this.scripts.clear();
    }
}
