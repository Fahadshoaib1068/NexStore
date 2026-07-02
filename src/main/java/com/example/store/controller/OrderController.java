package com.example.store.controller;

import com.example.store.model.Order;
import com.example.store.model.OrderDetail;
import com.example.store.repository.OrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // ─── GET ALL ORDERS ───────────────────────────────────────────
    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderRepository.findAll());
    }

    // ─── GET ORDER BY ID ──────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<OrderDetail> getOrderById(@PathVariable Integer id) {
        OrderDetail order = orderRepository.findById(id);
        return order != null
                ? ResponseEntity.ok(order)
                : ResponseEntity.notFound().build();
    }

    // ─── PLACE ORDER ──────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<String> createOrder(@RequestBody Map<String, Object> body) {
        Integer customer_id = (Integer) body.get("customer_id");
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");

        com.example.store.model.Order order = new com.example.store.model.Order();
        order.setCustomer_id(customer_id);

        Integer order_id = orderRepository.save(order);
        if (order_id == null) return ResponseEntity.internalServerError().body("Failed to create order.");

        try {
            for (Map<String, Object> item : items) {
                Integer item_id  = (Integer) item.get("item_id");
                Integer quantity = (Integer) item.get("quantity");
                orderRepository.addOrderItem(order_id, item_id, quantity);
            }
        } catch (RuntimeException e) {
            orderRepository.delete(order_id);
            return ResponseEntity.status(400).body("Order failed: " + e.getMessage());
        }

        return ResponseEntity.ok("Order #" + order_id + " created successfully.");
    }

    // ─── DELETE ORDER ─────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteOrder(@PathVariable Integer id) {
        OrderDetail existing = orderRepository.findById(id);
        if (existing == null) return ResponseEntity.notFound().build();

        orderRepository.delete(id);
        return ResponseEntity.ok("Order deleted successfully.");
    }
}