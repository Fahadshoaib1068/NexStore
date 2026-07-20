package com.example.store.view;

import com.example.store.repository.OrderRepository;
import org.springframework.stereotype.Component;

@Component
public class OrderViewFactory {

    private final OrderRepository orderRepository;

    public OrderViewFactory(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public OrderView getView(String role) {
        return switch (role) {
            case "CUSTOMER"    -> new CustomerOrderView(orderRepository);
            case "STAFF"       -> new StaffOrderView(orderRepository);
            case "ADMIN",
                 "SUPER_ADMIN" -> new AdminOrderView(orderRepository);
            default            -> new CustomerOrderView(orderRepository);
        };
    }
}