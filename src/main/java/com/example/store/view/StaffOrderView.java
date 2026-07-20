package com.example.store.view;

import com.example.store.model.Order;
import com.example.store.repository.OrderRepository;
import java.util.List;

public class StaffOrderView implements OrderView {

    private final OrderRepository orderRepository;

    public StaffOrderView(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public List<Order> getOrders(String username) {
        return orderRepository.findAll(); // sees ALL orders
    }

    @Override public boolean canPay()     { return false; }
    @Override public boolean canDelete()  { return false; }
    @Override public boolean canViewAll() { return true; }
}