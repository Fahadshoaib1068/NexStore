package com.example.store.repository;

import com.example.store.model.Order;
import com.example.store.model.OrderDetail;

import java.util.List;

public interface OrderRepository {
    List<Order> findAll();           // LAZY - basic order info
    OrderDetail findById(Integer id); // EAGER - full details
    Integer save(Order order);        // returns generated order_id
    void addOrderItem(Integer order_id, Integer item_id, Integer quantity);
    void delete(Integer id);
}