package net.gensokyoreimagined.motoori;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class KosuzuLearnsEverything implements CommandExecutor {

    public KosuzuLearnsEverything() {

    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            invalidSubcommand(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("language")) {
            changeUserLanguage(sender, args);
            return true;
        }

        invalidSubcommand(sender);
        return true;
    }

    private static void invalidSubcommand(@NotNull CommandSender sender) {
        sender.sendMessage(
            Component
                .text()
                .color(NamedTextColor.RED)
                .content("Usage: /kosuzu language <langcode>")
                .build()
        );
    }

    private void changeUserLanguage(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                Component
                    .text()
                    .color(NamedTextColor.RED)
                    .content("bruh just copy-paste the text into DeepL")
                    .build()
            );
            return;
        }

        var language = args[1].toUpperCase();

        if (KosuzuHintsEverything.LANGUAGES.contains(language)) {
            Kosuzu.getInstance().setUserLanguage(player.getUniqueId(), language);

            sender.sendMessage(
                Component
                    .text()
                    .color(NamedTextColor.GREEN)
                    .content("Language changed to " + language)
                    .build()
            );
        } else {
            sender.sendMessage(
                Component
                    .text()
                    .color(NamedTextColor.RED)
                    .content("Invalid language code")
                    .build()
            );
        }
    }
}
