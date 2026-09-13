package com.learning.meetingrooms.booking.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQTopologyConfig {

    public static final String EXCHANGE_NAME = "booking.exchange";
    public static final String NOTIFICATION_QUEUE = "notification.booking.queue";
    public static final String DLQ_NAME = "notification.booking.dlq";
    public static final String ROUTING_KEY_CREATED = "booking.created";
    public static final String ROUTING_KEY_CANCELLED = "booking.cancelled";

    @Bean
    TopicExchange bookingExchange() {
        return ExchangeBuilder
                .topicExchange(EXCHANGE_NAME)
                .durable(true)
                .build();
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder
                .durable(DLQ_NAME)
                .build();
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder
                .durable(NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DLQ_NAME)
                .build();
    }

    @Bean
    Binding createdBinding(Queue notificationQueue, TopicExchange bookingExchange) {
        return BindingBuilder
                .bind(notificationQueue)
                .to(bookingExchange)
                .with(ROUTING_KEY_CREATED);
    }

    @Bean
    Binding cancelledBinding(Queue notificationQueue, TopicExchange bookingExchange) {
        return BindingBuilder
                .bind(notificationQueue)
                .to(bookingExchange)
                .with(ROUTING_KEY_CANCELLED);
    }
}
