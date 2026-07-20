package com.example.store.model;

public class Order {
    private Integer order_id;
    private Integer customer_id;
    private String order_date;
    private double total_amount;
    private String payment_status;
    private String getCustomer_username;
    private String stripe_session_id;



    public Order() {}

    public Order(Integer order_id, Integer customer_id, String order_date, double total_amount,
                 String payment_status, String getCustomer_username, String stripe_session_id) {
        this.order_id = order_id;
        this.customer_id = customer_id;
        this.order_date = order_date;
        this.total_amount = total_amount;
        this.payment_status = payment_status;
        this.getCustomer_username = getCustomer_username;
        this.stripe_session_id = stripe_session_id;
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
    public String getPayment_status() {
        return payment_status;
    }
    public String getCustomer_username() {
        return getCustomer_username;
    }
    public String getStripe_session_id() {
        return stripe_session_id;
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
    public void setPayment_status(String payment_status) {
        this.payment_status = payment_status;
    }
    public void setCustomer_username(String getCustomer_username) {
        this.getCustomer_username = getCustomer_username;
    }
    public void setStripe_session_id(String stripe_session_id) {
        this.stripe_session_id = stripe_session_id;
    }


}
