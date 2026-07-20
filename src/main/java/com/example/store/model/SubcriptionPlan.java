package com.example.store.model;

public class SubcriptionPlan {
    private Integer plan_id;
    private String plan_name;
    private double price;
    private int discount_pct;
    private String stripe_priceid;
    private String description;


    public SubcriptionPlan(Integer plan_id, String plan_name, double price, int discount_pct,
                           String stripe_priceid, String description) {
        this.plan_id = plan_id;
        this.plan_name = plan_name;
        this.price = price;
        this.discount_pct = discount_pct;
        this.stripe_priceid = stripe_priceid;
        this.description = description;
    }

    public Integer getPlan_id() {
        return plan_id;
    }
    public void setPlan_id(Integer plan_id) {
        this.plan_id = plan_id;
    }
    public String getPlan_name() {
        return plan_name;
    }
    public void setPlan_name(String plan_name) {
        this.plan_name = plan_name;
    }
    public double getPrice() {
        return price;
    }
    public void setPrice(double price) {
        this.price = price;
    }
    public int getDiscount_pct() {
        return discount_pct;
    }
    public void setDiscount_pct(int discount_pct) {
        this.discount_pct = discount_pct;
    }
    public String getStripe_priceid() {
        return stripe_priceid;
    }
    public void setStripe_priceid(String stripe_priceid) {
        this.stripe_priceid = stripe_priceid;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

}

