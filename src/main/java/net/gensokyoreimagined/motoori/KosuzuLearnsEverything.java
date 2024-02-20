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
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class KosuzuLearnsEverything implements CommandExecutor {

    private final KosuzuRemembersEverything database;
    private final KosuzuTranslatesEverything translator;

    public KosuzuLearnsEverything(Kosuzu kosuzu) {
        database = kosuzu.database;
        translator = new KosuzuTranslatesEverything(kosuzu);
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

        if (args[0].equalsIgnoreCase("translate")) {
            translateMessage(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("auto")) {
            setAuto(sender, args);
            return true;
        }

        invalidSubcommand(sender);
        return true;
    }

    private static void invalidSubcommand(@NotNull CommandSender sender) {
        if (sender.hasPermission("kosuzu.translate.auto")) {
            sender.sendMessage(
                Kosuzu.HEADER.append(Component.text("/kosuzu <default|auto>", NamedTextColor.RED))
            );
        } else {
            sender.sendMessage(
                Kosuzu.HEADER.append(Component.text("/kosuzu <default>", NamedTextColor.RED))
            );
        }
    }

    private void changeUserLanguage(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                Kosuzu.HEADER.append(Component.text("bruh just copy-paste the text into DeepL", NamedTextColor.RED))
            );
            return;
        }

        if (args == null || args.length < 2) {
            sender.sendMessage(
                Kosuzu.HEADER.append(Component.text("/kosuzu default <language>", NamedTextColor.RED))
            );
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
                .filter(l -> l.code().toLowerCase().contains(language) || l.nativeName().toLowerCase().contains(language) || l.englishName().toLowerCase().contains(language))
                .findFirst();

        if (matchingLanguage.isPresent()) {
            var code = matchingLanguage.get().code();

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

    private void setAuto(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                Kosuzu.HEADER.append(Component.text("bruh just copy-paste the text into DeepL", NamedTextColor.RED))
            );
            return;
        }

        var canForce = sender.hasPermission("kosuzu.translate.auto.force");

        if (args.length < 2) {
            if (canForce) {
                sender.sendMessage(
                    Kosuzu.HEADER.append(Component.text("/kosuzu auto <on|off|force>", NamedTextColor.RED))
                );
            } else {
                sender.sendMessage(
                    Kosuzu.HEADER.append(Component.text("/kosuzu auto <on|off>", NamedTextColor.RED))
                );
            }
            return;
        }

        if (args[1].equalsIgnoreCase("on")) {
            database.setUserAutoTranslate(player.getUniqueId(), KosuzuDatabaseModels.TranslationMode.ON);

            sender.sendMessage(
                Kosuzu.HEADER.append(Component.text(database.getTranslation("auto.on", database.getUserDefaultLanguage(player.getUniqueId())), NamedTextColor.GREEN))
            );
        } else if (args[1].equalsIgnoreCase("off")) {
            database.setUserAutoTranslate(player.getUniqueId(), KosuzuDatabaseModels.TranslationMode.OFF);

            sender.sendMessage(
                Kosuzu.HEADER.append(Component.text(database.getTranslation("auto.off", database.getUserDefaultLanguage(player.getUniqueId())), NamedTextColor.GREEN))
            );
        } else if (args[1].equalsIgnoreCase("force") && canForce) {
            database.setUserAutoTranslate(player.getUniqueId(), KosuzuDatabaseModels.TranslationMode.FORCE);

            sender.sendMessage(
                Kosuzu.HEADER.append(Component.text(database.getTranslation("auto.force", database.getUserDefaultLanguage(player.getUniqueId())), NamedTextColor.GREEN))
            );
        } else {
            if (canForce) {
                sender.sendMessage(
                        Kosuzu.HEADER.append(Component.text("/kosuzu auto <on|off|force>", NamedTextColor.RED))
                );
            } else {
                sender.sendMessage(
                        Kosuzu.HEADER.append(Component.text("/kosuzu auto <on|off>", NamedTextColor.RED))
                );
            }
        }
    }

    private void translateMessage(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            return;
        }

        var uuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
        if (sender instanceof Player bukkitPlayer)
            uuid = bukkitPlayer.getUniqueId();

        UUID messageUuid;
        var userLanguage = database.getUserDefaultLanguage(uuid);

        try {
            messageUuid = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            return;
        }

        var translation = database.getTranslation(messageUuid, uuid);
        translation.loadTranslatedTextMessage(translator, database);
        var translatedLanguage = translation.getTranslatedTextLanguageCode();
        var translated = translation.getTranslatedTextMessage();
        var originalLanguage = translation.getOriginalTextLanguageCode();
        var original = translation.getOriginalTextMessage();

        if (translated == null || originalLanguage == null || translatedLanguage == null) {
            sender.sendMessage(
                    Kosuzu.HEADER.append(Component.text(database.getTranslation("translate.fail", userLanguage), NamedTextColor.RED))
            );

            return;
        }

        var json = translation.getMessageJson();
        json = json.replace(original, translated);
        var translatedComponent = JSONComponentSerializer.json().deserialize(json);

        sender.sendMessage(
                Component
                        .text()
                        .append(
                                Component
                                        .text()
                                        .content("[" + originalLanguage + " -> " + translatedLanguage + "] ")
                                        .color(NamedTextColor.GRAY)
                        )

                        .append(
                                translatedComponent
                        )
                        .append(
                                Component
                                        .text()
                                        .content(" (" + original + ")")
                                        .decorate(TextDecoration.ITALIC)
                                        .color(NamedTextColor.GRAY)
                        )
                        .build()
        );
    }
}
