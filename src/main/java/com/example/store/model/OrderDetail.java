package com.example.store.model;

import java.util.List;

public class OrderDetail {
    private Integer order_id;
    private String order_date;
    private double total_amount;

    // Customer info loaded eagerly
    private String customer_first_name;
    private String customer_last_name;
    private String customer_email;
    private String payement_status;

    // All order items loaded eagerly
    private List<OrderItem> items;

    public OrderDetail() {}

    public OrderDetail(Integer order_id, String order_date, double total_amount,
                       String customer_first_name, String customer_last_name,
                       String customer_email, String payement_status, List<OrderItem> items) {
        this.order_id = order_id;
        this.order_date = order_date;
        this.total_amount = total_amount;
        this.customer_first_name = customer_first_name;
        this.customer_last_name = customer_last_name;
        this.customer_email = customer_email;
        this.items = items;
        this.payement_status = payement_status;
    }

    public Integer getOrder_id()            { return order_id; }
    public String getOrder_date()           { return order_date; }
    public double getTotal_amount()         { return total_amount; }
    public String getCustomer_first_name()  { return customer_first_name; }
    public String getCustomer_last_name()   { return customer_last_name; }
    public String getCustomer_email()       { return customer_email; }
    public List<OrderItem> getItems()       { return items; }
    public String getPayement_status()      { return payement_status; }

    public void setOrder_id(Integer order_id)                   { this.order_id = order_id; }
    public void setOrder_date(String order_date)                { this.order_date = order_date; }
    public void setTotal_amount(double total_amount)            { this.total_amount = total_amount; }
    public void setCustomer_first_name(String customer_first_name) { this.customer_first_name = customer_first_name; }
    public void setCustomer_last_name(String customer_last_name)   { this.customer_last_name = customer_last_name; }
    public void setCustomer_email(String customer_email)           { this.customer_email = customer_email; }
    public void setItems(List<OrderItem> items)                 { this.items = items; }
    public void setPayement_status(String payement_status)        { this.payement_status = payement_status; }
}