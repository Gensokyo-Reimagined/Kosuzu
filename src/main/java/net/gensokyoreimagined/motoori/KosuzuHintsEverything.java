package net.gensokyoreimagined.motoori;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class KosuzuHintsEverything implements TabCompleter {

    private final List<String> LANGUAGES;

    public KosuzuHintsEverything(Kosuzu kosuzu) {
        var languages = kosuzu.database.getLanguages();
        LANGUAGES = languages.stream().map(KosuzuDatabaseModels.Language::getNativeName).toList();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("default", "");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("default")) {
            return LANGUAGES;
        }

        return null;
    }
}
