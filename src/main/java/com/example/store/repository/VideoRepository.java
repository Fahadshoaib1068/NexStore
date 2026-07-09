package com.example.store.repository;

import com.example.store.model.VideoProcessed;
import com.example.store.model.VideoUpload;
import org.springframework.stereotype.Repository;
import util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class VideoRepository {

    public Integer save(String originalName, String filePath, String uploadedBy) {
        String sql = "INSERT INTO video_upload (original_name, file_path, uploaded_by) VALUES (?, ?, ?)";
        Integer generatedId = null;

        // 1. Write to MASTER
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, originalName);
            ps.setString(2, filePath);
            ps.setString(3, uploadedBy);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) generatedId = keys.getInt(1);
            System.out.println("Video saved to master with id: " + generatedId);

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }

        // 2. Sync to REPLICA — key fix: use same connection for IDENTITY_INSERT
        if (generatedId != null) {
            try (Connection replicaConn = DBConnection.getReplicaConnection()) {

                // Must use same connection for all 3 statements
                replicaConn.createStatement()
                        .execute("SET IDENTITY_INSERT video_upload ON");

                try (PreparedStatement ps = replicaConn.prepareStatement(
                        "INSERT INTO video_upload (video_id, original_name, file_path, uploaded_by) VALUES (?, ?, ?, ?)")) {

                    ps.setInt(1,    generatedId);
                    ps.setString(2, originalName);
                    ps.setString(3, filePath);
                    ps.setString(4, uploadedBy);
                    ps.executeUpdate();
                }

                replicaConn.createStatement()
                        .execute("SET IDENTITY_INSERT video_upload OFF");

                System.out.println(" Video synced to replica with id: " + generatedId);

            } catch (SQLException e) {
                System.out.println(" Failed to sync video to replica: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return generatedId;
    }

    public List<VideoUpload> findPending() {
        return queryList("SELECT * FROM video_upload WHERE status = 'PENDING' ORDER BY uploaded_at DESC");
    }

    public List<VideoUpload> findAll() {
        return queryList("SELECT * FROM video_upload ORDER BY uploaded_at DESC");
    }

    public List<VideoUpload> findCompleted() {
        return queryList("SELECT * FROM video_upload WHERE status = 'COMPLETED' ORDER BY uploaded_at DESC");
    }

    private List<VideoUpload> queryList(String sql) {
        List<VideoUpload> list = new ArrayList<>();
        try (Connection conn = DBConnection.getReplicaConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(mapUpload(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public VideoUpload findById(Integer videoId) {
        String sql = "SELECT * FROM video_upload WHERE video_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, videoId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapUpload(rs);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void updateStatus(Integer videoId, String status) {
        try {
            DBConnection.executeOnBoth(
                    "UPDATE video_upload SET status = ? WHERE video_id = ?",
                    ps -> { ps.setString(1, status); ps.setInt(2, videoId); }
            );
            System.out.println("Video #" + videoId + " status -> " + status);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveProcessed(Integer videoId, String quality, String filePath) {
        try {
            DBConnection.executeOnBoth(
                    "INSERT INTO video_processed (video_id, quality, file_path) VALUES (?, ?, ?)",
                    ps -> { ps.setInt(1, videoId); ps.setString(2, quality); ps.setString(3, filePath); }
            );
            System.out.println("Saved processed video: " + quality + " for video #" + videoId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<VideoProcessed> findProcessedByVideoId(Integer videoId) {
        List<VideoProcessed> list = new ArrayList<>();
        String sql = "SELECT * FROM video_processed WHERE video_id = ? ORDER BY quality";
        try (Connection conn = DBConnection.getReplicaConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, videoId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapProcessed(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean markProcessing(Integer videoId) {
        String sql = "UPDATE video_upload SET status='PROCESSING' WHERE video_id=? AND status='PENDING'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, videoId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (Connection replicaConn = DBConnection.getReplicaConnection();
                     PreparedStatement replicaPs = replicaConn.prepareStatement(sql)) {
                    replicaPs.setInt(1, videoId);
                    replicaPs.executeUpdate();
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private VideoUpload mapUpload(ResultSet rs) throws SQLException {
        return new VideoUpload(
                rs.getInt("video_id"), rs.getString("original_name"), rs.getString("file_path"),
                rs.getString("status"), rs.getString("uploaded_at"), rs.getString("uploaded_by"),
                rs.getString("thumbnail_path")
        );
    }

    private VideoProcessed mapProcessed(ResultSet rs) throws SQLException {
        return new VideoProcessed(
                rs.getInt("processed_id"), rs.getInt("video_id"), rs.getString("quality"),
                rs.getString("file_path"), rs.getString("processed_at")
        );
    }

    public void updateThumbnail(Integer videoId, String thumbnailPath) {
        try {
            DBConnection.executeOnBoth(
                    "UPDATE video_upload SET thumbnail_path = ? WHERE video_id = ?",
                    ps -> { ps.setString(1, thumbnailPath); ps.setInt(2, videoId); }
            );
            System.out.println("Thumbnail updated for video #" + videoId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}