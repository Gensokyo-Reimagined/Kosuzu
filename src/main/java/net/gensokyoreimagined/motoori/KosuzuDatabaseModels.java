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

public class KosuzuDatabaseModels {
    public static class Language {
        private final String code;
        private final String nativeName;
        private final String englishName;

        public Language(String code, String nativeName, String englishName) {
            this.code = code;
            this.nativeName = nativeName;
            this.englishName = englishName;
        }

        public String getCode() { return code; }
        public String getNativeName() { return nativeName; }
        public String getEnglishName() { return englishName; }
    }

    public static class Message {
        private final String messageJson;
        private final String textMessage;
        private @Nullable String translatedTextLanguageCode;
        private @Nullable String translatedTextMessage;
        private final String languageCode;

        public Message(String messageJson, String textMessage, @Nullable String translatedTextLanguageCode, @Nullable String translatedTextMessage, String languageCode) {
            this.messageJson = messageJson;
            this.textMessage = textMessage;
            this.translatedTextLanguageCode = translatedTextLanguageCode;
            this.translatedTextMessage = translatedTextMessage;
            this.languageCode = languageCode;
        }

        public String getMessageJson() { return messageJson; }

        public String getTextMessage() { return textMessage; }

        public @Nullable String getTranslatedTextMessage() { return translatedTextMessage; }

        public void loadTranslatedTextMessage(KosuzuTranslatesEverything translator) {
            if (translatedTextLanguageCode != null && translatedTextMessage != null) {
                return;
            }

            var translation = translator.translate(textMessage, languageCode);
            translatedTextLanguageCode = translation.detectedSourceLanguage;
            translatedTextMessage = translation.text;
        }
    }
}
