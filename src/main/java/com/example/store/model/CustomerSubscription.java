package com.example.store.model;

public class CustomerSubscription {
    private Integer sub_id;
    private String  username;
    private Integer plan_id;
    private String  plan_name;
    private Integer discount_pct;
    private String  stripe_sub_id;
    private String  stripe_customer_id;
    private String  status;
    private String  started_at;
    private String  expires_at;
    private String  cancelled_at;

    public CustomerSubscription() {}

    public CustomerSubscription(Integer sub_id, String username, Integer plan_id,
                                String plan_name, Integer discount_pct,
                                String stripe_sub_id, String stripe_customer_id,
                                String status, String started_at,
                                String expires_at, String cancelled_at) {
        this.sub_id             = sub_id;
        this.username           = username;
        this.plan_id            = plan_id;
        this.plan_name          = plan_name;
        this.discount_pct       = discount_pct;
        this.stripe_sub_id      = stripe_sub_id;
        this.stripe_customer_id = stripe_customer_id;
        this.status             = status;
        this.started_at         = started_at;
        this.expires_at         = expires_at;
        this.cancelled_at       = cancelled_at;
    }

    public Integer getSub_id()              { return sub_id; }
    public String  getUsername()            { return username; }
    public Integer getPlan_id()             { return plan_id; }
    public String  getPlan_name()           { return plan_name; }
    public Integer getDiscount_pct()        { return discount_pct; }
    public String  getStripe_sub_id()       { return stripe_sub_id; }
    public String  getStripe_customer_id()  { return stripe_customer_id; }
    public String  getStatus()              { return status; }
    public String  getStarted_at()          { return started_at; }
    public String  getExpires_at()          { return expires_at; }
    public String  getCancelled_at()        { return cancelled_at; }

    public void setSub_id(Integer sub_id)                        { this.sub_id = sub_id; }
    public void setUsername(String username)                      { this.username = username; }
    public void setPlan_id(Integer plan_id)                      { this.plan_id = plan_id; }
    public void setPlan_name(String plan_name)                   { this.plan_name = plan_name; }
    public void setDiscount_pct(Integer discount_pct)            { this.discount_pct = discount_pct; }
    public void setStripe_sub_id(String stripe_sub_id)           { this.stripe_sub_id = stripe_sub_id; }
    public void setStripe_customer_id(String stripe_customer_id) { this.stripe_customer_id = stripe_customer_id; }
    public void setStatus(String status)                         { this.status = status; }
    public void setStarted_at(String started_at)                 { this.started_at = started_at; }
    public void setExpires_at(String expires_at)                 { this.expires_at = expires_at; }
    public void setCancelled_at(String cancelled_at)             { this.cancelled_at = cancelled_at; }
}
