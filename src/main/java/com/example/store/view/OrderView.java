package com.example.store.view;

import com.example.store.model.Order;
import java.util.List;

public interface OrderView {
    List<Order> getOrders(String username);
    boolean canPay();
    boolean canDelete();
    boolean canViewAll();
}