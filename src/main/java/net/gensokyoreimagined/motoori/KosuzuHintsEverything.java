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
