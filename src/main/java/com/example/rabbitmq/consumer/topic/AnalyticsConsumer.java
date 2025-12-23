package com.example.rabbitmq.consumer.topic;

import com.example.rabbitmq.config.RabbitMQConfig;
import com.example.rabbitmq.model.MessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Analytics Consumer - Topic Exchange Pattern Matching
 *
 * Pattern: "*.analytics"
 * Eşleşen mesajlar:
 * - user.analytics
 * - order.analytics
 * - payment.analytics
 * - product.analytics
 *
 * Eşleşmeyen mesajlar:
 * - analytics (1 kelime, pattern 2 kelime bekliyor)
 * - user.order.analytics (3 kelime, pattern 2 kelime bekliyor)
 *
 * * (yıldız) = tam olarak bir kelime eşleşir
 */
@Slf4j
@Component
public class AnalyticsConsumer {

    @RabbitListener(queues = RabbitMQConfig.ANALYTICS_QUEUE_NAME)
    public void receiveAnalyticsMessage(@Payload MessageDto message, Message amqpMessage) {
        String routingKey = amqpMessage.getMessageProperties().getReceivedRoutingKey();

        log.info("═══════════════════════════════════════════════════════");
        log.info("📊 ANALYTICS CONSUMER - Pattern: '*.analytics'");
        log.info("───────────────────────────────────────────────────────");
        log.info("🔑 Routing Key: {}", routingKey);
        log.info("📨 Message ID: {}", message.getId());
        log.info("👤 Sender: {}", message.getSender());
        log.info("💬 Content: {}", message.getContent());

        // Analytics kaynağını belirle
        String analyticsSource = extractAnalyticsSource(routingKey);
        log.info("🎯 Analytics Source: {}", analyticsSource);

        // Analytics türüne göre veri işleme
        log.info("📈 Processing analytics data from source: {}", analyticsSource);

        // Burada analytics veritabanına kayıt, metrik hesaplama vb. yapılabilir
        switch (analyticsSource) {
            case "user":
                log.info("👥 User analytics: Processing user behavior data");
                break;
            case "order":
                log.info("🛒 Order analytics: Processing order metrics");
                break;
            case "payment":
                log.info("💳 Payment analytics: Processing payment statistics");
                break;
            case "product":
                log.info("📦 Product analytics: Processing product performance data");
                break;
            default:
                log.info("📊 Generic analytics: {}", analyticsSource);
        }

        log.info("✅ Analytics data processed successfully");
        log.info("═══════════════════════════════════════════════════════\n");
    }

    private String extractAnalyticsSource(String routingKey) {
        String[] parts = routingKey.split("\\.");
        return parts.length > 0 ? parts[0] : "unknown";
    }
}
