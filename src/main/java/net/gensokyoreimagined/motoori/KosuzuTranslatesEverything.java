package net.gensokyoreimagined.motoori;

import com.github.mizosoft.methanol.MoreBodyHandlers;
import com.google.gson.Gson;
import net.gensokyoreimagined.motoori.KosuzuTranslationModels.*;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

/**
 * Invokes DeepLX (i.e., DeepL mobile app impersonation) to translate everything; if rate-limited, use DeepL API
 * DeepLX derived from <a href="https://github.com/OwO-Network/PyDeepLX">PyDeepLX</a>
 */
public class KosuzuTranslatesEverything {
    private final HttpClient client;

    private final Gson gson = new Gson();

    public KosuzuTranslatesEverything() {
        client = HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    private final String DEEPL_MOBILE_API = "https://www2.deepl.com/jsonrpc";

    private DeepLTranslation translateViaMobileRPC(String input, @Nullable String language) {
        var deeplRequest = new DeepLMobileRequest(input, language);
        var jsonRequest = gson.toJson(deeplRequest);

        if ((deeplRequest.id + 5) % 29 == 0 || (deeplRequest.id + 3) % 13 == 0) {
            jsonRequest = jsonRequest.replace("\"method\":\"", "\"method\" : \"");
        } else {
            jsonRequest = jsonRequest.replace("\"method\" : \"", "\"method\": \"");
        }

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
            var response = client.send(request, MoreBodyHandlers.decoding(HttpResponse.BodyHandlers.ofString()));
            var statusCode = response.statusCode();
            var body = response.body();

            if (statusCode != 200) {
                Bukkit.getLogger().warning("[Kosuzu] Failed to send request to DeepL via mobile API:\n" + body);
                return null;
            }

            var deeplResponse = gson.fromJson(body, DeepLMobileResponse.class);
            return deeplResponse.getTranslation();
        }
        catch (Exception e) {
            Bukkit.getLogger().warning("[Kosuzu] Failed to send request to DeepL via mobile API:\n" + e.getMessage());
            return null;
        }
    }

    private DeepLTranslation translateViaAPI(String input, @Nullable String language) {
        var config = Kosuzu.getInstance().config;
        var key = config.getString("deepl-api-key");
        if (key == null || key.equals("changeme")) {
            Bukkit.getLogger().warning("[Kosuzu] Please set your DeepL API key in config.yml");
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
                Bukkit.getLogger().warning("[Kosuzu] Failed to send request to DeepL:\n" + body);
                return null;
            }

            var deeplResponse = gson.fromJson(body, DeepLResponse.class);
            return deeplResponse.getTranslation();
        }
        catch (Exception e) {
            Bukkit.getLogger().warning("[Kosuzu] Failed to send request to DeepL:\n" + e.getMessage());
            return null;
        }
    }

    public DeepLTranslation translate(String input, @Nullable String language) {
        var translation = translateViaMobileRPC(input, language);
        if (translation == null) {
            Bukkit.getLogger().warning("[Kosuzu] Falling back to DeepL API");
            translation = translateViaAPI(input, language);
        }

        return translation;
    }
}
