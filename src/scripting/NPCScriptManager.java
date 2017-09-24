/*
 * TMS 113 scripting/NPCScriptManager.java
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
import server.quests.MapleQuest;
import tools.FileoutputUtil;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Lock;

public class NPCScriptManager extends AbstractScriptManager
{
    private final Map<MapleClient, NPCConversationManager> cms = new WeakHashMap<>();
    private static final NPCScriptManager instance = new NPCScriptManager();

    public static NPCScriptManager getInstance()
    {
        return instance;
    }

    public final void start(final MapleClient c, final int npc)
    {
        final Lock lock = c.getNPCLock();

        try {
            lock.lock();

            if (c.getPlayer().isGM()) {
                c.getPlayer().dropMessage("[系統提示]您已經建立與NPC:" + npc + "的對話。");
            }

            if (this.cms.containsKey(c)) {
                c.getPlayer().dropMessage(1,"角色狀態異常，請使用 @ea 來解除異常狀態");
            } else {
                Invocable iv = this.getInvocable("npc/" + npc + ".js", c, true);

                if (iv == null) {
                    iv = this.getInvocable("npc/notcoded.js", c, true);

                    if (iv == null) {
                        this.dispose(c);
                        return;
                    }
                }

                final ScriptEngine scriptengine = (ScriptEngine) iv;
                final NPCConversationManager cm = new NPCConversationManager(c, npc, -1, (byte) -1, iv);

                this.cms.put(c, cm);

                scriptengine.put("cm", cm);
                scriptengine.put("npcid", npc);

                c.getPlayer().setConversation(1);

                try {
                    iv.invokeFunction("start"); // Temporary until I've removed all of start
                } catch (NoSuchMethodException e) {
                    iv.invokeFunction("action", (byte) 1, (byte) 0, 0);
                }
            }
        } catch (final Exception e) {
            this.logError(c, "NPC " + npc + " 腳本錯誤 " + e);
            this.dispose(c);
        } finally {
            lock.unlock();
        }
    }

    public final void action(final MapleClient c, final byte mode, final byte type, final int selection)
    {
        if (mode != -1) {
            final NPCConversationManager cm = this.cms.get(c);

            if (cm == null || cm.getLastMsg() > -1) {
                return;
            }

            final Lock lock = c.getNPCLock();

            try {
                lock.lock();

                if (cm.pendingDisposal) {
                    dispose(c);
                } else {
                    cm.getIv().invokeFunction("action", mode, type, selection);
                }
            } catch (final Exception e) {
                this.logError(c, "NPC " + cm.getNpc() + " 腳本錯誤 " + e);
                this.dispose(c);
            } finally {
                lock.unlock();
            }
        }
    }

    public final void startQuest(final MapleClient c, final int npcId, final int questId)
    {
        final MapleQuest quest = MapleQuest.getInstance(questId);

        if (quest == null || !quest.canStart(c.getPlayer(), null)) {
            return;
        }

        final Lock lock = c.getNPCLock();

        try {
            lock.lock();

            if (!this.cms.containsKey(c)) {
                final Invocable iv = getInvocable("quest/" + questId + ".js", c, true);

                if (iv == null) {
                    this.dispose(c);
                    return;
                }

                final ScriptEngine scriptengine = (ScriptEngine) iv;
                final NPCConversationManager cm = new NPCConversationManager(c, npcId, questId, (byte) 0, iv);

                this.cms.put(c, cm);

                scriptengine.put("qm", cm);

                c.getPlayer().setConversation(1);

                if (c.getPlayer().isGM()) {
                    c.getPlayer().dropMessage("[系統提示] 您已經建立與任務腳本：" + questId + " 的往來");
                }

                iv.invokeFunction("start", (byte) 1, (byte) 0, 0);
            }
        } catch (final Exception e) {
            this.logError(c, "任務 " + questId + " 腳本錯誤 " + e);
            this.dispose(c);
        } finally {
            lock.unlock();
        }
    }

    public final void startQuest(final MapleClient c, final byte mode, final byte type, final int selection)
    {
        final NPCConversationManager cm = this.cms.get(c);

        if (cm == null || cm.getLastMsg() > -1) {
            return;
        }

        final Lock lock = c.getNPCLock();

        try {
            lock.lock();

            if (cm.pendingDisposal) {
                dispose(c);
            } else {
                cm.getIv().invokeFunction("start", mode, type, selection);
            }
        } catch (Exception e) {
            this.logError(c, "任務 " + cm.getQuestId() + " 腳本錯誤 " + e);
            this.dispose(c);
        } finally {
            lock.unlock();
        }
    }

    public final void endQuest(final MapleClient c, final int npcId, final int questId, final boolean customEnd)
    {
        if (!customEnd) {
            return;
        }

        final MapleQuest quest = MapleQuest.getInstance(questId);

        if (quest == null || !quest.canComplete(c.getPlayer(), null)) {
            return;

        }

        final Lock lock = c.getNPCLock();

        try {
            lock.lock();

            if (!this.cms.containsKey(c)) {
                final Invocable iv = getInvocable("quest/" + questId + ".js", c, true);

                if (iv == null) {
                    this.dispose(c);
                    return;
                }

                final ScriptEngine scriptengine = (ScriptEngine) iv;
                final NPCConversationManager cm = new NPCConversationManager(c, npcId, questId, (byte) 1, iv);

                this.cms.put(c, cm);

                scriptengine.put("qm", cm);

                c.getPlayer().setConversation(1);

                iv.invokeFunction("end", (byte) 1, (byte) 0, 0);
            }
        } catch (Exception e) {
            this.logError(c, "任務 " + questId + " 腳本錯誤 " + e);
            this.dispose(c);
        } finally {
            lock.unlock();
        }
    }

    public final void endQuest(final MapleClient c, final byte mode, final byte type, final int selection)
    {
        final NPCConversationManager cm = this.cms.get(c);

        if (cm == null || cm.getLastMsg() > -1) {
            return;
        }

        final Lock lock = c.getNPCLock();

        try {
            lock.lock();

            if (cm.pendingDisposal) {
                this.dispose(c);
            } else {
                cm.getIv().invokeFunction("end", mode, type, selection);
            }
        } catch (Exception e) {
            this.logError(c, "任務 " + cm.getQuestId() + " 腳本錯誤 " + e);
            this.dispose(c);
        } finally {
            lock.unlock();
        }
    }

    public final void dispose(final MapleClient c)
    {
        final NPCConversationManager cm = cms.get(c);

        if (cm != null) {
            this.cms.remove(c);

            if (cm.getType() != -1) {
                c.removeScriptEngine("script/quest/" + cm.getQuestId() + ".js");
            } else {
                c.removeScriptEngine("script/npc/" + cm.getNpc() + ".js");
                c.removeScriptEngine("script/npc/notcoded.js");
            }
        }

        if (c.getPlayer() != null && c.getPlayer().getConversation() == 1) {
            c.getPlayer().setConversation(0);
        }
    }

    public final NPCConversationManager getCM(final MapleClient c)
    {
        return this.cms.get(c);
    }

    private void logError(final MapleClient c, final String msg)
    {
        if (c.getPlayer().isGM()) {
            c.getPlayer().dropMessage(msg);
        }

        System.err.println(msg);

        FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, msg);
    }
}
