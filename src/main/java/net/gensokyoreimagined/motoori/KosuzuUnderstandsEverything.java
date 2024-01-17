package net.gensokyoreimagined.motoori;

import com.google.common.util.concurrent.RateLimiter;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

public class KosuzuUnderstandsEverything implements Listener {

    private final KosuzuTranslatesEverything translator = new KosuzuTranslatesEverything();

    // TODO make this configurable (esp by selected language)
    private final String FAILED_TO_TRANSLATE = "Failed to translate";

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
                        (player) -> translateCallback(event, player)
                    )
                )
        );
    }

    private void translateCallback(AsyncChatEvent event, Audience player) {
        // var ratelimit = RateLimiter.create(2000);

        var message = PlainTextComponentSerializer.plainText().serialize(event.message());

        var uuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
        if (player instanceof Player bukkitPlayer)
            uuid = bukkitPlayer.getUniqueId();

        var language = Kosuzu.getInstance().getUserLanguage(uuid);
        var translation = translator.translate(message, language);

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
}
