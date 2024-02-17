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

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class KosuzuUnderstandsEverything implements Listener {
    private final Logger logger;
    private final KosuzuTranslatesEverything translator;
    private final KosuzuRemembersEverything database;
    private final KosuzuKnowsWhereYouLive geolocation;

    private final ArrayList<Pattern> regexes = new ArrayList<>();
    private final Map<String, Map<UUID, Pattern>> placeholderRegexes = new HashMap<>();
    
    public KosuzuUnderstandsEverything(Kosuzu kosuzu) {
        logger = kosuzu.getLogger();
        translator = new KosuzuTranslatesEverything(kosuzu);
        database = kosuzu.database;
        geolocation = new KosuzuKnowsWhereYouLive(kosuzu);

        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(new PacketAdapter(kosuzu, ListenerPriority.NORMAL, PacketType.Play.Server.CHAT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                KosuzuUnderstandsEverything.this.onPacketSending(event, false);
            }
        });

        manager.addPacketListener(new PacketAdapter(kosuzu, ListenerPriority.NORMAL, PacketType.Play.Server.SYSTEM_CHAT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                KosuzuUnderstandsEverything.this.onPacketSending(event, true);
            }
        });

        prepareRegexes(kosuzu.getConfig());
    }

    private void prepareRegexes(@NotNull FileConfiguration config) {
        var regexes = config.getStringList("match.include");

        for (var regex : regexes) {
            if (regex.contains("%username%")) {
                placeholderRegexes.put(regex, new HashMap<>());
            } else {
                this.regexes.add(Pattern.compile(regex));
            }
        }

        logger.info("Prepared " + this.regexes.size() + " regexes");
    }

    // Called by ProtocolLib
    private void onPacketSending(PacketEvent event, boolean isSystem) {
        var player = event.getPlayer();
        var packet = event.getPacket();
        var message = packet.getChatComponents().read(0);
        var json = message.getJson();
        var component = JSONComponentSerializer.json().deserialize(json); // Adventure API from raw JSON
        var text = getTextMessage(component, isSystem, player);

        if (text != null) {
            var uuid = database.addMessage(json, text, player.getUniqueId());

            if (uuid != null) {
                var newComponent = component.hoverEvent(
                        Component
                            .text("Click to translate")
                            .color(NamedTextColor.GRAY)
                    )
                    .clickEvent(
                        ClickEvent.runCommand("/kosuzu translate " + uuid)
                    );

                var newJson = JSONComponentSerializer.json().serialize(newComponent);

                packet.getChatComponents().write(0, WrappedChatComponent.fromJson(newJson));
            }
        }
    }

    /**
     * Extracts the text message from a chat component
     * Also determines if we should translate the message
     * @param component The chat component created from the message
     * @param isSystem Whether the message is a system message
     * @param player The player who sent the message
     * @return The text message, or null if it could/should not be translated
     */
    private @Nullable String getTextMessage(Component component, boolean isSystem, Player player) {
        var text =  PlainTextComponentSerializer.plainText().serialize(component);

        if (!isSystem) {
            return text;
        }

        for (var pattern : regexes) {
            var matcher = pattern.matcher(text);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }

        for (var placeholder : placeholderRegexes.entrySet()) {
            var regex = placeholder.getKey();
            var cache = placeholder.getValue();

            var pattern = cache.computeIfAbsent(player.getUniqueId(), (key) -> Pattern.compile(regex.replace("%username%", player.getName())));
            var matcher = pattern.matcher(text);

            if (matcher.matches()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        var player = event.getPlayer();
        var uuid = player.getUniqueId();
        var name = player.getName();


        if (database.isNewUser(uuid, name)) {
            var country = geolocation.getCountryCode(player);
            if (Objects.equals(country, "CN")) country = "ZH"; // Special case for China
            if (Objects.equals(country, "TW")) country = "ZH"; // Special case for Taiwan
            if (Objects.equals(country, "HK")) country = "ZH"; // Special case for Hong Kong
            if (Objects.equals(country, "JP")) country = "JA"; // Special case for Japan
            if (Objects.equals(country, "GB")) country = "EN-GB"; // Special case for England
            if (Objects.equals(country, "US")) country = "EN-US"; // Special case for United States

            if (country != null) {
                // Extra searching for languages
                var languages = database.getLanguages();
                String finalCountry = country;
                country = languages.stream().map(KosuzuDatabaseModels.Language::getCode).filter(code -> code.toUpperCase().contains(finalCountry.toUpperCase())).findFirst().orElse(null);
            }

            try {
                database.setUserDefaultLanguage(uuid, country);
            } catch (Exception e) {
                logger.warning("Failed to set default language for " + name + " (" + uuid + ") to " + country + ": " + e.getMessage());
            }

            player.sendMessage(
                Kosuzu.HEADER
                    .append(Component.text(database.getTranslation("welcome.first", country).replace("%username%", name), NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text(database.getTranslation("welcome.second", country), NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text(database.getTranslation("welcome.third", country), NamedTextColor.GRAY))
            );
        }
    }

    @EventHandler
    public void onPlayerDisconnect(@NotNull PlayerQuitEvent event) {
        var player = event.getPlayer();

        // Remove from cache
        placeholderRegexes.values().forEach(map -> map.remove(player.getUniqueId()));
    }

    private void translateCallback(AsyncChatEvent event, Audience player) {
        // var ratelimit = RateLimiter.create(2000);

        var message = PlainTextComponentSerializer.plainText().serialize(event.message());

        var uuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
        if (player instanceof Player bukkitPlayer)
            uuid = bukkitPlayer.getUniqueId();

        var language = database.getUserDefaultLanguage(uuid);
        var translation = translator.translate(message, language);

        if (translation == null) {
            player.sendMessage(
                Kosuzu.HEADER.append(Component.text(database.getTranslation("translate.fail", language), NamedTextColor.RED))
            );

            return;
        }

        player.sendMessage(
            Component
                .text()
                .append(
                    Component
                        .text()
                        .content("[" + translation.detectedSourceLanguage + " -> " + language + "] ")
                        .color(NamedTextColor.GRAY)
                )

                .append(
                    Component
                        .text()
                        .content("<" + event.getPlayer().getName() + "> ")
                        .append(Component.text(translation.text))
                        .color(NamedTextColor.WHITE)
                )
                .append(
                    Component
                        .text()
                        .content(" (" + message + ")")
                        .decorate(TextDecoration.ITALIC)
                        .color(NamedTextColor.GRAY)
                )
                .build()
        );
    }
}
