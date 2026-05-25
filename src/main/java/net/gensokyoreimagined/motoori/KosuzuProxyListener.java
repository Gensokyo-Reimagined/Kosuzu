// Kosuzu Copyright (C) 2024 Gensokyo Reimagined
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

package net.gensokyoreimagined.motoori;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Listens to the {@code chatty:proxy} plugin channel and ingests cross-server
 * chat messages into the local Kosuzu database so that {@code /kosuzu translate
 * <uuid>} works when a player clicks a message that originated on another
 * backend.
 *
 * <p>Wire format (defined by chatty-velocity / GensouCoreCore's ChatProxyBridge):
 * gson-serialized {@code Component.textOfChildren(senderName, channelId,
 * message, simpleMessage)} where {@code message} carries the
 * {@code clickEvent("/kosuzu translate <uuid>")} that Kosuzu attached during
 * {@code AsyncChatDecorateEvent} on the origin server.
 */
public class KosuzuProxyListener implements PluginMessageListener {
    public static final String CHANNEL = "chatty:proxy";
    private static final String CLICK_PREFIX = "/kosuzu translate ";

    private final KosuzuRemembersEverything database;
    private final Logger logger;

    public KosuzuProxyListener(Kosuzu kosuzu) {
        this.database = kosuzu.database;
        this.logger = kosuzu.getLogger();
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player carrier, byte @NotNull [] bytes) {
        if (!CHANNEL.equals(channel)) return;

        Component decoded;
        try {
            decoded = GsonComponentSerializer.gson().deserialize(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            logger.warning("Failed to decode proxied chat component: " + ex.getMessage());
            return;
        }

        List<Component> children = decoded.children();
        if (children.size() < 3) return;
        Component messageComponent = children.get(2);

        Component subtree = findKosuzuClickSubtree(messageComponent);
        if (subtree == null) return;

        ClickEvent click = subtree.clickEvent();
        if (click == null) return;
        String value = click.value();
        if (value == null || !value.startsWith(CLICK_PREFIX)) return;

        UUID lookupUuid;
        try {
            lookupUuid = UUID.fromString(value.substring(CLICK_PREFIX.length()).trim());
        } catch (IllegalArgumentException ex) {
            return;
        }

        // Strip the click/hover so the stored JSON mirrors what the origin
        // server stored at AsyncChatDecorateEvent time (pre-decoration).
        Component clean = subtree.clickEvent(null).hoverEvent(null);
        String json = JSONComponentSerializer.json().serialize(clean);
        String text = PlainTextComponentSerializer.plainText().serialize(clean);
        if (text.isEmpty()) return;

        database.ingestRemoteMessage(lookupUuid, json, text);
    }

    private @Nullable Component findKosuzuClickSubtree(Component component) {
        ClickEvent click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.RUN_COMMAND) {
            String value = click.value();
            if (value != null && value.startsWith(CLICK_PREFIX)) {
                return component;
            }
        }
        for (Component child : component.children()) {
            Component found = findKosuzuClickSubtree(child);
            if (found != null) return found;
        }
        return null;
    }
}
