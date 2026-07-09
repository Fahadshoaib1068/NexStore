package com.example.store.repository;
import org.springframework.stereotype.Repository;
import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.HashMap;

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

    // ─── SAVE NEW COUNTS (adds to existing totals) ────────────────
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

        DBConnection.executeOnBoth(sql, ps -> {
            ps.setString(1, apiName);
            ps.setLong(2, deltaHits);
            ps.setLong(3, deltaErrors);
            ps.setLong(4, deltaDuration);
            ps.setString(5, apiName);
            ps.setLong(6, deltaHits);
            ps.setLong(7, deltaErrors);
            ps.setLong(8, deltaDuration);
        });
    }}
