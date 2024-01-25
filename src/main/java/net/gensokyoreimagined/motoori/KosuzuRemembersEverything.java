package net.gensokyoreimagined.motoori;

import org.apache.commons.dbcp2.BasicDataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class KosuzuRemembersEverything {
    private final YamlConfiguration translations;
    private final BasicDataSource dataSource = new BasicDataSource();
    private final Logger logger;

    private boolean isSqlite = false;

    public KosuzuRemembersEverything() {
        logger = Kosuzu.getInstance().getLogger();

        var translationFile = Kosuzu.getInstance().getResource("translations.yml");
        if (translationFile == null) {
            throw new RuntimeException("Failed to find translations.yml! Is the plugin jar corrupted?");
        }

        try (var reader = new InputStreamReader(translationFile)) {
            translations = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load translations.yml! Is the plugin jar corrupted?", ex);
        }

        var type = Kosuzu.getInstance().config.getString("storage.type");
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
                throw new RuntimeException("Kosuzu can't remember how to use storage type: " + type);
        }

        initializeDatabase();
    }

    public String getTranslation(String key, String lang) {
        // Fetches the translation for a given key and language
        // If the translation doesn't exist, it will try to fetch the translation for the default language
        // If that doesn't exist either, it will return the key

        return translations.getString(key + "." + lang,
                translations.getString(key + "." + Kosuzu.getInstance().config.getString("default-language", "EN-US"), key + "." + lang));
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void initializeSqlite() {
        var path = Kosuzu.getInstance().config.getString("storage.sqlite.file", "kosuzu.db");

        try {
            var pathObj = Path.of(path);

            if (!Files.exists(pathObj))
                Files.createFile(pathObj);
        } catch (IOException e) {
            logger.severe("Failed to create SQLite database file! Maybe check your permissions? Writing to: " + path);
            throw new RuntimeException(e);
        }

        try {
            dataSource.setUrl("jdbc:sqlite:plugins/Kosuzu/" + path);
            dataSource.setMinIdle(5);
            dataSource.setMaxIdle(10);
            dataSource.setMaxOpenPreparedStatements(50);
        } catch (Exception e) {
            logger.severe("Failed to connect to SQLite database! Writing to: " + path);
            throw new RuntimeException(e);
        }
    }

    private void initializeMySQL() {
        var host = Kosuzu.getInstance().config.getString("storage.mysql.host", "localhost");
        var port = Kosuzu.getInstance().config.getInt("storage.mysql.port", 3306);

        if (port > 65535 || port < 0) {
            throw new RuntimeException("MySQL port is invalid! Writing to: " + port);
        }

        var database = Kosuzu.getInstance().config.getString("storage.mysql.database", "kosuzu");
        var username = Kosuzu.getInstance().config.getString("storage.mysql.username", "kosuzu");
        var password = Kosuzu.getInstance().config.getString("storage.mysql.password", "changeme");

        try {
            dataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
            dataSource.setUsername(username);
            dataSource.setPassword(password);
            dataSource.setMinIdle(5);
            dataSource.setMaxIdle(20);
            dataSource.setMaxOpenPreparedStatements(100);
        } catch (Exception e) {
            logger.severe("Failed to connect to MySQL database! Connecting to: " + host + ":" + port + "/" + database);
            throw new RuntimeException(e);
        }
    }

    private void initializeDatabase() {
        var initialization = Kosuzu.getInstance().getResource("dbinit.sql");
        if (initialization == null) {
            throw new RuntimeException("Failed to find dbinit.sql! Is the plugin jar corrupted?");
        }

        String sql;

        try {
            sql = new String(initialization.readAllBytes());
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load dbinit.sql! Is the plugin jar corrupted?", ex);
        }

        var split = sql.split(";");

        try (var connection = getConnection()) {
            try (var statement = connection.createStatement()) {
                for (var query : split) {
                    statement.execute(query);
                }
            }

            for (var language : KosuzuHintsEverything.LANGUAGES) {
                try (var insert = connection.prepareStatement(s("INSERT IGNORE INTO `language` (`code`, `native_name`, `english_name`) VALUES (?, ?, ?)"))) {
                    insert.setString(1, language);
                    insert.setString(2, getTranslation("language.native", language));
                    insert.setString(3, getTranslation("language.english", language));
                    insert.execute();
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to initialize database!");
            throw new RuntimeException(e);
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

        return Kosuzu.getInstance().config.getString("default-language", "EN-US");
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

        return List.of(Kosuzu.getInstance().config.getString("default-language", "EN-US"));
    }

    public void setUserDefaultLanguage(UUID uuid, String lang) {
        try (var connection = getConnection()) {
            // i want to do ON DUPLICATE KEY UPDATE, but ugh compatibility
            try (var statement = connection.prepareStatement(s("INSERT IGNORE INTO `user` (`uuid`, `default_language`) VALUES (?, ?); UPDATE `user` SET `default_language` = ? WHERE `uuid` = ?;"))) {
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
}
