/*
 * TMS 113 scripting/AbstractScriptManager.java
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
import tools.EncodingDetect;
import tools.FileoutputUtil;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

abstract class AbstractScriptManager
{
    private static final ScriptEngineManager sem = new ScriptEngineManager();

    Invocable getInvocable(String path, MapleClient c)
    {
        return getInvocable(path, c, false);
    }

    Invocable getInvocable(String path, MapleClient c, boolean npc)
    {
        try {
            path = "script/" + path;

            ScriptEngine engine = null;

            if (c != null) {
                engine = c.getScriptEngine(path);
            }

            if (engine != null) {
                if (npc) {
                    c.getPlayer().dropMessage(5,"角色狀態異常，請使用 @ea 來解除異常狀態");
                }
            } else {
                final File scriptFile = new File(path);

                if (!scriptFile.exists()) {
                    return null;
                }

                engine = sem.getEngineByName("nashorn");

                if (c != null) {
                    c.setScriptEngine(path, engine);
                }

                final BufferedReader buffer = new BufferedReader(
                    new InputStreamReader(
                        new FileInputStream(scriptFile),
                        EncodingDetect.getJavaEncode(scriptFile)
                    )
                );

                final String lines = "load('nashorn:mozilla_compat.js');" + buffer.lines().collect(Collectors.joining(System.lineSeparator()));

                engine.eval(lines);
            }

            return (Invocable) engine;
        } catch (Exception e) {
            final String msg = "Error executing script. Path: " + path + "\nException " + e;

            System.err.println(msg);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, msg);

            return null;
        }
    }
}
