package net.gensokyoreimagined.motoori;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class KosuzuHintsEverything implements TabCompleter {

    private final Collection<KosuzuDatabaseModels.Language> LANGUAGES;

    public KosuzuHintsEverything(Kosuzu kosuzu) {
        LANGUAGES = kosuzu.database.getLanguages();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("default", "");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("default")) {
            var query = args[1].toLowerCase();

            return
                LANGUAGES
                    .stream()
                    .filter(l -> l.getCode().toLowerCase().contains(query) || l.getNativeName().toLowerCase().contains(query) || l.getEnglishName().toLowerCase().contains(query))
                    .map(KosuzuDatabaseModels.Language::getNativeName)
                    .toList();
        }

        return null;
    }
}
