package com.vertex.cv_app.utils;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class JWTTokenManager {
    private static final String DB_URL = "jdbc:sqlite:app_data.db";
    private Connection connection;

    public JWTTokenManager() {
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            createTokenTable();
            System.out.println("Database connected successfully!");
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private void createTokenTable() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS tokens (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                token_type VARCHAR(50) NOT NULL,
                token_value TEXT NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                expires_at DATETIME,
                is_active BOOLEAN DEFAULT 1
            )
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("Tokens table created/verified successfully!");
        } catch (SQLException e) {
            System.err.println("Error creating table: " + e.getMessage());
        }
    }

    // Save JWT token
    public boolean saveToken(String tokenType, String tokenValue, String expiresAt) {
        String insertSQL = """
            INSERT INTO tokens (token_type, token_value, expires_at) 
            VALUES (?, ?, ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
            pstmt.setString(1, tokenType);
            pstmt.setString(2, tokenValue);
            pstmt.setString(3, expiresAt);

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Error saving token: " + e.getMessage());
            return false;
        }
    }

    public String getToken(String tokenType) {
        String selectSQL = """
            SELECT token_value FROM tokens 
            WHERE token_type = ? AND is_active = 1 
            ORDER BY created_at DESC 
            LIMIT 1
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(selectSQL)) {
            pstmt.setString(1, tokenType);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("token_value");
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving token: " + e.getMessage());
        }
        return null;
    }

    public void deactivateTokens(String tokenType) {
        String updateSQL = "UPDATE tokens SET is_active = 0 WHERE token_type = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(updateSQL)) {
            pstmt.setString(1, tokenType);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error deactivating tokens: " + e.getMessage());
        }
    }

    public boolean saveNewToken(String tokenType, String tokenValue, String expiresAt) {
        deactivateTokens(tokenType); // Deactivate old tokens first
        return saveToken(tokenType, tokenValue, expiresAt);
    }

    public boolean hasActiveToken(String tokenType) {
        return getToken(tokenType) != null;
    }

    public boolean clearAllTokens() {
        String deleteSQL = "DELETE FROM tokens";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(deleteSQL);
            return true;
        } catch (SQLException e) {
            System.err.println("Error clearing tokens: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed.");
            }
        } catch (SQLException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        JWTTokenManager tokenManager = new JWTTokenManager();

        String jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
        String expiresAt = LocalDateTime.now().plusHours(24)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        boolean saved = tokenManager.saveNewToken("access_token", jwtToken, expiresAt);
        if (saved) {
            System.out.println("Token saved successfully!");
        }

        String retrievedToken = tokenManager.getToken("access_token");
        if (retrievedToken != null) {
            System.out.println("Retrieved token: " + retrievedToken.substring(0, 20) + "...");
        }

        boolean hasToken = tokenManager.hasActiveToken("access_token");
        System.out.println("Has active token: " + hasToken);

        tokenManager.close();
    }
}