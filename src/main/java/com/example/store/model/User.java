package com.example.store.model;

import java.util.List;

public class User {
    private Integer user_id;
    private String  username;
    private String  email;
    private String  password_hash;
    private Integer customer_id;
    private String  created_at;
    private List<String> roles;

    public User() {}

    public User(Integer user_id, String username, String email,
                String password_hash, Integer customer_id,
                String created_at, List<String> roles) {
        this.user_id       = user_id;
        this.username      = username;
        this.email         = email;
        this.password_hash = password_hash;
        this.customer_id   = customer_id;
        this.created_at    = created_at;
        this.roles         = roles;
    }

    public Integer getUser_id()       { return user_id; }
    public String  getUsername()      { return username; }
    public String  getEmail()         { return email; }
    public String  getPassword_hash() { return password_hash; }
    public Integer getCustomer_id()   { return customer_id; }
    public String  getCreated_at()    { return created_at; }
    public List<String> getRoles()    { return roles; }

    public void setUser_id(Integer user_id)          { this.user_id = user_id; }
    public void setUsername(String username)          { this.username = username; }
    public void setEmail(String email)               { this.email = email; }
    public void setPassword_hash(String password_hash){ this.password_hash = password_hash; }
    public void setCustomer_id(Integer customer_id)  { this.customer_id = customer_id; }
    public void setCreated_at(String created_at)     { this.created_at = created_at; }
    public void setRoles(List<String> roles)         { this.roles = roles; }
}