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
            Kosuzu.HEADER.append(Component.text("/kosuzu default <lang>", NamedTextColor.RED))
        );
    }

    private void changeUserLanguage(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                Kosuzu.HEADER.append(Component.text("bruh just copy-paste the text into DeepL", NamedTextColor.RED))
            );
            return;
        }

        if (args == null || args.length < 2) {
            invalidSubcommand(sender);
            return;
        }

        StringBuilder language = new StringBuilder();

        for (int i = 1; i < args.length; i++) {
            language.append(args[i].toLowerCase()).append(" ");
        }

        language.delete(language.length() - 1, language.length()); // Remove trailing space

        var languages = database.getLanguages();
        var matchingLanguage = languages
                .stream()
                .filter(l -> l.getCode().toLowerCase().contains(language) || l.getNativeName().toLowerCase().contains(language) || l.getEnglishName().toLowerCase().contains(language))
                .findFirst();

        if (matchingLanguage.isPresent()) {
            var code = matchingLanguage.get().getCode();

            database.setUserDefaultLanguage(player.getUniqueId(), code);

            sender.sendMessage(
                Kosuzu.HEADER.append(Component.text(database.getTranslation("language.change.success", code), NamedTextColor.GREEN))
            );
        } else {
            var oldLang = database.getUserDefaultLanguage(player.getUniqueId());

            sender.sendMessage(
                Kosuzu.HEADER.append(Component.text(database.getTranslation("language.change.fail", oldLang), NamedTextColor.RED))
            );
        }
    }
}
