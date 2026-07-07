package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;

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

    // in DBConnection.java
    public static void executeOnBoth(String sql, SqlBinder binder) throws SQLException {
        for (Connection conn : new Connection[]{ getConnection(), getReplicaConnection() }) {
            try (conn; PreparedStatement ps = conn.prepareStatement(sql)) {
                binder.bind(ps);
                ps.executeUpdate();
            }
        }
    }

    @FunctionalInterface
    public interface SqlBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    // Add alongside executeOnBoth
    public static void executeWithRetry(Runnable action, int maxAttempts) {
        SQLException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                action.run();
                return;
            } catch (RuntimeException e) {
                if (e.getCause() instanceof SQLException sqlEx && "40001".equals(sqlEx.getSQLState())) {
                    System.out.println("Deadlock detected, retrying (" + attempt + "/" + maxAttempts + ")");
                    try { Thread.sleep(150L * attempt); } catch (InterruptedException ignored) {}
                    last = sqlEx;
                } else {
                    throw e;
                }
            }
        }
        throw new RuntimeException("Operation failed after " + maxAttempts + " attempts due to repeated deadlocks", last);
    }
}