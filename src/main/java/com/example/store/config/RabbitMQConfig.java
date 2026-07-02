package com.example.store.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ─── SINGLE QUEUE ─────────────────────────────────────────────
    public static final String QUEUE    = "items.queue";
    public static final String EXCHANGE = "items.exchange";
    public static final String KEY      = "item.action";

    // ─── QUEUE ────────────────────────────────────────────────────
    @Bean
    public Queue itemsQueue() {
        return new Queue(QUEUE, true); // durable = survives RabbitMQ restart
    }

    // ─── EXCHANGE ─────────────────────────────────────────────────
    @Bean
    public TopicExchange itemsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    // ─── BINDING ──────────────────────────────────────────────────
    @Bean
    public Binding itemsBinding() {
        return BindingBuilder
                .bind(itemsQueue())
                .to(itemsExchange())
                .with(KEY);
    }

    // ─── MESSAGE CONVERTER ────────────────────────────────────────
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new org.springframework.amqp.support.converter.JacksonJsonMessageConverter();
    }

    // ─── RABBIT TEMPLATE ──────────────────────────────────────────
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}