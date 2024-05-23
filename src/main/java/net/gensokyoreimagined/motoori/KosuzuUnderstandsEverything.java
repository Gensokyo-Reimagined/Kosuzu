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

import io.papermc.paper.event.player.AsyncChatDecorateEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.logging.Logger;

public class KosuzuUnderstandsEverything implements Listener {
    private final Logger logger;
    private final KosuzuRemembersEverything database;
    private final KosuzuKnowsWhereYouLive geolocation;
    private final KosuzuParsesEverything parser;

    public KosuzuUnderstandsEverything(Kosuzu kosuzu) {
        logger = kosuzu.getLogger();
        database = kosuzu.database;
        geolocation = new KosuzuKnowsWhereYouLive(kosuzu);
        parser = new KosuzuParsesEverything(kosuzu);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onChatDecorateEarliest(AsyncChatDecorateEvent event) {
        var player = event.player();
        if (player == null) return;

        // filter early to adjust for following decorators
        var message = event.originalMessage();
        message = parser.removeUnwantedSyntax(message);
        // Don't make links clickable twice
        event.result(message);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onChatDecorateLatest(AsyncChatDecorateEvent event) {
        var player = event.player();
        if (player == null) return;

        // retrieve original filtered message
        var message = event.originalMessage();
        message = parser.removeUnwantedSyntax(message);
        message = parser.makeLinksClickable(message);

        var json = JSONComponentSerializer.json().serialize(event.result());
        var uuid = database.addMessage(json, PlainTextComponentSerializer.plainText().serialize(message));

        event.result(event.result().hoverEvent(
                Component
                    .text(database.getTranslation("translate.hover", database.getUserDefaultLanguage(player.getUniqueId())))
                    .color(NamedTextColor.GRAY)
            )
            .clickEvent(
                ClickEvent.runCommand("/kosuzu translate " + uuid.toString())
            ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        var player = event.getPlayer();
        var uuid = player.getUniqueId();
        var name = player.getName();


        if (database.isNewUser(uuid, name)) {
            var country = geolocation.getCountryCode(player);

            // Special cases for countries (yandere dev would be proud)

            if (Objects.equals(country, "CN")) country = "ZH"; // Special case for China
            else if (Objects.equals(country, "TW")) country = "ZH"; // Special case for Taiwan
            else if (Objects.equals(country, "HK")) country = "ZH"; // Special case for Hong Kong
            else if (Objects.equals(country, "JP")) country = "JA"; // Special case for Japan
            else if (Objects.equals(country, "GB")) country = "EN-GB"; // Special case for England
            else if (Objects.equals(country, "US")) country = "EN-US"; // Special case for United States
            else if (Objects.equals(country, "CA")) country = "EN-US"; // Special case for Canada
            else if (Objects.equals(country, "AU")) country = "EN-GB"; // Special case for Australia

            else if (Objects.equals(country, "ES")) country = "ES"; // Special case for Spain
            else if (Objects.equals(country, "MX")) country = "ES"; // Special case for Mexico
            else if (Objects.equals(country, "AR")) country = "ES"; // Special case for Argentina
            else if (Objects.equals(country, "CL")) country = "ES"; // Special case for Chile
            else if (Objects.equals(country, "CO")) country = "ES"; // Special case for Colombia
            else if (Objects.equals(country, "PE")) country = "ES"; // Special case for Peru
            else if (Objects.equals(country, "VE")) country = "ES"; // Special case for Venezuela
            else if (Objects.equals(country, "EC")) country = "ES"; // Special case for Ecuador
            else if (Objects.equals(country, "GT")) country = "ES"; // Special case for Guatemala
            else if (Objects.equals(country, "CU")) country = "ES"; // Special case for Cuba
            else if (Objects.equals(country, "BO")) country = "ES"; // Special case for Bolivia
            else if (Objects.equals(country, "DO")) country = "ES"; // Special case for Dominican Republic
            else if (Objects.equals(country, "HN")) country = "ES"; // Special case for Honduras

            else if (Objects.equals(country, "BR")) country = "PT-BR"; // Special case for Brazil
            else if (Objects.equals(country, "PT")) country = "PT-PT"; // Special case for Portugal

            if (country != null) {
                // Extra searching for languages
                var languages = database.getLanguages();
                String finalCountry = country;
                country = languages.stream().map(KosuzuDatabaseModels.Language::code).filter(code -> code.toUpperCase().contains(finalCountry.toUpperCase())).findFirst().orElse(null);
            }

            if (country != null) {
                try {
                    database.setUserDefaultLanguage(uuid, country);
                } catch (Exception e) {
                    logger.warning("Failed to set default language for " + name + " (" + uuid + ") to " + country + ": " + e.getMessage());
                }
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
}
