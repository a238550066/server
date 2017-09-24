/*
 * TMS 113 scripting/EventScriptManager.java
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

import handling.channel.ChannelServer;
import tools.FileoutputUtil;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class EventScriptManager extends AbstractScriptManager
{
    private final Map<String, EventEntry> events = new LinkedHashMap<>();
    private final AtomicInteger runningInstanceMapId = new AtomicInteger(0);

    final int getNewInstanceMapId()
    {
        return runningInstanceMapId.addAndGet(1);
    }

    public EventScriptManager(final ChannelServer cserv, final String[] scripts)
    {
        super();

        for (final String script : scripts) {
            if (!script.equals("")) {
                final Invocable iv = getInvocable("event/" + script + ".js", null);

                if (iv != null) {
                    this.events.put(script, new EventEntry(script, iv, new EventManager(cserv, iv, script)));
                }
            }
        }
    }

    public final EventManager getEventManager(final String event)
    {
        final EventEntry entry = this.events.get(event);

        if (entry == null) {
            return null;
        }

        return entry.em;
    }

    public final void init()
    {
        for (final EventEntry entry : this.events.values()) {
            try {
                ((ScriptEngine) entry.iv).put("em", entry.em);

                entry.iv.invokeFunction("init", (Object) null);
            } catch (final Exception e) {
                final String msg = "Event " + entry.script + " 腳本錯誤 " + e;

                System.out.println(msg);
                FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, msg);
            }
        }
    }

    public final void cancel()
    {
        for (final EventEntry entry : this.events.values()) {
            entry.em.cancel();
        }
    }

    private static class EventEntry
    {
        public final String script;
        public final Invocable iv;
        public final EventManager em;

        EventEntry(final String script, final Invocable iv, final EventManager em)
        {
            this.script = script;
            this.iv = iv;
            this.em = em;
        }
    }
}
