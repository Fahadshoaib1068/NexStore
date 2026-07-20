package com.example.store.repository;

import com.example.store.config.CacheService;
import com.example.store.model.Order;
import com.example.store.model.OrderDetail;
import com.example.store.model.OrderItem;
import org.springframework.stereotype.Repository;
import util.DBConnection;

import java.sql.*;
import java.util.*;

@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final CacheService cacheService;

    public OrderRepositoryImpl(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    // ─── FIND ALL Orders (READ from master DB) ──────────────────────
    @Override
    public List<Order> findAll() {
        String cacheKey = "orders:all";

        Object cached = cacheService.get(cacheKey);
        if (cached != null) {
            System.out.println(" Cache HIT — orders:all");
            return (List<Order>) cached;
        }

        System.out.println(" Cache MISS — fetching orders from DB");
        List<Order> orders = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM orders")) {

            while (rs.next()) {
                orders.add(new Order(
                        rs.getInt("order_id"),
                        rs.getInt("customer_id"),
                        rs.getString("order_date"),
                        rs.getDouble("total_amount"),
                        rs.getString("payment_status"),
                        rs.getString("customer_username"),
                        rs.getString("stripe_session_id")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        cacheService.put(cacheKey, orders);
        return orders;
    }

    // ─── FIND Order By ID (READ from master DB) ─────────────────────
    @Override
    public OrderDetail findById(Integer id) {
        String cacheKey = "orders:" + id;

        Object cached = cacheService.get(cacheKey);
        if (cached != null) {
            System.out.println(" Cache HIT — orders:" + id);
            return (OrderDetail) cached;
        }

        System.out.println(" Cache MISS — fetching order detail from DB");
        String sql = """
                SELECT o.order_id, o.order_date, o.total_amount,
                       c.first_name, c.last_name, c.email, o.payment_status
                FROM orders o
                JOIN customer c ON o.customer_id = c.customer_id
                WHERE o.order_id = ?
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                List<OrderItem> items = findOrderItems(conn, id);
                OrderDetail detail = new OrderDetail(
                        rs.getInt("order_id"),
                        rs.getString("order_date"),
                        rs.getDouble("total_amount"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getString("payment_status"),
                        items
                );
                cacheService.put(cacheKey, detail);
                return detail;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ─── HELPER: get order items (READ from replica) ──────────────
    private List<OrderItem> findOrderItems(Connection conn, Integer orderId) throws SQLException {
        List<OrderItem> items = new ArrayList<>();
        String sql = """
SELECT
    oi.oi_id,
    oi.order_id,
    oi.item_id,
    i.item_name,
    oi.quantity,
    oi.unit_price
FROM order_item oi
JOIN item i
    ON oi.item_id = i.item_id
WHERE oi.order_id = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                items.add(new OrderItem(
                        rs.getInt("oi_id"),
                rs.getInt("order_id"),
                rs.getInt("item_id"),
                rs.getString("item_name"),
                rs.getInt("quantity"),
                rs.getDouble("unit_price")
                ));
            }
        }
        return items;
    }

    // ─── SAVE ORDER (WRITE to master DB) ────────────────────────────
    @Override
    public Integer save(Order order) {
        String sql =
                "INSERT INTO orders (customer_id, total_amount, payment_status, customer_username) " +
                        "VALUES (?, 0, 'UNPAID', ?)";
        Integer generatedId = null;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, order.getCustomer_id());
            ps.setString(2, order.getCustomer_username());
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) generatedId = keys.getInt(1);
            System.out.println("Order #" + generatedId + " created");

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }

        if (generatedId != null) {
            cacheService.evict("orders:all");
        }

        return generatedId;
    }

    @Override
    public void addOrderItem(Integer order_id, Integer item_id, Integer quantity) {
        String getItem    = "SELECT price, stock_quantity FROM item WHERE item_id = ?";
        String insertItem = "INSERT INTO order_item (order_id, item_id, quantity, unit_price) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement getPs = conn.prepareStatement(getItem)) {

            getPs.setInt(1, item_id);
            ResultSet rs = getPs.executeQuery();

            if (rs.next()) {
                int availableStock = rs.getInt("stock_quantity");
                double price       = rs.getDouble("price");

                if (quantity > availableStock) {
                    throw new RuntimeException(
                            "Insufficient stock for item " + item_id +
                                    ". Requested: " + quantity + ", Available: " + availableStock
                    );
                }

                // Insert into master order_item
                try (PreparedStatement insertPs = conn.prepareStatement(insertItem)) {
                    insertPs.setInt(1, order_id);
                    insertPs.setInt(2, item_id);
                    insertPs.setInt(3, quantity);
                    insertPs.setDouble(4, price);
                    insertPs.executeUpdate();
                    System.out.println("order_item inserted");
                }

                // Reduce stock and update order total
                String syncStock = "UPDATE item SET stock_quantity = stock_quantity - ? WHERE item_id = ?";
                try (PreparedStatement syncPs = conn.prepareStatement(syncStock)) {
                    syncPs.setInt(1, quantity);
                    syncPs.setInt(2, item_id);
                    syncPs.executeUpdate();
                }

                String syncTotal = """
                        UPDATE orders SET total_amount = (
                            SELECT ISNULL(SUM(quantity * unit_price), 0)
                            FROM order_item WHERE order_id = ?
                        ) WHERE order_id = ?
                        """;
                try (PreparedStatement totalPs = conn.prepareStatement(syncTotal)) {
                    totalPs.setInt(1, order_id);
                    totalPs.setInt(2, order_id);
                    totalPs.executeUpdate();
                }

                System.out.println("order_item, stock, and total updated");

                cacheService.evict("orders:all");
                cacheService.evict("orders:" + order_id);
                cacheService.evict("items:all");
                cacheService.evict("items:" + item_id);
                System.out.println("All caches evicted");

            } else {
                throw new RuntimeException("Item not found: " + item_id);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Database error: " + e.getMessage());
        }
    }

    // ─── DELETE ORDER (WRITE to master DB) ──────────────────────────
    @Override
    public void delete(Integer id) {
        // Delete order_items first (foreign key constraint)
        String deleteItems = "DELETE FROM order_item WHERE order_id = ?";
        String deleteOrder = "DELETE FROM orders WHERE order_id = ?";

        try (Connection conn = DBConnection.getConnection()) {
            PreparedStatement ps1 = conn.prepareStatement(deleteItems);
            ps1.setInt(1, id);
            ps1.executeUpdate();

            PreparedStatement ps2 = conn.prepareStatement(deleteOrder);
            ps2.setInt(1, id);
            ps2.executeUpdate();
            System.out.println(" Order #" + id + " deleted");

        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        cacheService.evict("orders:all");
        cacheService.evict("orders:" + id);
    }

    @Override
    public void PaymentStatus(Integer orderId, String status) {

        String sql = """
            UPDATE orders
            SET payment_status = ?
            WHERE order_id = ?
            """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, orderId);
            ps.executeUpdate();

            System.out.println("Payment status updated");

        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        // Clear cache
        cacheService.evict("orders:all");
        cacheService.evict("orders:" + orderId);
    }

    @Override
    public List<Order> findByUsername(String username) {
        String cacheKey = "orders:user:" + username;

        Object cached = cacheService.get(cacheKey);
        if (cached != null) {
            System.out.println("Cache HIT — orders:user:" + username);
            return (List<Order>) cached;
        }

        List<Order> orders = new ArrayList<>();
        String sql = "SELECT * FROM orders WHERE customer_username = ? ORDER BY order_date DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                orders.add(new Order(
                        rs.getInt("order_id"),
                        rs.getInt("customer_id"),
                        rs.getString("order_date"),
                        rs.getDouble("total_amount"),
                        rs.getString("payment_status"),
                        rs.getString("customer_username"),
                        rs.getString("stripe_session_id")
                ));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        cacheService.put(cacheKey, orders);
        return orders;
    }

    @Override
    public void updateStripeSession(Integer orderId, String sessionId){
        String sql = "UPDATE orders SET stripe_session_id = ? WHERE order_id = ?";
        try(Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setInt(2, orderId);
            ps.executeUpdate();

            //Evict cache
            cacheService.evict("orders:all");
            cacheService.evict("orders:" + orderId);
            System.out.println("Order #" + orderId + " updated");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updatePaymentStatus(Integer orderId, String status) {
        String sql = "UPDATE orders SET payment_status = ? WHERE order_id = ?";
        try(Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, orderId);
            ps.executeUpdate();

            // cache evict
            cacheService.evict("orders:all");
            cacheService.evict("orders:" + orderId);
            System.out.println("Order #" + orderId + " updated Payment Status");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void applyDiscount(Integer orderId, Integer discountPct) {
        String sql = """
            UPDATE orders 
            SET total_amount = total_amount * (1 - ? / 100.0)
            WHERE order_id = ?
            """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, discountPct);
            ps.setInt(2, orderId);
            ps.executeUpdate();
            System.out.println("Discount applied to order #" + orderId);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}

