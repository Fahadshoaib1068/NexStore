package com.example.store.repository;

import com.example.store.model.Order;
import com.example.store.model.OrderDetail;
import java.util.List;

public interface OrderRepository {
    List<Order> findAll();
    OrderDetail findById(Integer id);
    Integer save(Order order);
    void addOrderItem(Integer order_id, Integer item_id, Integer quantity);
    void delete(Integer id);
    void PaymentStatus(Integer id, String status);
    List<Order> findByUsername(String username);
    void updateStripeSession(Integer orderId,String sessionId);
    void updatePaymentStatus(Integer orderId,String status);
    void applyDiscount(Integer orderId, Integer discount);
}