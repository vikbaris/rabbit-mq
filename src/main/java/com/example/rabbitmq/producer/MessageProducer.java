package com.example.rabbitmq.producer;

import com.example.rabbitmq.config.RabbitMQConfig;
import com.example.rabbitmq.model.MessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * RabbitMQ Producer Service
 * Mesajları RabbitMQ'ya gönderir
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Basit mesaj gönderme
     * @param message Gönderilecek mesaj
     */
    public void sendMessage(MessageDto message) {
        try {
            log.info("Sending message: {}", message);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.ROUTING_KEY,
                    message
            );
            log.info("Message sent successfully with ID: {}", message.getId());
        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send message", e);
        }
    }

    /**
     * String content ile mesaj oluşturur ve gönderir
     * @param content Mesaj içeriği
     * @param sender Gönderen
     */
    public void sendMessage(String content, String sender) {
        MessageDto message = createMessage(content, sender, "NORMAL", "INFO");
        sendMessage(message);
    }

    /**
     * Priority ile mesaj gönderme
     * @param content Mesaj içeriği
     * @param sender Gönderen
     * @param priority Öncelik seviyesi (LOW, NORMAL, HIGH)
     */
    public void sendMessageWithPriority(String content, String sender, String priority) {
        MessageDto message = createMessage(content, sender, priority, "PRIORITY");
        sendMessage(message);
    }

    /**
     * Custom routing key ile mesaj gönderme
     * @param message Mesaj
     * @param customRoutingKey Özel routing key
     */
    public void sendMessageWithCustomRouting(MessageDto message, String customRoutingKey) {
        try {
            log.info("Sending message with custom routing key: {}", customRoutingKey);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    customRoutingKey,
                    message
            );
            log.info("Message sent successfully with custom routing");
        } catch (Exception e) {
            log.error("Error sending message with custom routing: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send message with custom routing", e);
        }
    }

    /**
     * Callback ile mesaj gönderme (Publisher Confirms)
     * @param message Mesaj
     */
    public void sendMessageWithConfirm(MessageDto message) {
        try {
            log.info("Sending message with publisher confirm: {}", message);

            // ID yoksa oluştur
            String messageId = message.getId() != null ? message.getId() : UUID.randomUUID().toString();
            if (message.getId() == null) {
                message.setId(messageId);
            }

            CorrelationData correlationData = new CorrelationData(messageId);

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.ROUTING_KEY,
                    message,
                    correlationData
            );

            log.info("Message sent with confirm for ID: {}", messageId);
        } catch (Exception e) {
            log.error("Error sending message with confirm: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send message with confirm", e);
        }
    }

    /**
     * Helper method: MessageDto oluşturur
     */
    private MessageDto createMessage(String content, String sender, String priority, String type) {
        MessageDto.MessageMetadata metadata = new MessageDto.MessageMetadata(
                priority,
                type,
                "1.0"
        );

        return new MessageDto(
                UUID.randomUUID().toString(),
                content,
                sender,
                LocalDateTime.now(),
                metadata
        );
    }

    // ========== TOPIC EXCHANGE METHODS ==========

    /**
     * Topic Exchange'e mesaj gönderme (Generic)
     * @param message Mesaj
     * @param routingKey Routing key (pattern matching için)
     */
    public void sendToTopicExchange(MessageDto message, String routingKey) {
        try {
            log.info("📤 Sending to Topic Exchange - Routing Key: {}", routingKey);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.TOPIC_EXCHANGE_NAME,
                    routingKey,
                    message
            );
            log.info("✅ Message sent to topic exchange successfully");
        } catch (Exception e) {
            log.error("❌ Error sending message to topic exchange: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send message to topic exchange", e);
        }
    }

    /**
     * Log mesajı gönder
     * Routing key pattern: log.{level}.{category}
     * Örnekler: log.error, log.info.security, log.warning.database
     */
    public void sendLogMessage(String level, String category, String content, String sender) {
        String routingKey = category != null && !category.isEmpty()
                ? String.format("log.%s.%s", level, category)
                : String.format("log.%s", level);

        MessageDto message = createMessage(content, sender, "NORMAL", "LOG");
        log.info("📋 Sending LOG message - Level: {}, Category: {}, RoutingKey: {}",
                level, category, routingKey);
        sendToTopicExchange(message, routingKey);
    }

    /**
     * Notification mesajı gönder
     * Routing key pattern: notification.{type}
     * Örnekler: notification.email, notification.sms, notification.push
     */
    public void sendNotification(String type, String content, String sender) {
        String routingKey = String.format("notification.%s", type);
        MessageDto message = createMessage(content, sender, "HIGH", "NOTIFICATION");
        log.info("🔔 Sending NOTIFICATION - Type: {}, RoutingKey: {}", type, routingKey);
        sendToTopicExchange(message, routingKey);
    }

    /**
     * Analytics mesajı gönder
     * Routing key pattern: {source}.analytics
     * Örnekler: user.analytics, order.analytics, payment.analytics
     */
    public void sendAnalytics(String source, String content, String sender) {
        String routingKey = String.format("%s.analytics", source);
        MessageDto message = createMessage(content, sender, "LOW", "ANALYTICS");
        log.info("📊 Sending ANALYTICS - Source: {}, RoutingKey: {}", source, routingKey);
        sendToTopicExchange(message, routingKey);
    }

    /**
     * Order event mesajı gönder
     * Routing key pattern: order.{event}
     * Örnekler: order.created, order.updated, order.cancelled, order.completed
     */
    public void sendOrderEvent(String event, String content, String sender) {
        String routingKey = String.format("order.%s", event);
        MessageDto message = createMessage(content, sender, "HIGH", "ORDER_EVENT");
        log.info("🛒 Sending ORDER EVENT - Event: {}, RoutingKey: {}", event, routingKey);
        sendToTopicExchange(message, routingKey);
    }
}
