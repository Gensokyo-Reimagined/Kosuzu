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
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

public class KosuzuParsesEverything {
    private final ArrayList<Pattern> regexes = new ArrayList<>();
    private final Map<String, Map<UUID, Pattern>> placeholderRegexes = new HashMap<>();

    private final List<TextReplacementConfig> syntaxReplacementConfigs;

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

        List<String> syntaxBlacklist = config.getStringList("match.blacklist");

        var replacementConfigs = new ArrayList<TextReplacementConfig>();

        for (var syntax : syntaxBlacklist) {
            replacementConfigs.add(TextReplacementConfig.builder().matchLiteral(syntax).replacement("").build());
        }

        syntaxReplacementConfigs = Collections.unmodifiableList(replacementConfigs);

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
        for (var replacementConfig : syntaxReplacementConfigs) {
            message = message.replaceText(replacementConfig);
        }

        return message;
    }

    private final String URL_REGEX = "\\bhttps?://[0-9a-zA-Z](?:[-.\\w]*[0-9a-zA-Z])*(?::(0-9)*)*(/?)(?:[a-zA-Z0-9\\-.?,'/\\\\+&amp;%$#_]*)?\\b";
    private final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);

    public Component makeLinksClickable(Component message) {
        final var urlReplacement = TextReplacementConfig.builder().match(URL_PATTERN).replacement((match) -> {
            var url = match.content();
            return Component.text(url).clickEvent(ClickEvent.openUrl(url));
        }).build();

        return message.replaceText(urlReplacement);
    }
}
