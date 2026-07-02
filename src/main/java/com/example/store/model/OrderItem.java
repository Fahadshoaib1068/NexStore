package com.example.store.model;

public class OrderItem {
    private Integer oi_id;
    private Integer order_id;
    private Integer item_id;
    private String item_name;
    private Integer quantity;
    private double unit_price;

    public OrderItem() {}

    public OrderItem(Integer oi_id, Integer order_id, Integer item_id,
                     String item_name, Integer quantity, double unit_price) {
        this.oi_id = oi_id;
        this.order_id = order_id;
        this.item_id = item_id;
        this.item_name = item_name;
        this.quantity = quantity;
        this.unit_price = unit_price;
    }

    public Integer getOi_id()      { return oi_id; }
    public Integer getOrder_id()   { return order_id; }
    public Integer getItem_id()    { return item_id; }
    public String getItem_name()   { return item_name; }
    public Integer getQuantity()   { return quantity; }
    public double getUnit_price()  { return unit_price; }

    public void setOi_id(Integer oi_id)          { this.oi_id = oi_id; }
    public void setOrder_id(Integer order_id)    { this.order_id = order_id; }
    public void setItem_id(Integer item_id)      { this.item_id = item_id; }
    public void setItem_name(String item_name)   { this.item_name = item_name; }
    public void setQuantity(Integer quantity)    { this.quantity = quantity; }
    public void setUnit_price(double unit_price) { this.unit_price = unit_price; }
}