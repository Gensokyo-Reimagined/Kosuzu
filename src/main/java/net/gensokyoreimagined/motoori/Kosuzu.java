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

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class Kosuzu extends JavaPlugin {
    public final FileConfiguration config = getConfig();
    public KosuzuRemembersEverything database;

    private boolean papiGeoip = false;
    public boolean canUsePAPIGeoIP() { return papiGeoip; }

    public static Component HEADER = Component
            .text("[", NamedTextColor.GOLD)
            .append(Component.text("Kosuzu", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text("] ", NamedTextColor.GOLD));

    @Override
    public void onEnable() {
        config.addDefault("use-deepl-mobile", true);
        config.addDefault("deepl-api-url", "https://api-free.deepl.com/v2/translate");
        config.addDefault("deepl-api-key", "changeme");
        config.addDefault("default-language", "EN-US");
        config.addDefault("ratelimit.token_bucket_capacity", 256); // Max characters in a single message
        config.addDefault("ratelimit.token_refill_rate", 25); // Characters per second

        config.addDefault("storage.type", "sqlite");
        config.addDefault("storage.sqlite.file", "kosuzu.db");
        config.addDefault("storage.mysql.host", "localhost");
        config.addDefault("storage.mysql.port", 3306);
        config.addDefault("storage.mysql.database", "kosuzu");
        config.addDefault("storage.mysql.username", "kosuzu");
        config.addDefault("storage.mysql.password", "changeme");

        config.options().copyDefaults(true);
        saveConfig();

        database = new KosuzuRemembersEverything(this);

        var autocompleteHandler = new KosuzuHintsEverything(this);
        var commandHandler = new KosuzuLearnsEverything(this);
        var eventHandler = new KosuzuUnderstandsEverything(this);

        var command = Objects.requireNonNull(getCommand("kosuzu"));
        command.setTabCompleter(autocompleteHandler);
        command.setExecutor(commandHandler);

        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Server.CHAT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                var player = event.getPlayer();
                var packet = event.getPacket();
                var message = packet.getChatComponents().read(0);
                // getLogger().info("CHAT EVENT TO " + player.getName() + " " + message.getJson());
            }
        });

        manager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Server.SYSTEM_CHAT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                var player = event.getPlayer();
                var packet = event.getPacket();
                var message = packet.getChatComponents().read(0);
                var component = JSONComponentSerializer.json().deserialize(message.getJson()); // Adventure API from raw JSON
                // getLogger().info("SYSTEM CHAT EVENT TO " + player.getName() + " " + message.getJson());
            }
        });

        getServer().getPluginManager().registerEvents(eventHandler,this);
    }

    @Override
    public void onDisable() {
        if (database != null)
            database.close();
    }
}
