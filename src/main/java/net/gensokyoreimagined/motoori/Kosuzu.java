package net.gensokyoreimagined.motoori;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Kosuzu extends JavaPlugin {
    private static Kosuzu instance;

    public static Kosuzu getInstance() {
        return instance;
    }

    public final FileConfiguration config = getConfig();

    private static final Gson gson = new Gson();

    // we should probably use a database for this just for demo plzplzplz
    private ConcurrentHashMap<UUID, String> userLanguages = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        config.addDefault("deepl-api-url", "https://api-free.deepl.com/v2/translate");
        config.addDefault("deepl-api-key", "changeme");
        config.addDefault("default-language", "EN-US");
        config.addDefault("eager-translation", false); // DEPRECATED
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

        var autocompleteHandler = new KosuzuHintsEverything();
        var commandHandler = new KosuzuLearnsEverything();
        var eventHandler = new KosuzuUnderstandsEverything();

        var command = Objects.requireNonNull(getCommand("kosuzu"));
        command.setTabCompleter(autocompleteHandler);
        command.setExecutor(commandHandler);

        getServer().getPluginManager().registerEvents(eventHandler,this);

        // Load from config using Gson
        // Check if file exists
        var file = getDataFolder().toPath().resolve("user-languages.json").toFile();

        if (file.exists()) {
            // Read from file
            String json = null;
            try {
                json = Files.readString(file.toPath());
            } catch (IOException e) {
                Bukkit.getLogger().warning("[Kosuzu] Failed to read user-languages.json");
            }

            // Parse JSON
            var type = new TypeToken<ConcurrentHashMap<UUID, String>>(){}.getType();
            userLanguages = gson.fromJson(json, type);
        }
    }

    public void setUserLanguage(UUID uuid, String language) {
        userLanguages.put(uuid, language);

        // Save to config using Gson
        var file = getDataFolder().toPath().resolve("user-languages.json").toFile();
        var json = gson.toJson(userLanguages);

        try {
            Files.writeString(file.toPath(), json);
        } catch (IOException e) {
            Bukkit.getLogger().warning("[Kosuzu] Failed to write user-languages.json");
        }
    }

    public String getUserLanguage(UUID uuid) {
        return userLanguages.getOrDefault(uuid, config.getString("default-language"));
    }
}
