package com.example.store.service;

import com.example.store.config.RabbitMQConfig;
import com.example.store.model.ItemEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ItemEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public ItemEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(ItemEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.KEY,
                event
        );
        System.out.println("Published to RabbitMQ: " + event);
    }
}