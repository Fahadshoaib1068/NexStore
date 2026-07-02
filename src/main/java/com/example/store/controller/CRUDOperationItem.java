package com.example.store.controller;

import com.example.store.model.Item;
import com.example.store.model.ItemEvent;
import com.example.store.repository.ItemRepository;
import com.example.store.service.ItemEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/items")
public class CRUDOperationItem {

    private final ItemRepository     itemRepository;
    private final ItemEventPublisher itemEventPublisher;

    public CRUDOperationItem(ItemRepository itemRepository,
                             ItemEventPublisher itemEventPublisher) {
        this.itemRepository     = itemRepository;
        this.itemEventPublisher = itemEventPublisher;
    }

    // ─── READ ALL ─────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<List<Item>> getAllItems() {
        System.out.println(" CRUDOperationItem.getAllItems() reached!");
        return ResponseEntity.ok(itemRepository.findAll());
    }

    // ─── READ ONE ─────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<Item> getItemById(@PathVariable Integer id) {
        Item item = itemRepository.findById(id);
        return item != null
                ? ResponseEntity.ok(item)
                : ResponseEntity.notFound().build();
    }

    // ─── CREATE → publish to RabbitMQ ─────────────────────────────
    @PostMapping
    public ResponseEntity<String> createItem(@RequestBody Item item) {
        itemEventPublisher.publish(new ItemEvent(ItemEvent.Action.CREATED, item));
        return ResponseEntity.accepted().body(
                "Item creation request received. Processing via RabbitMQ..."
        );
    }

    // ─── UPDATE → publish to RabbitMQ ─────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<String> updateItem(@PathVariable Integer id,
                                             @RequestBody Item item) {
        Item existing = itemRepository.findById(id);
        if (existing == null) return ResponseEntity.notFound().build();

        item.setItem_id(id);
        itemEventPublisher.publish(new ItemEvent(ItemEvent.Action.UPDATED, item));
        return ResponseEntity.accepted().body(
                "Item update request received. Processing via RabbitMQ..."
        );
    }

    // ─── DELETE → publish to RabbitMQ ─────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteItem(@PathVariable Integer id) {
        Item existing = itemRepository.findById(id);
        if (existing == null) return ResponseEntity.notFound().build();

        itemEventPublisher.publish(new ItemEvent(ItemEvent.Action.DELETED, id));
        return ResponseEntity.accepted().body(
                "Item deletion request received. Processing via RabbitMQ..."
        );
    }
}