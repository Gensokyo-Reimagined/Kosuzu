package net.gensokyoreimagined.motoori;

import com.google.gson.Gson;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class Kosuzu extends JavaPlugin {
    private static Kosuzu instance;

    public static Kosuzu getInstance() {
        return instance;
    }

    public final FileConfiguration config = getConfig();
    public KosuzuRemembersEverything database;

    private static final Gson gson = new Gson();

    @Override
    public void onEnable() {
        instance = this;

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

        var autocompleteHandler = new KosuzuHintsEverything();
        var commandHandler = new KosuzuLearnsEverything();
        var eventHandler = new KosuzuUnderstandsEverything();

        var command = Objects.requireNonNull(getCommand("kosuzu"));
        command.setTabCompleter(autocompleteHandler);
        command.setExecutor(commandHandler);

        getServer().getPluginManager().registerEvents(eventHandler,this);

        database = new KosuzuRemembersEverything();
    }
}
