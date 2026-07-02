package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String MASTER_URL =
            "jdbc:sqlserver://localhost:1433;databaseName=Ecommerce;encrypt=true;trustServerCertificate=true;";
    private static final String REPLICA_URL =
            "jdbc:sqlserver://localhost:1433;databaseName=Ecommerce Replica;encrypt=true;trustServerCertificate=true;";

    private static final String DEFAULT_USER = "Fahad";
    private static final String DEFAULT_PASS = "1234";

    private DBConnection() {}

    // ─── MASTER — use for all WRITES ────────────────────────────
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(MASTER_URL, DEFAULT_USER, DEFAULT_PASS);
    }

    // ─── REPLICA — use for all READS ────────────────────────────
    public static Connection getReplicaConnection() throws SQLException {
        return DriverManager.getConnection(REPLICA_URL, DEFAULT_USER, DEFAULT_PASS);
    }
}