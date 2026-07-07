package com.example.store.repository;

import com.example.store.model.Item;

import java.util.List;

public interface ItemRepository {
    List<Item> findAll();
    Item findById(Integer id);
    void save(Item item);
    void update(Integer id, Item item);
    void delete(Integer id);
    List<Item> search(String name, Double minPrice, Double maxPrice,
                      Integer minStock, int page, int size, String Sortby, String directions);
}
