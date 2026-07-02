package com.example.store.model;

public class Item {

    private Integer item_id;
    private String item_name;
    private String item_description;
    private double price;
    private Integer stock_quantity;

    public Item() {}

    public Item(Integer item_id, String item_name, String item_description,
                double price, Integer stock_quantity) {
        this.item_id = item_id;
        this.item_name = item_name;
        this.item_description = item_description;
        this.price = price;
        this.stock_quantity = stock_quantity;
    }

    public Integer getItem_id() {
        return item_id;
    }
    public String getItem_name() {
        return item_name;
    }
    public String getItem_description() {
        return item_description;
    }
    public double getPrice() { return price; }
    public Integer getStock_quantity()   { return stock_quantity; }

    public void setItem_id(Integer item_id){
        this.item_id = item_id;
    }
    public void setItem_name(String item_name) {
        this.item_name = item_name;
    }
    public void setItem_description(String item_description){
        this.item_description = item_description;
    }
    public void setPrice(double price) {
        this.price = price;
    }
    public void setStock_quantity(int stock_quantity) {
        this.stock_quantity = stock_quantity;
    }
}