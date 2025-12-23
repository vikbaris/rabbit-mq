package com.example.rabbitmq.consumer.topic;

import com.example.rabbitmq.config.RabbitMQConfig;
import com.example.rabbitmq.model.MessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Notification Consumer - Topic Exchange Pattern Matching
 *
 * Pattern: "notification.*"
 * Eşleşen mesajlar:
 * - notification.email
 * - notification.sms
 * - notification.push
 *
 * Eşleşmeyen mesajlar:
 * - notification.email.urgent (3 kelime, pattern sadece 2 kelime bekliyor)
 * - notification (1 kelime, pattern 2 kelime bekliyor)
 *
 * * (yıldız) = tam olarak bir kelime eşleşir
 */
@Slf4j
@Component
public class NotificationConsumer {

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE_NAME)
    public void receiveNotificationMessage(@Payload MessageDto message, Message amqpMessage) {
        String routingKey = amqpMessage.getMessageProperties().getReceivedRoutingKey();

        log.info("═══════════════════════════════════════════════════════");
        log.info("🔔 NOTIFICATION CONSUMER - Pattern: 'notification.*'");
        log.info("───────────────────────────────────────────────────────");
        log.info("🔑 Routing Key: {}", routingKey);
        log.info("📨 Message ID: {}", message.getId());
        log.info("👤 Sender: {}", message.getSender());
        log.info("💬 Content: {}", message.getContent());

        // Notification türünü belirle
        String notificationType = extractNotificationType(routingKey);
        log.info("📱 Notification Type: {}", notificationType);

        // Notification türüne göre işlem yap
        switch (notificationType) {
            case "email":
                log.info("📧 Sending EMAIL notification: {}", message.getContent());
                // Email gönderme servisi çağrılabilir
                break;
            case "sms":
                log.info("📱 Sending SMS notification: {}", message.getContent());
                // SMS gönderme servisi çağrılabilir
                break;
            case "push":
                log.info("🔔 Sending PUSH notification: {}", message.getContent());
                // Push notification servisi çağrılabilir
                break;
            default:
                log.info("📨 Unknown notification type: {}", notificationType);
        }

        log.info("✅ Notification processed successfully");
        log.info("═══════════════════════════════════════════════════════\n");
    }

    private String extractNotificationType(String routingKey) {
        String[] parts = routingKey.split("\\.");
        return parts.length > 1 ? parts[1] : "unknown";
    }
}
