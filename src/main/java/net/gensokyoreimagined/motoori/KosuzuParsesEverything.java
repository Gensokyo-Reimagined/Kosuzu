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
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class KosuzuParsesEverything {
    private final ArrayList<Pattern> regexes = new ArrayList<>();
    private final Map<String, Map<UUID, Pattern>> placeholderRegexes = new HashMap<>();
    private final List<String> syntaxBlacklist;

    public KosuzuParsesEverything(Kosuzu kosuzu) {
        var logger = kosuzu.getLogger();

        var config = kosuzu.config;
        var regexes = config.getStringList("match.include");

        for (var regex : regexes) {
            if (regex.contains("%username%")) {
                placeholderRegexes.put(regex, new HashMap<>());
            } else {
                this.regexes.add(Pattern.compile(regex));
            }
        }

        logger.info("Prepared " + this.regexes.size() + " regexes");

        syntaxBlacklist = config.getStringList("match.blacklist");

        logger.info("Added " + syntaxBlacklist.size() + " blacklist entries");
    }

    /**
     * Extracts the text message from a chat component
     * Also determines if we should translate the message
     * @param component The chat component created from the message
     * @param player The player who sent the message
     * @return The text message, or null if it could/should not be translated
     */
    public @Nullable String getTextMessage(Component component, Player player) {
        var text =  PlainTextComponentSerializer.plainText().serialize(component);

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

    /**
     * Removes unwanted chat syntax from the message, in case someone's trying to be a neerdowell.
     * Examples of syntax include chat prefixes and role prefixes.
     * @param message The message to modify.
     * @return The same message after modification
     */
    public Component removeUnwantedSyntax(Component message) {
        // only plain text components are of concern for now
        if (!(message instanceof TextComponent)) return message;

        for (var childComponent : message.children()) {
            if (!(childComponent instanceof TextComponent)) return message;
        }

        var messagePlaintext = (TextComponent) message;
        var componentContent = messagePlaintext.content();
        boolean matched;
        do {
            matched = false;
            for (var syntaxBlacklistString : syntaxBlacklist) {
                matched = matched || componentContent.indexOf(syntaxBlacklistString) != -1;
                componentContent = componentContent.replaceAll(syntaxBlacklistString, "");
            }
        } while (matched);
        message = messagePlaintext.content(componentContent);

        var currentChildren = message.children();
        var newChildren = new ArrayList<Component>(currentChildren.size());
        for (var childComponent : currentChildren) {
            newChildren.add(removeUnwantedSyntax(childComponent));
        }
        message.children(newChildren);

        return message;
    }
}
