package com.example.store.repository;

import com.example.store.model.CustomerSubscription;
import com.example.store.model.SubcriptionPlan;
import org.springframework.stereotype.Repository;
import util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SubscriptionRepository {

    // ─── GET ALL PLANS ────────────────────────────────────────────
    public List<SubcriptionPlan> findAllPlans() {
        List<SubcriptionPlan> plans = new ArrayList<>();
        String sql = "SELECT * FROM subscription_plans ORDER BY price ASC";

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                plans.add(new SubcriptionPlan(
                        rs.getInt("plan_id"),
                        rs.getString("plan_name"),
                        rs.getDouble("price"),
                        rs.getInt("discount_pct"),
                        rs.getString("stripe_price_id"),
                        rs.getString("description")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return plans;
    }

    // ─── GET PLAN BY ID ───────────────────────────────────────────
    public SubcriptionPlan findPlanById(Integer planId) {
        String sql = "SELECT * FROM subscription_plans WHERE plan_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, planId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new SubcriptionPlan(
                        rs.getInt("plan_id"),
                        rs.getString("plan_name"),
                        rs.getDouble("price"),
                        rs.getInt("discount_pct"),
                        rs.getString("stripe_price_id"),
                        rs.getString("description")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ─── GET ACTIVE SUBSCRIPTION BY USERNAME ──────────────────────
    public CustomerSubscription findActiveByUsername(String username) {
        String sql = """
                SELECT cs.*, sp.plan_name, sp.discount_pct
                FROM customer_subscription cs
                JOIN subscription_plans sp ON cs.plan_id = sp.plan_id
                WHERE cs.username = ? AND cs.status = 'ACTIVE'
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) return mapSubscription(rs);

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ─── GET ALL SUBSCRIPTIONS (admin) ────────────────────────────
    public List<CustomerSubscription> findAll() {
        List<CustomerSubscription> list = new ArrayList<>();
        String sql = """
                SELECT cs.*, sp.plan_name, sp.discount_pct
                FROM customer_subscription cs
                JOIN subscription_plans sp ON cs.plan_id = sp.plan_id
                ORDER BY cs.started_at DESC
                """;

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) list.add(mapSubscription(rs));

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ─── SAVE NEW SUBSCRIPTION ────────────────────────────────────
    public Integer save(String username, Integer planId,
                        String stripeSubId, String stripeCustomerId) {
        String sql = """
                INSERT INTO customer_subscription 
                (username, plan_id, stripe_sub_id, stripe_customer_id, status)
                VALUES (?, ?, ?, ?, 'PENDING')
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, username);
            ps.setInt(2,    planId);
            ps.setString(3, stripeSubId);
            ps.setString(4, stripeCustomerId);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ─── UPDATE STATUS ────────────────────────────────────────────
    public void updateStatus(Integer subId, String status) {
        String sql = "UPDATE customer_subscription SET status = ? WHERE sub_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, subId);
            ps.executeUpdate();
            System.out.println(" Subscription #" + subId + " status → " + status);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ─── UPDATE STATUS BY STRIPE SUB ID ──────────────────────────
    public void updateStatusByStripeSubId(String stripeSubId, String status) {
        String sql = "UPDATE customer_subscription SET status = ? WHERE stripe_sub_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setString(2, stripeSubId);
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ─── CANCEL SUBSCRIPTION ─────────────────────────────────────
    public void cancel(Integer subId) {
        String sql = """
                UPDATE customer_subscription 
                SET status = 'CANCELLED', cancelled_at = GETDATE() 
                WHERE sub_id = ?
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, subId);
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ─── GET DISCOUNT FOR USERNAME ────────────────────────────────
    public Integer getDiscountForUser(String username) {
        CustomerSubscription sub = findActiveByUsername(username);
        return sub != null ? sub.getDiscount_pct() : 0;
    }

    // ─── HELPER ───────────────────────────────────────────────────
    private CustomerSubscription mapSubscription(ResultSet rs) throws SQLException {
        return new CustomerSubscription(
                rs.getInt("sub_id"),
                rs.getString("username"),
                rs.getInt("plan_id"),
                rs.getString("plan_name"),
                rs.getInt("discount_pct"),
                rs.getString("stripe_sub_id"),
                rs.getString("stripe_customer_id"),
                rs.getString("status"),
                rs.getString("started_at"),
                rs.getString("expires_at"),
                rs.getString("cancelled_at")
        );
    }

    public void updateStripeSubId(Integer subId, String stripeSubId) {
        String sql = "UPDATE customer_subscription SET stripe_sub_id = ? WHERE sub_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stripeSubId);
            ps.setInt(2, subId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateStripeCustomerId(Integer subId, String stripeCustomerId) {
        String sql = "UPDATE customer_subscription SET stripe_customer_id = ? WHERE sub_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, stripeCustomerId);
            ps.setInt(2, subId);
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}