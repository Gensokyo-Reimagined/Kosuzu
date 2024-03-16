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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class KosuzuParsesEverything {
    private final Logger logger;

    private final ArrayList<Pattern> regexes = new ArrayList<>();
    private final Map<String, Map<UUID, Pattern>> placeholderRegexes = new HashMap<>();
    private final List<String> syntaxBlacklist;

    public KosuzuParsesEverything(Kosuzu kosuzu) {
        logger = kosuzu.getLogger();

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
     * Removes unwanted chat syntax from raw text, in case someone's trying to be a neerdowell.
     * Examples of syntax include chat prefixes and role prefixes.
     * @param message The message to check as raw text, possibly JSON
     * @return The same message with unwanted syntax removed.
     */
    public String removeUnwantedSyntax(String message) {
        // first figure out which part or parts need to be replaced
        logger.info(message);
        try {
            var messageJson = JsonParser.parseString(message);
            if (messageJson.isJsonObject()) {
                // we need to find the actual text which could be displayed
                var messageJsonObject = messageJson.getAsJsonObject();
                if (removeUnwantedSyntax(messageJsonObject)) {
                    return messageJsonObject.toString();
                }
            }

            if (messageJson.isJsonArray()) {
                // ditto
                var messageJsonArray = messageJson.getAsJsonArray();
                if (removeUnwantedSyntax(messageJsonArray)) {
                    return messageJsonArray.toString();
                }
            }
        } catch (JsonSyntaxException dummy) {}

        // seems to be a normal string?
        for (var syntaxBlacklistString : syntaxBlacklist) {
            message = message.replaceAll(syntaxBlacklistString, "");
        }

        return message;
    }

    private boolean removeUnwantedSyntax(JsonObject message) {
        String rawJsonTextType = null;
        if (message.has("type")) {
            rawJsonTextType = message.get("type").getAsString();
        } else {
            // deduce the implied type
            if (message.has("text")) {
                rawJsonTextType = "text";
            } else if (message.has("translate")) {
                rawJsonTextType = "translatable";
            } else if (message.has("selector")) {
                rawJsonTextType = "selector";
            } else if (message.has("keybind")) {
                rawJsonTextType = "keybind";
            } else if (message.has("nbt")) {
                rawJsonTextType = "nbt";
            }
        }
        switch (rawJsonTextType) {
            case "text":
                if (!message.has("text")) return false;

                var text = message.get("text");
                if (!text.isJsonPrimitive()) return false;
                var textPrimitive = text.getAsJsonPrimitive();
                if (!textPrimitive.isString()) return false;

                var filteredText = removeUnwantedSyntax(textPrimitive.getAsString());
                message.addProperty("text", filteredText);
                break;
            case "translatable":
                if (!message.has("translate")) break;
                if (!message.has("with")) return false;

                var translationArguments = message.get("with");
                if (!translationArguments.isJsonArray()) return false;

                if (!removeUnwantedSyntax(translationArguments.getAsJsonArray())) return false;
                break;
            default:
                // it's okay if the type is unknown, but it should be warned
                if (rawJsonTextType != null) {
                    logger.warning("I don't recognize the raw JSON text type of " + rawJsonTextType + "! Could someone please teach me?");
                } else {
                    logger.warning("I don't recognize the implied type of this raw JSON text! Here, maybe someone knows?");
                    logger.warning(message.toString());
                }
                return false;
        }

        // check extra
        if (message.has("extra")) {
            var extraComponents = message.get("extra");
            if (!extraComponents.isJsonArray()) return false;
            if (!removeUnwantedSyntax(extraComponents.getAsJsonArray())) return false;
        }

        return true;
    }

    private boolean removeUnwantedSyntax(JsonArray message) {
        for (var i = 0; i < message.size(); ++i) {
            var messageElement = message.get(i);
            if (messageElement.isJsonObject()) {
                if (!removeUnwantedSyntax(messageElement.getAsJsonObject())) return false;

                continue;
            }

            if (!messageElement.isJsonPrimitive()) return false;
            var messageElementPrimitive = messageElement.getAsJsonPrimitive();
            if (!messageElementPrimitive.isString()) return false;

            var filteredElement = new JsonPrimitive(removeUnwantedSyntax(messageElementPrimitive.getAsString()));
            message.set(i, filteredElement);
        }

        return true;
    }
}
