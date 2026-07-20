package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String DB_URL =
            "jdbc:sqlserver://localhost:1433;databaseName=Ecommerce;encrypt=true;trustServerCertificate=true;";

    private static final String DEFAULT_USER = "Fahad";
    private static final String DEFAULT_PASS = "1234";

    private DBConnection() {}

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DEFAULT_USER, DEFAULT_PASS);
    }
}