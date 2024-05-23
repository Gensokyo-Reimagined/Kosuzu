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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.dbcp2.BasicDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import static net.gensokyoreimagined.motoori.KosuzuDatabaseModels.*;

public class KosuzuRemembersEverything implements Closeable {
    private final Kosuzu kosuzu;
    private final YamlConfiguration translations;
    private final BasicDataSource dataSource = new BasicDataSource();
    private final FileConfiguration config;
    private final Logger logger;

    private boolean isSqlite = false;

    public KosuzuRemembersEverything(Kosuzu kosuzu) {
        this.kosuzu = kosuzu;
        config = kosuzu.config;
        logger = kosuzu.getLogger();

        var translationFile = kosuzu.getResource("translations.yml");
        if (translationFile == null) {
            throw new KosuzuException("Failed to find translations.yml! Is the plugin jar corrupted?");
        }

        try (var reader = new InputStreamReader(translationFile, StandardCharsets.UTF_8)) {
            translations = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            throw new KosuzuException("Failed to load translations.yml! Is the plugin jar corrupted?", ex);
        }

        var type = config.getString("storage.type", "sqlite");

        switch (type) {
            case "sqlite":
                isSqlite = true;
                initializeSqlite();
                break;
            case "mysql":
                initializeMySQL();
                break;
            default:
                throw new KosuzuException("Kosuzu can't remember how to use storage type: " + type);
        }

        initializeDatabase(kosuzu);
    }

    public String getTranslation(@NotNull String key, @Nullable String lang) {
        // Fetches the translation for a given key and language
        // If the translation doesn't exist, it will try to fetch the translation for the default language
        // If that doesn't exist either, it will return the key

        return translations.getString(key + "." + lang,
                translations.getString(key + "." + config.getString("default-language", "EN-US"), key + "." + lang));
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void initializeSqlite() {
        var path = config.getString("storage.sqlite.file", "kosuzu.db");

        if (!Files.exists(Path.of("plugins/Kosuzu/" + path))) {
            // Force re-migration if the database file was deleted
            config.set("DO-NOT-EDIT-VERSION-UNLESS-YOU-KNOW-WHAT-YOU-ARE-DOING", 0);
        }

        try {
            dataSource.setUrl("jdbc:sqlite:plugins/Kosuzu/" + path);
            dataSource.setMinIdle(5);
            dataSource.setMaxIdle(10);
            dataSource.setMaxOpenPreparedStatements(50);
        } catch (Exception e) {
            logger.severe("Failed to setup SQLite database! Writing to: " + path);
            throw new KosuzuException(e);
        }
    }

    private void initializeMySQL() {
        var host = config.getString("storage.mysql.host", "localhost");
        var port = config.getInt("storage.mysql.port", 3306);

        if (port > 65535 || port < 0) {
            throw new KosuzuException("MySQL port is invalid! Writing to: " + port);
        }

        var database = config.getString("storage.mysql.database", "kosuzu");
        var username = config.getString("storage.mysql.username", "kosuzu");
        var password = config.getString("storage.mysql.password", "changeme");

        try {
            // Initialize the schema before setting up pool
            var connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/", username, password);

            // Can't really use prepared statements here
            try (var statement = connection.prepareStatement("CREATE SCHEMA IF NOT EXISTS `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_520_ci;")) {
                statement.execute();
            }
            connection.close();

            dataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useUnicode=true&characterEncoding=utf-8");
            dataSource.setUsername(username);
            dataSource.setPassword(password);
            dataSource.setMinIdle(5);
            dataSource.setMaxIdle(20);
            dataSource.setMaxOpenPreparedStatements(100);
        } catch (Exception e) {
            logger.severe("Failed to connect to MySQL database! Connecting to: " + host + ":" + port + "/" + database);
            throw new KosuzuException(e);
        }
    }

    private void initializeDatabase(Kosuzu kosuzu) {
        var migrationIndex = config.getInt("DO-NOT-EDIT-VERSION-UNLESS-YOU-KNOW-WHAT-YOU-ARE-DOING", 0);
        var extension = "." + config.getString("storage.type", "sqlite") + ".sql";

        // Iterate through all available migration files starting from last read in config
        while (true) {
            var update = kosuzu.getResource("migration" + migrationIndex + extension);
            if (update == null) {
                break;
            }

            logger.info("Applying migration " + migrationIndex + " to database...");
            loadSQLFile(update);

            ++migrationIndex;
        }

        config.set("DO-NOT-EDIT-VERSION-UNLESS-YOU-KNOW-WHAT-YOU-ARE-DOING", migrationIndex);
        kosuzu.saveConfig();

        loadLanguages();
    }

    private void loadSQLFile(InputStream fileStream) {
        if (fileStream == null) {
            throw new KosuzuException("Failed to load SQL file stream! Is the plugin jar corrupted?");
        }

        String sql;

        try {
            sql = new String(fileStream.readAllBytes());
        } catch (IOException ex) {
            throw new KosuzuException("Failed to read SQL file stream! Is the plugin jar corrupted?", ex);
        }

        var split = sql.split(";");

        String lastQuery = "";

        try (var connection = getConnection()) {
            try (var statement = connection.createStatement()) {
                for (var query : split) {
                    if (query.isBlank()) continue;
                    lastQuery = query;
                    statement.execute(query);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to initialize database! Violating query: " + lastQuery);
            throw new KosuzuException(e);
        }
    }

    private void loadLanguages() {
        try (var connection = getConnection()) {
            // Get all codes from the translations file
            var keys = Objects.requireNonNull(translations.getConfigurationSection("language.english")).getKeys(false);

            for (var language : keys) {
                try (var insert = connection.prepareStatement(s("INSERT IGNORE INTO `language` (`code`, `native_name`, `english_name`) VALUES (?, ?, ?)"))) {
                    insert.setString(1, language);
                    insert.setString(2, getTranslation("language.native", language));
                    insert.setString(3, getTranslation("language.english", language));
                    insert.execute();
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to load languages into database! " + e.getMessage());
            throw new KosuzuException(e);
        }
    }

    /**
     * Change MySQL queries to be compatible with SQLite
     * @param sql The MySQL-compatible SQL query
     * @return The SQLite-compatible SQL query
     */
    private String s(String sql) {
        if (!isSqlite) {
            return sql;
        }

        return sql
                .replace("AUTO_INCREMENT", "AUTOINCREMENT")
                .replace("INSERT IGNORE", "INSERT OR IGNORE");
    }

    private final HashMap<UUID, String> userLanguages = new HashMap<>();

    @NotNull
    public String getUserDefaultLanguage(UUID uuid) {
        return userLanguages.computeIfAbsent(uuid, this::getUserDefaultLanguageSQL);
    }

    @NotNull
    private String getUserDefaultLanguageSQL(UUID uuid) {
        try (var connection = getConnection()) {
            try (var statement = connection.prepareStatement("SELECT `default_language` FROM `user` WHERE `uuid` = ?")) {
                statement.setString(1, uuid.toString());
                try (var result = statement.executeQuery()) {
                    if (result.next()) {
                        return result.getString("default_language");
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get user default language!");
            logger.severe(e.getMessage());
        }

        return config.getString("default-language", "EN-US");
    }

    public boolean isNewUser(UUID uuid, String username) {
        boolean isNew = false;

        try (var connection = getConnection()) {
            try (var statement = connection.prepareStatement("SELECT `uuid` FROM `user` WHERE `uuid` = ?")) {
                statement.setString(1, uuid.toString());
                var result = statement.executeQuery();
                isNew = !result.next();
            }

            if (isNew) {
                try (var statement = connection.prepareStatement("INSERT INTO `user` (`uuid`, `last_known_name`, `default_language`) VALUES (?, ?, ?)")) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, username);
                    statement.setString(3, config.getString("default-language", "EN-US"));
                    statement.execute();
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to check if user is new!");
            logger.severe(e.getMessage());
        }

        return isNew;
    }

    @NotNull
    public Collection<String> getUserLanguages(String uuid) {
        try (var connection = getConnection()) {
            try (var statement = connection.prepareStatement("SELECT `language` FROM `multilingual` WHERE `uuid` = ?")) {
                statement.setString(1, uuid);
                try (var result = statement.executeQuery()) {
                    // collect results into a collection
                    var output = new ArrayList<String>();

                    while (result.next()) {
                        output.add(result.getString("language"));
                    }

                    return output;
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get user languages!");
            logger.severe(e.getMessage());
        }

        return List.of(config.getString("default-language", "EN-US"));
    }

    public void setUserDefaultLanguage(@NotNull UUID uuid, @NotNull String lang) {
        try (var connection = getConnection()) {
            try (var statement = connection.prepareStatement(s("UPDATE `user` SET `default_language` = ? WHERE `uuid` = ?;"))) {
                statement.setString(1, lang);
                statement.setString(2, uuid.toString());
                statement.execute();
                userLanguages.put(uuid, lang);
            }
        } catch (SQLException e) {
            logger.severe("Failed to set user default language!");
            logger.severe(e.getMessage());
        }
    }

    // Use TranslationMode mode instead of directly using integers
    public void setUserAutoTranslate(@NotNull UUID uuid, TranslationMode mode) {
        try (var connection = getConnection()) {
            try (var statement = connection.prepareStatement("UPDATE `user` SET `use_auto` = ? WHERE `uuid` = ?;")) {
                statement.setInt(1, mode.getValue());
                statement.setString(2, uuid.toString());
                statement.execute();
            }
        } catch (SQLException e) {
            logger.severe("Failed to set user auto translate!");
            logger.severe(e.getMessage());
        }
    }

    private Collection<Language> languages;

    /**
     * Gets a list of languages supported by Kosuzu.
     * Note that the first call will be cached and saved, as languages cannot be modified on the fly.
     * @return An unmodifiable list of languages.
     */
    public Collection<Language> getLanguages() {
        if (languages != null)
            return languages;

        try (var connection = getConnection()) {
            try (var statement = connection.prepareStatement("SELECT `code`, `native_name`, `english_name` FROM `language` ORDER BY `native_name`;")) {
                try (var result = statement.executeQuery()) {
                    var output = new ArrayList<Language>();

                    while (result.next()) {
                        var language = new Language(result.getString("code"), result.getString("native_name"), result.getString("english_name"));
                        output.add(language);
                    }

                    languages = List.copyOf(output);
                    return languages;
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get user languages!");
            logger.severe(e.getMessage());
        }

        throw new KosuzuException("Failed to get languages!");
    }

    public Translation getTranslation(@NotNull UUID message, @NotNull UUID user) {
        try (var connection = getConnection()) {
            try (var statement = connection.prepareStatement("SELECT user_message.json_msg, message.uuid AS message_id, message.language, message.text, message_translation.text AS translation, `user`.`default_language` FROM `user_message_lookup` INNER JOIN `user_message` ON user_message.uuid = user_message_lookup.user_message_id LEFT JOIN `message` ON message.uuid = user_message.message_id LEFT JOIN `user` ON `user`.`uuid` = ? LEFT JOIN `message_translation` ON message_translation.message_id = message.uuid AND user.default_language = message_translation.language WHERE `user_message_lookup`.`uuid` = ?;")) {
                statement.setString(1, user.toString());
                statement.setString(2, message.toString());
                try (var result = statement.executeQuery()) {
                    if (result.next()) {
                        return new Translation(result.getString("json_msg"), UUID.fromString(result.getString("message_id")), result.getString("language"), result.getString("text"), result.getString("translation"), result.getString("default_language"));
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get message!");
            logger.severe(e.getMessage());
        }

        return null;
    }

    public void addTranslation(@NotNull UUID message, @NotNull String translation, @NotNull String language, @NotNull String originalLanguage) {
        try (var connection = getConnection()) {
            try (var statement = connection.prepareStatement("INSERT INTO `message_translation` (`uuid`, `message_id`,`language`, `text`) VALUES (?, ?, ?, ?);")) {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, message.toString());
                statement.setString(3, language);
                statement.setString(4, translation);

                statement.execute();
            }

            try (var statement = connection.prepareStatement("UPDATE `message` SET `language` = ? WHERE `uuid` = ?;")) {
                statement.setString(1, originalLanguage);
                statement.setString(2, message.toString());

                statement.execute();
            }
        } catch (SQLException e) {
            logger.severe("Failed to add translation!");
            logger.severe(e.getMessage());
        }
    }

    /**
     * Explanation: So, we need to capture outgoing messages, because this includes Discord messages and other messages
     * that are not chat messages - this is a lot of messages, and we don't want to send a lot of requests to the database.
     * This will unfortunately mean this gets called once per message, per player (!) - we don't want to do that.
     */
    private final LoadingCache<Message, UUID> messageCache = CacheBuilder.newBuilder()
            .maximumSize(512)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build(
                    new CacheLoader<>() {
                        public @NotNull UUID load(@NotNull Message json) {
                            return addMessageSQLWrapper(json.getJSON(), json.getMessage());
                        }
                    });

    public UUID addMessage(@NotNull String json, @NotNull String message) {
        return messageCache.getUnchecked(new Message(message, json));
    }

    /**
     * OK, so second issue: what if SQL is slow? We can't wait for SQL to get a UUID, so we generate one ahead of time.
     * So, we've created a new table called `user_message_lookup` that links the eagerly generated UUID to the actual UUID.
     * Note that it's possible that a person tries to translate a message before it's been added to the database (async).
     */
    private @NotNull UUID addMessageSQLWrapper(@NotNull String json, @NotNull String message) {
        UUID uuid = UUID.randomUUID();

        // Invoke addMessageSQL asynchronously
        kosuzu.getServer().getScheduler().runTaskAsynchronously(kosuzu, () -> addMessageSQL(uuid, json, message));

        return uuid;
    }

    private void addMessageSQL(@NotNull UUID lookupUUID, @NotNull String json, @NotNull String message) {
        try (var connection = getConnection()) {
            UUID messageUUID = null;

            // Store the message contents (plain text) in the database
            try (var statement = connection.prepareStatement("SELECT uuid FROM `message` WHERE `text` = ?")) {
                statement.setString(1, message);
                try (var data = statement.executeQuery()) {
                    if (data.next()) {
                        messageUUID = UUID.fromString(data.getString("uuid"));
                    }
                }
            }

            if (messageUUID == null) {
                messageUUID = UUID.randomUUID();
                try (var statement = connection.prepareStatement("INSERT INTO `message` (`uuid`, `text`) VALUES (?, ?)")) {
                    statement.setString(1, messageUUID.toString());
                    statement.setString(2, message);
                    statement.execute();
                }
            }

            UUID uuid = null;
            // Store the JSON Minecraft message in the database
            try (var statement = connection.prepareStatement("SELECT `uuid` FROM `user_message` WHERE `json_msg` = ?")) {
                statement.setString(1, json);
                try (var data = statement.executeQuery()) {
                    if (data.next()) {
                        uuid = UUID.fromString(data.getString("uuid"));
                    }
                }
            }

            if (uuid == null) {
                uuid = UUID.randomUUID();

                try (var statement = connection.prepareStatement("INSERT INTO `user_message` (`uuid`, `message_id`, `json_msg`) VALUES (?, ?, ?)")) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, messageUUID.toString());
                    statement.setString(3, json);
                    statement.execute();
                }
            }

            // Finally, store the lookup UUID
            try (var statement = connection.prepareStatement("INSERT INTO `user_message_lookup` (`uuid`, `user_message_id`) VALUES (?, ?)")) {
                statement.setString(1, lookupUUID.toString());
                statement.setString(2, uuid.toString());
                statement.execute();
            }
        } catch (SQLException e) {
            logger.severe("Failed to add message!");
            logger.severe(e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            dataSource.close();
        } catch (SQLException e) {
            logger.severe("Failed to close database connection!");
            logger.severe(e.getMessage());
        }
    }
}
