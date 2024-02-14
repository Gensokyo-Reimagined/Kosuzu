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

import com.github.mizosoft.methanol.MoreBodyHandlers;
import com.google.gson.Gson;
import net.gensokyoreimagined.motoori.KosuzuTranslationModels.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Invokes DeepLX (i.e., DeepL mobile app impersonation) to translate everything; if rate-limited, use DeepL API
 * DeepLX derived from <a href="https://github.com/OwO-Network/PyDeepLX">PyDeepLX</a>
 */
public class KosuzuTranslatesEverything {
    private final HttpClient client;
    private final Logger logger;
    private final FileConfiguration config;

    private final Gson gson = new Gson();

    public KosuzuTranslatesEverything(Kosuzu kosuzu) {
        logger = kosuzu.getLogger();
        config = kosuzu.config;
        client = HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    private DeepLTranslation translateViaMobileRPC(String input, @Nullable String language) {
        // issue: language is weirdly converted for mobile API?
        if (language == null) {
            language = Objects.requireNonNull(config.getString("default-language"));
        }

        // region not respected in this API
        language = language.substring(0, 2).toLowerCase();

        var deeplRequest = new DeepLMobileRequest(input, language);
        var jsonRequest = gson.toJson(deeplRequest);

        if ((deeplRequest.id + 5) % 29 == 0 || (deeplRequest.id + 3) % 13 == 0) {
            jsonRequest = jsonRequest.replace("\"method\":\"", "\"method\" : \"");
        } else {
            jsonRequest = jsonRequest.replace("\"method\" : \"", "\"method\": \"");
        }

        final String DEEPL_MOBILE_API = "https://www2.deepl.com/jsonrpc";
        var request = HttpRequest
            .newBuilder(URI.create(DEEPL_MOBILE_API))
            // we have an impostor among us
            .header("Content-Type", "application/json")
            .header("Accept", "*/*")
            .header("x-app-os-name", "iOS")
            .header("x-app-os-version", "16.3.0")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept-Encoding", "gzip, deflate, br")
            .header("x-app-device", "iPhone13,2")
            .header("User-Agent", "DeepL-iOS/2.11.2 iOS 16.3.0 (iPhone13,1)")
            .header("x-app-build", "510265")
            .header("x-app-version", "2.11.2")
            // .header("Connection", "keep-alive")
            .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
            .build();

        try {
            // MoreBodyHandlers since the response is brotli-compressed
            var response = client.send(request, MoreBodyHandlers.decoding(HttpResponse.BodyHandlers.ofString()));
            var statusCode = response.statusCode();
            var body = response.body();

            if (statusCode != 200) {
                logger.warning("Failed to send request to DeepL via mobile API:\n" + body);
                return null;
            }

            var deeplResponse = gson.fromJson(body, DeepLMobileResponse.class);
            return deeplResponse.getTranslation();
        }
        catch (Exception e) {
            logger.warning("Failed to send request to DeepL via mobile API:\n" + e.getMessage());
            return null;
        }
    }

    private DeepLTranslation translateViaAPI(String input, @Nullable String language) {
        var key = config.getString("deepl-api-key");
        if (key == null || key.equals("changeme")) {
            logger.warning("Please set your DeepL API key in config.yml");
            return null;
        }

        var url = Objects.requireNonNull(config.getString("deepl-api-url"));
        URI uri = URI.create(url);

        if (language == null) {
            language = Objects.requireNonNull(config.getString("default-language"));
        }

        var deeplRequest = new DeepLRequest(input, language);
        var request = HttpRequest
            .newBuilder(uri)
            .header("Authorization", "DeepL-Auth-Key " + key)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(deeplRequest)))
            .build();

        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            var statusCode = response.statusCode();
            var body = response.body();

            if (statusCode != 200) {
                logger.warning("Failed to send request to DeepL:\n" + body);
                return null;
            }

            var deeplResponse = gson.fromJson(body, DeepLResponse.class);
            return deeplResponse.getTranslation();
        }
        catch (Exception e) {
            logger.warning("Failed to send request to DeepL:\n" + e.getMessage());
            return null;
        }
    }

    public DeepLTranslation translate(String input, @Nullable String language) {
        if (config.getBoolean("use-deepl-mobile")) {
            var translation = translateViaMobileRPC(input, language);

            if (translation == null) {
                logger.warning("Falling back to DeepL API");
            } else {
                return translation;
            }
        }

        return translateViaAPI(input, language);
    }
}
