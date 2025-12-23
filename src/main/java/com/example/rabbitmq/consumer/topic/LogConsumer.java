package com.example.rabbitmq.consumer.topic;

import com.example.rabbitmq.config.RabbitMQConfig;
import com.example.rabbitmq.model.MessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Log Consumer - Topic Exchange Pattern Matching
 *
 * Pattern: "log.#"
 * Eşleşen mesajlar:
 * - log.error
 * - log.info
 * - log.warning
 * - log.error.database
 * - log.info.security.authentication
 *
 * # (hash) = sıfır veya daha fazla kelime eşleşir
 */
@Slf4j
@Component
public class LogConsumer {

    @RabbitListener(queues = RabbitMQConfig.LOG_QUEUE_NAME)
    public void receiveLogMessage(@Payload MessageDto message, Message amqpMessage) {
        String routingKey = amqpMessage.getMessageProperties().getReceivedRoutingKey();

        log.info("═══════════════════════════════════════════════════════");
        log.info("📋 LOG CONSUMER - Pattern: 'log.#'");
        log.info("───────────────────────────────────────────────────────");
        log.info("🔑 Routing Key: {}", routingKey);
        log.info("📨 Message ID: {}", message.getId());
        log.info("👤 Sender: {}", message.getSender());
        log.info("💬 Content: {}", message.getContent());

        // Routing key'e göre log seviyesini belirle
        String logLevel = extractLogLevel(routingKey);
        log.info("🎯 Log Level: {}", logLevel);

        // Pattern matching örneği
        if (routingKey.matches("log\\.error.*")) {
            log.error("🚨 ERROR LOG detected: {}", message.getContent());
            // Burada error notification, alert sistemi vb. tetiklenebilir
        } else if (routingKey.matches("log\\.warning.*")) {
            log.warn("⚠️ WARNING LOG detected: {}", message.getContent());
        } else if (routingKey.matches("log\\.info.*")) {
            log.info("ℹ️ INFO LOG detected: {}", message.getContent());
        }

        log.info("✅ Log message processed successfully");
        log.info("═══════════════════════════════════════════════════════\n");
    }

    private String extractLogLevel(String routingKey) {
        String[] parts = routingKey.split("\\.");
        return parts.length > 1 ? parts[1].toUpperCase() : "UNKNOWN";
    }
}
