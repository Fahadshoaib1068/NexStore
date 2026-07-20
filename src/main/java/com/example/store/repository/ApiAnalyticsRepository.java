package com.example.store.repository;

import org.springframework.stereotype.Repository;
import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ApiAnalyticsRepository {
    public Map<String, long[]> findAll() {
        Map<String, long[]> result = new HashMap<>();
        String sql = "SELECT api_name, hit_count, error_count, total_duration_ms FROM api_hits";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.put(rs.getString("api_name"), new long[]{
                        rs.getLong("hit_count"),
                        rs.getLong("error_count"),
                        rs.getLong("total_duration_ms")
                });
            }
        } catch (SQLException e) {
            System.out.println("Could not load saved analytics (table may not exist yet)");
        }
        return result;
    }

    public void addToCounts(String apiName, long deltaHits, long deltaErrors, long deltaDuration) throws SQLException {
        String sql = """
            MERGE INTO api_hits AS target
            USING (SELECT ? AS api_name) AS src
            ON target.api_name = src.api_name
            WHEN MATCHED THEN UPDATE SET
                hit_count = hit_count + ?,
                error_count = error_count + ?,
                total_duration_ms = total_duration_ms + ?
            WHEN NOT MATCHED THEN
                INSERT (api_name, hit_count, error_count, total_duration_ms)
                VALUES (?, ?, ?, ?);
            """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, apiName);
            ps.setLong(2, deltaHits);
            ps.setLong(3, deltaErrors);
            ps.setLong(4, deltaDuration);
            ps.setString(5, apiName);
            ps.setLong(6, deltaHits);
            ps.setLong(7, deltaErrors);
            ps.setLong(8, deltaDuration);
            ps.executeUpdate();
        }
    }

    // ─── WRITE: log call to master DB ────────────────────────────
    public void logCall(String apiName, LocalDateTime calledAt) throws SQLException {
        String sql = "INSERT INTO api_call_log (api_name, called_at) VALUES (?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, apiName);
            ps.setTimestamp(2, Timestamp.valueOf(calledAt));
            ps.executeUpdate();
        }
    }

    // ─── READ: bucketed timeline from master DB ──────────────────
    public List<Map<String, Object>> getTimeline(LocalDateTime windowStart, int bucketSeconds) throws SQLException {
        String sql = """
            SELECT bucket, api_name, COUNT(*) AS cnt
            FROM (
               SELECT DATEDIFF(SECOND, ?, called_at) / ? AS bucket, api_name
               FROM api_call_log
            WHERE called_at >= ?
            ) AS bucketed
            GROUP BY bucket, api_name
            ORDER BY bucket
            """;

        Map<Integer, Map<String, Object>> buckets = new LinkedHashMap<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(windowStart));
            ps.setInt(2, bucketSeconds);
            ps.setTimestamp(3, Timestamp.valueOf(windowStart));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int bucket = rs.getInt("bucket");
                    String api = rs.getString("api_name");
                    int cnt = rs.getInt("cnt");

                    Map<String, Object> b = buckets.computeIfAbsent(bucket, k -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("bucket", k);
                        m.put("apis", new ArrayList<String>());
                        m.put("count", 0);
                        return m;
                    });
                    ((List<String>) b.get("apis")).add(api);
                    b.put("count", (int) b.get("count") + cnt);
                }
            }
        }
        return new ArrayList<>(buckets.values());
    }

    public void deleteOlderThan(LocalDateTime cutoff) throws SQLException {
        String sql = "DELETE FROM api_call_log WHERE called_at < ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(cutoff));
            int deleted = ps.executeUpdate();
            if (deleted > 0) System.out.println("Purged " + deleted + " old api_call_log rows");
        }
    }
}