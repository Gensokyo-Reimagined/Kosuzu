package net.gensokyoreimagined.motoori;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class KosuzuUnderstandsEverything implements Listener {

    static class DeepLRequest {
        @SerializedName("text")
        List<String> text;
        @SerializedName("target_lang")
        String targetLanguage;

        DeepLRequest(String text, String targetLanguage) {
            this.text = List.of(text);
            this.targetLanguage = targetLanguage;
        }
    }

    static class DeepLResponse {
        @SerializedName("translations")
        DeepLTranslation[] translations;

        DeepLTranslation getTranslation() {
            return translations[0];
        }

    }

    static class DeepLTranslation {
        @SerializedName("detected_source_language")
        String detectedSourceLanguage;
        @SerializedName("text")
        String text;
    }

    private final HttpClient client;

    private final Gson gson = new Gson();

    public KosuzuUnderstandsEverything() {
        client = HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    }

    // TODO make this configurable (esp by selected language)
    private final String FAILED_TO_TRANSLATE = "Failed to translate";

    private DeepLTranslation translate(String input, @Nullable String language) {

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

    @EventHandler
    public void onPlayerMessage(AsyncChatEvent event) {
        // problem with eager translation: can't adjust the message

        event.message(
            event
                .message()
                .hoverEvent(
                    Component
                        .text("Click to translate")
                        .color(NamedTextColor.GRAY)
                )
                .clickEvent(
                    ClickEvent.callback(
                        (player) -> {
                            var message = PlainTextComponentSerializer.plainText().serialize(event.message());

                            var uuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
                            if (player instanceof Player bukkitPlayer)
                                uuid = bukkitPlayer.getUniqueId();

                            var language = Kosuzu.getInstance().getUserLanguage(uuid);
                            var translation = translate(message, language);

                            if (translation == null) {
                                event.getPlayer().sendMessage(
                                    Component.text()
                                        .content(FAILED_TO_TRANSLATE)
                                        .color(NamedTextColor.RED)
                                        .build()
                                );

                                return;
                            }

                            event.getPlayer().sendMessage(
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
                    )
                )
        );
    }
}
