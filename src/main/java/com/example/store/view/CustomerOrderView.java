package com.example.store.view;

import com.example.store.model.Order;
import com.example.store.repository.OrderRepository;
import java.util.List;
import java.util.stream.Collectors;

public class CustomerOrderView implements OrderView {

    private final OrderRepository orderRepository;

    public CustomerOrderView(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public List<Order> getOrders(String username) {
        // Filter to only return this customer's own orders
        return orderRepository.findAll()
                .stream()
                .filter(o -> username.equals(o.getCustomer_username()))
                .collect(Collectors.toList());
    }

    @Override public boolean canPay()     { return true; }
    @Override public boolean canDelete()  { return false; }
    @Override public boolean canViewAll() { return false; }
}