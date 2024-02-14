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

import org.apache.commons.dbcp2.BasicDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;
import static net.gensokyoreimagined.motoori.KosuzuDatabaseModels.*;

public class KosuzuRemembersEverything implements Closeable {
    private final YamlConfiguration translations;
    private final BasicDataSource dataSource = new BasicDataSource();
    private final FileConfiguration config;
    private final Logger logger;

    private boolean isSqlite = false;
    private boolean requiresInitialization = false;

    public KosuzuRemembersEverything(Kosuzu kosuzu) {
        config = kosuzu.config;
        logger = kosuzu.getLogger();

        var translationFile = kosuzu.getResource("translations.yml");
        if (translationFile == null) {
            throw new KosuzuException("Failed to find translations.yml! Is the plugin jar corrupted?");
        }

        try (var reader = new InputStreamReader(translationFile)) {
            translations = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            throw new KosuzuException("Failed to load translations.yml! Is the plugin jar corrupted?", ex);
        }

        var type = config.getString("storage.type");
        if (type == null) {
            type = "sqlite";
        }

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

    public String getTranslation(String key, String lang) {
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

        try {
            var pathObj = Path.of(path);

            if (!Files.exists(pathObj)) {
                Files.createFile(pathObj);
                requiresInitialization = true;
            }
        } catch (IOException e) {
            logger.severe("Failed to create SQLite database file! Maybe check your permissions? Writing to: " + path);
            throw new KosuzuException(e);
        }

        try {
            dataSource.setUrl("jdbc:sqlite:plugins/Kosuzu/" + path);
            dataSource.setMinIdle(5);
            dataSource.setMaxIdle(10);
            dataSource.setMaxOpenPreparedStatements(50);
        } catch (Exception e) {
            logger.severe("Failed to connect to SQLite database! Writing to: " + path);
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

            var statement = connection.prepareStatement("SHOW DATABASES LIKE ?");
            statement.setString(1, database);
            var result = statement.executeQuery();
            if (!result.next()) {
                requiresInitialization = true;
            }
            statement.close();

            statement = connection.prepareStatement("CREATE SCHEMA IF NOT EXISTS `" + database + "`");
            statement.execute();
            statement.close();
            connection.close();

            dataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
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
        if (!requiresInitialization) return;

        var initialization = kosuzu.getResource("dbinit.sql");
        if (initialization == null) {
            throw new KosuzuException("Failed to find dbinit.sql! Is the plugin jar corrupted?");
        }

        String sql;

        try {
            sql = new String(initialization.readAllBytes());
        } catch (IOException ex) {
            throw new KosuzuException("Failed to load dbinit.sql! Is the plugin jar corrupted?", ex);
        }

        var split = sql.split(";");

        try (var connection = getConnection()) {
            try (var statement = connection.createStatement()) {
                for (var query : split) {
                    statement.execute(query);
                }
            }

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
            logger.severe("Failed to initialize database!");
            throw new KosuzuException(e);
        }
    }

    private String s(String sql) {
        if (!isSqlite) {
            return sql;
        }

        return sql
                .replace("AUTO_INCREMENT", "AUTOINCREMENT")
                .replace("INSERT IGNORE", "INSERT OR IGNORE");
    }

    @NotNull
    public String getUserDefaultLanguage(UUID uuid) {
        try (var connection = getConnection()) {
            try (var statement = connection.prepareStatement("SELECT `default_language` FROM `user` WHERE `uuid` = ?")) {
                statement.setString(1, uuid.toString());
                var result = statement.executeQuery();
                if (result.next()) {
                    return result.getString("default_language");
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
                try (var statement = connection.prepareStatement("INSERT INTO `user` (`uuid`, `last_known_name`) VALUES (?, ?)")) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, username);
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
                var result = statement.executeQuery();
                // collect results into a collection
                var output = new ArrayList<String>();

                while (result.next()) {
                    output.add(result.getString("language"));
                }

                return output;
            }
        } catch (SQLException e) {
            logger.severe("Failed to get user languages!");
            logger.severe(e.getMessage());
        }

        return List.of(config.getString("default-language", "EN-US"));
    }

    public void setUserDefaultLanguage(UUID uuid, String lang) {
        try (var connection = getConnection()) {
            // i want to do ON DUPLICATE KEY UPDATE, but ugh compatibility
            try (var statement = connection.prepareStatement(s("INSERT IGNORE INTO `user` (`uuid`, `default_language`) VALUES (?, ?);"))) {
                statement.setString(1, uuid.toString());
                statement.setString(2, lang);
                statement.execute();
            }

            try (var statement = connection.prepareStatement(s("UPDATE `user` SET `default_language` = ? WHERE `uuid` = ?;"))) {
                statement.setString(1, lang);
                statement.setString(2, uuid.toString());
                statement.execute();
            }
        } catch (SQLException e) {
            logger.severe("Failed to set user default language!");
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
                var result = statement.executeQuery();
                var output = new ArrayList<Language>();

                while (result.next()) {
                    var language = new Language(result.getString("code"), result.getString("native_name"), result.getString("english_name"));
                    output.add(language);
                }

                languages = List.copyOf(output);
                return languages;
            }
        } catch (SQLException e) {
            logger.severe("Failed to get user languages!");
            logger.severe(e.getMessage());
        }

        throw new KosuzuException("Failed to get languages!");
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
