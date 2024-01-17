package net.gensokyoreimagined.motoori;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class KosuzuHintsEverything implements TabCompleter {

    public static final List<String> LANGUAGES = List.of(
        "BG", // Bulgarian
        "CS", // Czech
        "DA", // Danish
        "DE", // German
        "EL", // Greek
        "EN-GB", // English (British)
        "EN-US", // English (American)
        "ES", // Spanish
        "ET", // Estonian
        "FI", // Finnish
        "FR", // French
        "HU", // Hungarian
        "ID", // Indonesian
        "IT", // Italian
        "JA", // Japanese
        "KO", // Korean
        "LT", // Lithuanian
        "LV", // Latvian
        "NB", // Norwegian (Bokmål)
        "NL", // Dutch
        "PL", // Polish
        "PT-BR", // Portuguese (Brazilian)
        "PT-PT", // Portuguese (all Portuguese varieties excluding Brazilian Portuguese)
        "RO", // Romanian
        "RU", // Russian
        "SK", // Slovak
        "SL", // Slovenian
        "SV", // Swedish
        "TR", // Turkish
        "UK", // Ukrainian
        "ZH" // Chinese (simplified)
    );

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("language");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("language")) {
            return LANGUAGES;
        }

        return null;
    }
}
