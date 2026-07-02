package com.example.store.service;

import com.example.store.config.RabbitMQConfig;
import com.example.store.model.ItemEvent;
import com.example.store.repository.ItemRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class ItemEventListener {

    private final ItemRepository itemRepository;

    public ItemEventListener(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void handleItemEvent(ItemEvent event) {
        System.out.println(" Received from RabbitMQ: " + event);

        switch (event.getAction()) {
            case CREATED -> itemRepository.save(event.getItem());
            case UPDATED -> itemRepository.update(event.getItemId(), event.getItem());
            case DELETED -> itemRepository.delete(event.getItemId());
        }
    }
}