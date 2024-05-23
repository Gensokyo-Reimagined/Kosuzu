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

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public class KosuzuDatabaseModels {
    public record Language(String code, String nativeName, String englishName) {}

    public static class Translation {
        private final String messageJson;
        private final UUID originalTextMessageId;
        private @Nullable String originalTextLanguageCode;
        private final String originalTextMessage;
        private final String requestedLanguageCode;
        private @Nullable String translatedTextMessage;

        public Translation(String messageJson, UUID originalTextMessageId, @Nullable String originalTextLanguageCode, String originalTextMessage, @Nullable String translatedTextMessage, String requestedLanguageCode) {
            this.messageJson = messageJson;
            this.originalTextMessageId = originalTextMessageId;
            this.originalTextLanguageCode = originalTextLanguageCode;
            this.originalTextMessage = originalTextMessage;
            this.translatedTextMessage = translatedTextMessage;
            this.requestedLanguageCode = requestedLanguageCode;
        }

        public String getMessageJson() { return messageJson; }
        public @Nullable String getOriginalTextLanguageCode() { return originalTextLanguageCode; }
        public String getOriginalTextMessage() { return originalTextMessage; }
        public String getTranslatedTextLanguageCode() { return requestedLanguageCode; }

        public @Nullable String getTranslatedTextMessage() { return translatedTextMessage; }

        public void loadTranslatedTextMessage(KosuzuTranslatesEverything translator, KosuzuRemembersEverything database) {
            if (originalTextLanguageCode != null && translatedTextMessage != null) {
                return;
            }

            var translation = translator.translate(originalTextMessage, requestedLanguageCode);
            originalTextLanguageCode = translation.detectedSourceLanguage;
            translatedTextMessage = translation.text;
            database.addTranslation(originalTextMessageId, translation.text, requestedLanguageCode, translation.detectedSourceLanguage);
        }
    }

    // Only used by the database class for caching
    public static class Message {
        private final String message;
        private final String json;

        public Message(String message, String json) {
            this.message = message;
            this.json = json;
        }

        public String getMessage() { return message; }
        public String getJSON() { return json; }

        // We don't need to compare the message, just the JSON

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Message message1 = (Message) o;
            return Objects.equals(json, message1.json);
        }

        @Override
        public int hashCode() {
            return Objects.hash(json);
        }
    }

    public record User(UUID uuid, String lastKnownName, String defaultLanguage, TranslationMode translationMode) {}

    public enum TranslationMode {
        OFF(0), ON(1), FORCE(2);
        private final int value;

        TranslationMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
