package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class LegacyDatabase {
    private static final String DB_URL = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(DB_URL);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
                migrateLegacyPasswords(connection);
                insertSeedUser(connection, "student", "Workshop123!", "Workshop Student", "STUDENT");
                insertSeedUser(connection, "trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
            }
        } catch (IOException | SQLException error) {
            throw new IllegalStateException("Unable to initialize authentication database", error);
        }
    }

    private void migrateLegacyPasswords(Connection connection) throws SQLException {
        List<LegacyPassword> legacyPasswords = new ArrayList<>();
        String selectSql = "SELECT id, password FROM legacy_users";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(selectSql)) {
            while (result.next()) {
                String storedPassword = result.getString("password");
                if (!PasswordHasher.isHashed(storedPassword)) {
                    legacyPasswords.add(new LegacyPassword(
                            result.getLong("id"),
                            storedPassword));
                }
            }
        }

        String updateSql = "UPDATE legacy_users SET password=? WHERE id=? AND password=?";
        try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
            for (LegacyPassword legacyPassword : legacyPasswords) {
                statement.setString(1, PasswordHasher.hash(legacyPassword.password()));
                statement.setLong(2, legacyPassword.userId());
                statement.setString(3, legacyPassword.password());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public UserRecord findByCredentials(String username, String password) {
        String sql = "SELECT id, username, password, display_name, role FROM legacy_users WHERE username=?";
        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            UserRecord user;
            String storedPassword;
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                user = new UserRecord(
                        result.getLong("id"),
                        result.getString("username"),
                        result.getString("display_name"),
                        result.getString("role"));
                storedPassword = result.getString("password");
            }

            if (!PasswordHasher.matches(password, storedPassword)) {
                return null;
            }
            if (!PasswordHasher.isHashed(storedPassword)
                    && !upgradePassword(connection, user.id(), storedPassword, password)) {
                return null;
            }
            return user;
        } catch (SQLException error) {
            throw new IllegalStateException("Unable to query authentication database", error);
        }
    }

    private void insertSeedUser(
            Connection connection,
            String username,
            String password,
            String displayName,
            String role) throws SQLException {
        String sql = "INSERT OR IGNORE INTO legacy_users"
                + "(username,password,display_name,role) VALUES (?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, PasswordHasher.hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    private boolean upgradePassword(
            Connection connection,
            long userId,
            String legacyPassword,
            String password) throws SQLException {
        String sql = "UPDATE legacy_users SET password=? WHERE id=? AND password=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, PasswordHasher.hash(password));
            statement.setLong(2, userId);
            statement.setString(3, legacyPassword);
            return statement.executeUpdate() == 1;
        }
    }

    private record LegacyPassword(long userId, String password) {}
}
