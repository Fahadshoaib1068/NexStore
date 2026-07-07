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

    // ─── LAZY: Find All Orders (READ from replica) ────────────────
    @Override
    public List<Order> findAll() {
        String cacheKey = "orders:all";

        Object cached = cacheService.get(cacheKey);
        if (cached != null) {
            System.out.println(" Cache HIT — orders:all");
            return (List<Order>) cached;
        }

        System.out.println(" Cache MISS — fetching orders from REPLICA");
        List<Order> orders = new ArrayList<>();
        try (Connection conn = DBConnection.getReplicaConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM orders")) {

            while (rs.next()) {
                orders.add(new Order(
                        rs.getInt("order_id"),
                        rs.getInt("customer_id"),
                        rs.getString("order_date"),
                        rs.getDouble("total_amount")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        cacheService.put(cacheKey, orders);
        return orders;
    }

    // ─── EAGER: Find Order By ID (READ from replica) ──────────────
    @Override
    public OrderDetail findById(Integer id) {
        String cacheKey = "orders:" + id;

        Object cached = cacheService.get(cacheKey);
        if (cached != null) {
            System.out.println(" Cache HIT — orders:" + id);
            return (OrderDetail) cached;
        }

        System.out.println(" Cache MISS — fetching order detail from REPLICA");
        String sql = """
                SELECT o.order_id, o.order_date, o.total_amount,
                       c.first_name, c.last_name, c.email
                FROM orders o
                JOIN customer c ON o.customer_id = c.customer_id
                WHERE o.order_id = ?
                """;

        try (Connection conn = DBConnection.getReplicaConnection();
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
                SELECT oi.oi_id, oi.order_id, oi.item_id,
                       i.item_name, oi.quantity, oi.unit_price
                FROM order_item oi
                JOIN item i ON oi.item_id = i.item_id
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

    // ─── SAVE ORDER (WRITE to master + sync to replica) ───────────
    @Override
    public Integer save(Order order) {
        String sql = "INSERT INTO orders (customer_id, total_amount) VALUES (?, 0)";
        Integer generatedId = null;

        // 1. Write to MASTER
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, order.getCustomer_id());
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) generatedId = keys.getInt(1);
            System.out.println("Order #" + generatedId + " created in master");

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }

        // 2. Sync to REPLICA with same order_id
        if (generatedId != null) {
            try (Connection replicaConn = DBConnection.getReplicaConnection()) {

                // Turn on IDENTITY_INSERT so we can specify the order_id
                replicaConn.createStatement().execute("SET IDENTITY_INSERT orders ON");

                PreparedStatement replicaPs = replicaConn.prepareStatement(
                        "INSERT INTO orders (order_id, customer_id, total_amount) VALUES (?, ?, 0)"
                );
                replicaPs.setInt(1, generatedId);
                replicaPs.setInt(2, order.getCustomer_id());
                replicaPs.executeUpdate();

                replicaConn.createStatement().execute("SET IDENTITY_INSERT orders OFF");

                System.out.println("Order #" + generatedId + " synced to replica");

            } catch (SQLException e) {
                System.out.println("Failed to sync order to replica: " + e.getMessage());
                e.printStackTrace();
            }

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

                // 1. Insert into MASTER order_item (fires master's stock-reduction trigger)
                try (PreparedStatement insertPs = conn.prepareStatement(insertItem)) {
                    insertPs.setInt(1, order_id);
                    insertPs.setInt(2, item_id);
                    insertPs.setInt(3, quantity);
                    insertPs.setDouble(4, price);
                    insertPs.executeUpdate();
                    System.out.println("order_item inserted into master");
                }

                // 2. Sync order_item to REPLICA + manually reduce replica stock (no trigger there)
                //    + recompute order total on replica — wrapped with deadlock retry
                final double finalPrice = price;
                DBConnection.executeWithRetry(() -> {
                    try (Connection replicaConn = DBConnection.getReplicaConnection()) {

                        try (PreparedStatement replicaPs = replicaConn.prepareStatement(insertItem)) {
                            replicaPs.setInt(1, order_id);
                            replicaPs.setInt(2, item_id);
                            replicaPs.setInt(3, quantity);
                            replicaPs.setDouble(4, finalPrice);
                            replicaPs.executeUpdate();
                        }

                        String syncStock = "UPDATE item SET stock_quantity = stock_quantity - ? WHERE item_id = ?";
                        try (PreparedStatement syncPs = replicaConn.prepareStatement(syncStock)) {
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
                        try (PreparedStatement totalPs = replicaConn.prepareStatement(syncTotal)) {
                            totalPs.setInt(1, order_id);
                            totalPs.setInt(2, order_id);
                            totalPs.executeUpdate();
                        }

                        System.out.println("order_item, stock, and total synced to replica");

                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }, 3);

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

    // ─── DELETE ORDER (WRITE to master + sync to replica) ─────────
    @Override
    public void delete(Integer id) {
        // Delete order_items first (foreign key constraint)
        String deleteItems = "DELETE FROM order_item WHERE order_id = ?";
        String deleteOrder = "DELETE FROM orders WHERE order_id = ?";

        // 1. Delete from MASTER
        try (Connection conn = DBConnection.getConnection()) {
            PreparedStatement ps1 = conn.prepareStatement(deleteItems);
            ps1.setInt(1, id);
            ps1.executeUpdate();

            PreparedStatement ps2 = conn.prepareStatement(deleteOrder);
            ps2.setInt(1, id);
            ps2.executeUpdate();
            System.out.println(" Order #" + id + " deleted from master");

        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        // 2. Sync delete to REPLICA
        try (Connection replicaConn = DBConnection.getReplicaConnection()) {
            PreparedStatement ps1 = replicaConn.prepareStatement(deleteItems);
            ps1.setInt(1, id);
            ps1.executeUpdate();

            PreparedStatement ps2 = replicaConn.prepareStatement(deleteOrder);
            ps2.setInt(1, id);
            ps2.executeUpdate();
            System.out.println(" Order #" + id + " delete synced to replica");

        } catch (SQLException e) {
            System.out.println(" Failed to sync order delete to replica: " + e.getMessage());
            e.printStackTrace();
        }

        cacheService.evict("orders:all");
        cacheService.evict("orders:" + id);
    }

}