package net.gensokyoreimagined.motoori;

import org.apache.commons.dbcp2.BasicDataSource;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class KosuzuRemembersEverything {
    private BasicDataSource dataSource = new BasicDataSource();

    private static KosuzuRemembersEverything instance;

    public static KosuzuRemembersEverything getInstance() {
        return instance;
    }

    private KosuzuRemembersEverything() {
        instance = this;

        var type = Kosuzu.getInstance().config.getString("storage.type");
        if (type == null) {
            type = "sqlite";
        }

        switch (type) {
            case "sqlite":
                initializeSqlite();
                break;
            case "mysql":
                initializeMysql();
                break;
            default:
                throw new RuntimeException("Kosuzu can't remember how to use storage type: " + type);
        }

        initializeDatabase();
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
            Bukkit.getLogger().severe("Failed to create SQLite database file! Maybe check your permissions? Writing to: " + path);
            throw new RuntimeException(e);
        }

        try {
            dataSource.setUrl("jdbc:sqlite:" + path);
            dataSource.setMinIdle(5);
            dataSource.setMaxIdle(10);
            dataSource.setMaxOpenPreparedStatements(50);
        } catch (Exception e) {
            Bukkit.getLogger().severe("Failed to connect to SQLite database! Writing to: " + path);
            throw new RuntimeException(e);
        }
    }

    private void initializeMysql() {
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
            Bukkit.getLogger().severe("Failed to connect to MySQL database! Connecting to: " + host + ":" + port + "/" + database);
            throw new RuntimeException(e);
        }
    }

    private void initializeDatabase() {
        try (var connection = getConnection()) {
            var statement = connection.createStatement();
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `user` (`uuid` VARCHAR(36) NOT NULL, `default_language` VARCHAR(8) NOT NULL DEFAULT 'EN-US', PRIMARY KEY (`uuid`))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `language` (`uuid` VARCHAR(36) NOT NULL, `language` VARCHAR(8) NOT NULL, `level` INT NOT NULL DEFAULT 0, PRIMARY KEY (`uuid`, `language`), FOREIGN KEY (`uuid`) REFERENCES `user` (`uuid`) ON DELETE CASCADE)");
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Failed to initialize database!");
            throw new RuntimeException(e);
        }
    }

    @NotNull
    public String getUserDefaultLanguage(String uuid) {
        try (var connection = getConnection()) {
            var statement = connection.prepareStatement("SELECT `default_language` FROM `user` WHERE `uuid` = ?");
            statement.setString(1, uuid);
            var result = statement.executeQuery();
            if (result.next()) {
                return result.getString("default_language");
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Failed to get user default language!");
            Bukkit.getLogger().severe(e.getMessage());
        }

        return Kosuzu.getInstance().config.getString("default-language", "EN-US");
    }

    @NotNull
    public Collection<String> getUserLanguages(String uuid) {
        try (var connection = getConnection()) {
            try (var statement = connection.prepareStatement("SELECT `language` FROM `language` WHERE `uuid` = ?")) {
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
            Bukkit.getLogger().severe("Failed to get user languages!");
            Bukkit.getLogger().severe(e.getMessage());
        }

        return List.of(Kosuzu.getInstance().config.getString("default-language", "EN-US"));
    }
}
