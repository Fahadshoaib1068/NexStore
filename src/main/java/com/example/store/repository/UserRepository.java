package com.example.store.repository;

import com.example.store.model.User;
import util.DBConnection;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class UserRepository {

    public User findByUsername(String username) {
        String sql = """
                SELECT u.userid, u.username, u.email, u.passwordhash,
                       u.customerid, u.created, r.rolename
                FROM users u
                JOIN user_roles ur ON u.userid = ur.userid
                JOIN roles r       ON ur.roleid = r.roleid
                WHERE u.username = ?
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            User user = null;
            List<String> roles = new ArrayList<>();
            while (rs.next()) {
                if (user == null) {
                    user = new User(
                            rs.getInt("userid"), rs.getString("username"), rs.getString("email"),
                            rs.getString("passwordhash"), rs.getInt("customerid"), rs.getString("created"), null
                    );
                }
                roles.add(rs.getString("rolename"));
            }
            if (user != null) user.setRoles(roles);
            return user;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public User findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new User(
                        rs.getInt("userid"), rs.getString("username"), rs.getString("email"),
                        rs.getString("passwordhash"), rs.getInt("customerid"), rs.getString("created"), null
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean existsByUsername(String username) {
        return exists("SELECT COUNT(*) FROM users WHERE username = ?", username);
    }

    public boolean existsByEmail(String email) {
        return exists("SELECT COUNT(*) FROM users WHERE email = ?", email);
    }

    private boolean exists(String sql, String value) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // ─── SAVE NEW USER (WRITE master, then sync replica with same ID) ──
    public Integer save(String username, String email, String passwordHash) {
        String sql = "INSERT INTO users (username, email, passwordhash) VALUES (?, ?, ?)";
        Integer generatedId = null;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) generatedId = keys.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }

        if (generatedId != null) {
            try (Connection replicaConn = DBConnection.getReplicaConnection()) {
                replicaConn.createStatement().execute("SET IDENTITY_INSERT users ON");
                String replicaSql = "INSERT INTO users (userid, username, email, passwordhash) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = replicaConn.prepareStatement(replicaSql)) {
                    ps.setInt(1, generatedId);
                    ps.setString(2, username);
                    ps.setString(3, email);
                    ps.setString(4, passwordHash);
                    ps.executeUpdate();
                }
                replicaConn.createStatement().execute("SET IDENTITY_INSERT users OFF");
                System.out.println("User #" + generatedId + " synced to replica");
            } catch (SQLException e) {
                System.out.println("Failed to sync user to replica: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return generatedId;
    }

    public void assignRole(Integer userId, Integer roleId) {
        try {
            DBConnection.executeOnBoth(
                    "INSERT INTO user_roles (userid, roleid) VALUES (?, ?)",
                    ps -> { ps.setInt(1, userId); ps.setInt(2, roleId); }
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Integer getRoleIdByName(String roleName) {
        String sql = "SELECT roleid FROM roles WHERE rolename = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roleName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("roleid");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void updateUserRole(Integer userId, Integer newRoleId) {
        try {
            DBConnection.executeOnBoth(
                    "UPDATE user_roles SET roleid = ? WHERE userid = ?",
                    ps -> { ps.setInt(1, newRoleId); ps.setInt(2, userId); }
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}