package com.example.rabbitmq.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Queue, Exchange ve Routing Key tanımlamaları
    public static final String QUEUE_NAME = "example.queue";
    public static final String EXCHANGE_NAME = "example.exchange";
    public static final String ROUTING_KEY = "example.routing.key";

    // Dead Letter Queue (DLQ) tanımlamaları
    public static final String DLQ_QUEUE_NAME = "example.dlq";
    public static final String DLQ_EXCHANGE_NAME = "example.dlq.exchange";
    public static final String DLQ_ROUTING_KEY = "example.dlq.routing.key";

    // ========== TOPIC EXCHANGE CONFIGURATION ==========
    // Topic Exchange - Pattern Matching için kullanılır
    public static final String TOPIC_EXCHANGE_NAME = "topic.exchange";

    // Log Queue - Tüm log mesajlarını dinler (log.error, log.info, log.warning, vb.)
    public static final String LOG_QUEUE_NAME = "log.queue";
    public static final String LOG_ROUTING_PATTERN = "log.#";  // # = sıfır veya daha fazla kelime

    // Notification Queue - Bildirim mesajlarını dinler (notification.email, notification.sms)
    public static final String NOTIFICATION_QUEUE_NAME = "notification.queue";
    public static final String NOTIFICATION_ROUTING_PATTERN = "notification.*";  // * = tam bir kelime

    // Analytics Queue - Analytics mesajlarını dinler (user.analytics, order.analytics)
    public static final String ANALYTICS_QUEUE_NAME = "analytics.queue";
    public static final String ANALYTICS_ROUTING_PATTERN = "*.analytics";

    // Order Queue - Sipariş işlemlerini dinler (order.created, order.updated, order.cancelled)
    public static final String ORDER_QUEUE_NAME = "order.queue";
    public static final String ORDER_ROUTING_PATTERN = "order.*";

    /**
     * Ana Queue tanımlaması
     * Dead Letter Exchange ile birlikte yapılandırılmış
     */
    @Bean
    public Queue queue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * Direct Exchange tanımlaması
     */
    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    /**
     * Queue ile Exchange arasında Binding
     */
    @Bean
    public Binding binding(Queue queue, DirectExchange exchange) {
        return BindingBuilder
                .bind(queue)
                .to(exchange)
                .with(ROUTING_KEY);
    }

    /**
     * Dead Letter Queue tanımlaması
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_QUEUE_NAME).build();
    }

    /**
     * Dead Letter Exchange tanımlaması
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLQ_EXCHANGE_NAME);
    }

    /**
     * DLQ Binding
     */
    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(DLQ_ROUTING_KEY);
    }

    /**
     * JSON message converter
     * Mesajları JSON formatında serialize/deserialize eder
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate yapılandırması
     * Producer tarafından mesaj göndermek için kullanılır
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }

    // ========== TOPIC EXCHANGE BEANS ==========

    /**
     * Topic Exchange Bean
     * Pattern matching ile routing yapar
     * Wildcard karakterleri:
     * - * (yıldız) = tam olarak bir kelime eşleşir
     * - # (hash) = sıfır veya daha fazla kelime eşleşir
     */
    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(TOPIC_EXCHANGE_NAME);
    }

    /**
     * Log Queue - Tüm log mesajlarını alır
     */
    @Bean
    public Queue logQueue() {
        return QueueBuilder.durable(LOG_QUEUE_NAME).build();
    }

    /**
     * Log Queue Binding
     * Pattern: "log.#" -> log.error, log.info, log.warning.database gibi tüm log mesajları
     */
    @Bean
    public Binding logBinding(Queue logQueue, TopicExchange topicExchange) {
        return BindingBuilder
                .bind(logQueue)
                .to(topicExchange)
                .with(LOG_ROUTING_PATTERN);
    }

    /**
     * Notification Queue - Bildirim mesajlarını alır
     */
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE_NAME).build();
    }

    /**
     * Notification Queue Binding
     * Pattern: "notification.*" -> notification.email, notification.sms (sadece iki kelimeli)
     */
    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange topicExchange) {
        return BindingBuilder
                .bind(notificationQueue)
                .to(topicExchange)
                .with(NOTIFICATION_ROUTING_PATTERN);
    }

    /**
     * Analytics Queue - Analytics mesajlarını alır
     */
    @Bean
    public Queue analyticsQueue() {
        return QueueBuilder.durable(ANALYTICS_QUEUE_NAME).build();
    }

    /**
     * Analytics Queue Binding
     * Pattern: "*.analytics" -> user.analytics, order.analytics, payment.analytics
     */
    @Bean
    public Binding analyticsBinding(Queue analyticsQueue, TopicExchange topicExchange) {
        return BindingBuilder
                .bind(analyticsQueue)
                .to(topicExchange)
                .with(ANALYTICS_ROUTING_PATTERN);
    }

    /**
     * Order Queue - Sipariş mesajlarını alır
     */
    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable(ORDER_QUEUE_NAME).build();
    }

    /**
     * Order Queue Binding
     * Pattern: "order.*" -> order.created, order.updated, order.cancelled
     */
    @Bean
    public Binding orderBinding(Queue orderQueue, TopicExchange topicExchange) {
        return BindingBuilder
                .bind(orderQueue)
                .to(topicExchange)
                .with(ORDER_ROUTING_PATTERN);
    }
}
