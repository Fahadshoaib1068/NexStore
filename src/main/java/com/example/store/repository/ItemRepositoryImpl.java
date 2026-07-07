package com.example.store.repository;

import com.example.store.config.CacheService;
import com.example.store.model.Item;
import org.springframework.stereotype.Repository;
import util.DBConnection;

import java.sql.*;
import java.util.*;

@Repository
public class ItemRepositoryImpl implements ItemRepository {

    private final CacheService cacheService;

    public ItemRepositoryImpl(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    // ─── HELPER ───────────────────────────────────────────────────
    private Item mapRow(ResultSet rs) throws SQLException {
        return new Item(
                rs.getInt("item_id"),
                rs.getString("item_name"),
                rs.getString("item_description"),
                rs.getDouble("price"),
                rs.getInt("stock_quantity")
        );
    }

    // ─── FIND ALL (READ → replica, with cache) ─────────────────────
    @Override
    public List<Item> findAll() {
        String cacheKey = "items:all";

        Object cached = cacheService.get(cacheKey);
        if (cached != null) {
            System.out.println("Cache HIT — items:all");
            return (List<Item>) cached;
        }

        System.out.println("Cache MISS — fetching from REPLICA DB");
        List<Item> items = new ArrayList<>();
        try (Connection conn = DBConnection.getReplicaConnection();   // ← READ from replica
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM item")) {

            while (rs.next()) items.add(mapRow(rs));

        } catch (SQLException e) {
            e.printStackTrace();
        }

        cacheService.put(cacheKey, items);
        return items;
    }

    // ─── FIND BY ID (READ → replica, with cache) ───────────────────
    @Override
    public Item findById(Integer id) {
        String cacheKey = "items:" + id;

        Object cached = cacheService.get(cacheKey);
        if (cached != null) {
            System.out.println("Cache HIT — items:" + id);
            return (Item) cached;
        }

        System.out.println("Cache MISS — fetching from REPLICA DB");
        try (Connection conn = DBConnection.getReplicaConnection();   // ← READ from replica
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM item WHERE item_id = ?")) {

            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                Item item = mapRow(rs);
                cacheService.put(cacheKey, item);
                return item;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ─── SAVE (WRITE → master, then sync to replica) ───────────────
    @Override
    public void save(Item item) {
        String sql = "INSERT INTO item (item_id, item_name, item_description, price, stock_quantity) VALUES (?, ?, ?, ?, ?)";

        // 1. Write to MASTER
        try (Connection conn = DBConnection.getConnection();          // ← WRITE to master
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1,    item.getItem_id());
            ps.setString(2, item.getItem_name());
            ps.setString(3, item.getItem_description());
            ps.setDouble(4, item.getPrice());
            ps.setInt(5,    item.getStock_quantity());
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            return; // don't replicate if master write failed
        }

        // 2. Replicate same write to REPLICA
        try (Connection conn = DBConnection.getReplicaConnection();   // ← SYNC to replica
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1,    item.getItem_id());
            ps.setString(2, item.getItem_name());
            ps.setString(3, item.getItem_description());
            ps.setDouble(4, item.getPrice());
            ps.setInt(5,    item.getStock_quantity());
            ps.executeUpdate();
            System.out.println("Replicated INSERT to Ecommerce_Replica");

        } catch (SQLException e) {
            System.out.println("Replication failed for item " + item.getItem_id());
            e.printStackTrace();
        }

        cacheService.evict("items:all");
    }

    @Override
    public void update(Integer id, Item item) {
        try {
            DBConnection.executeOnBoth(
                    "UPDATE item SET item_name = ?, item_description = ?, price = ?, stock_quantity = ? WHERE item_id = ?",
                    ps -> {
                        ps.setString(1, item.getItem_name());
                        ps.setString(2, item.getItem_description());
                        ps.setDouble(3, item.getPrice());
                        ps.setInt(4, item.getStock_quantity());
                        ps.setInt(5, id);
                    }
            );
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
        cacheService.evict("items:all");
        cacheService.evict("items:" + id);
    }

    @Override
    public void delete(Integer id) {
        try {
            DBConnection.executeOnBoth(
                    "DELETE FROM item WHERE item_id = ?",
                    ps -> ps.setInt(1, id)
            );
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
        cacheService.evict("items:all");
        cacheService.evict("items:" + id);
    }

    @Override
    public List<Item> search(String name, Double minPrice, Double maxPrice,
                             Integer minStock, int page, int size, String sortBy, String direction) {

        StringBuilder sql = new StringBuilder("SELECT * FROM item WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (name != null && !name.isEmpty()) {
            sql.append(" AND item_name LIKE ?");
            params.add("%" + name + "%");
        }
        if (minPrice != null) {
            sql.append(" AND price >= ?");
            params.add(minPrice);
        }
        if (maxPrice != null) {
            sql.append(" AND price <= ?");
            params.add(maxPrice);
        }
        if (minStock != null) {
            sql.append(" AND stock_quantity >= ?");
            params.add(minStock);
        }

        Set<String> allowedColumns = Set.of(
                "item_id",
                "item_name",
                "price",
                "stock_quantity"
        );

        if (!allowedColumns.contains(sortBy)) {
            sortBy = "item_id";
        }

        direction = direction.equalsIgnoreCase("desc")
                ? "DESC"
                : "ASC";

        sql.append(" ORDER BY ")
                .append(sortBy)
                .append(" ")
                .append(direction);
        sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(page * size);
        params.add(size);

        List<Item> items = new ArrayList<>();

        try (Connection conn = DBConnection.getReplicaConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) items.add(mapRow(rs));

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return items;
    }
}