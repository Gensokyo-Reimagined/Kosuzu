package net.gensokyoreimagined.motoori;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class KosuzuLearnsEverything implements CommandExecutor {

    private final KosuzuRemembersEverything database;

    public KosuzuLearnsEverything(Kosuzu kosuzu) {
        database = kosuzu.database;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            invalidSubcommand(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("default")) {
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

        if (args == null || args.length < 2) {
            invalidSubcommand(sender);
            return;
        }

        StringBuilder language = new StringBuilder();

        for (int i = 1; i < args.length; i++) {
            language.append(args[i]).append(" ");
        }

        language.delete(language.length() - 1, language.length()); // Remove trailing space

        var languages = database.getLanguages();
        var matchingLanguage = languages.stream().filter(lang -> lang.getNativeName().contentEquals(language)).findFirst();

        if (matchingLanguage.isPresent()) {
            var code = matchingLanguage.get().getCode();

            database.setUserDefaultLanguage(player.getUniqueId(), code);

            sender.sendMessage(
                Component
                    .text()
                    .color(NamedTextColor.GREEN)
                    .content(database.getTranslation("language.change.success", code))
                    .build()
            );
        } else {
            var oldLang = database.getUserDefaultLanguage(player.getUniqueId());

            sender.sendMessage(
                Component
                    .text()
                    .color(NamedTextColor.RED)
                    .content(database.getTranslation("language.change.fail", oldLang))
                    .build()
            );
        }
    }
}
