package com.example.store.model;

public class Order {
    private Integer order_id;
    private Integer customer_id;
    private String order_date;
    private double total_amount;

    public Order() {}

    public Order(Integer order_id, Integer customer_id, String order_date, double total_amount) {
        this.order_id = order_id;
        this.customer_id = customer_id;
        this.order_date = order_date;
        this.total_amount = total_amount;
    }

    public Integer getOrder_id() {
        return order_id;
    }
    public Integer getCustomer_id() {
        return customer_id;
    }
    public String getOrder_date() {
        return order_date;
    }
    public double getTotal_amount() {
        return total_amount;
    }

    public void setOrder_id(Integer order_id) {
        this.order_id = order_id;
    }
    public void setCustomer_id(Integer customer_id) {
        this.customer_id = customer_id;
    }
    public void setOrder_date(String order_date) {
        this.order_date = order_date;
    }
    public void setTotal_amount(double total_amount) {
        this.total_amount = total_amount;
    }
}
