package com.example.rabbitmq.consumer.topic;

import com.example.rabbitmq.config.RabbitMQConfig;
import com.example.rabbitmq.model.MessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Order Consumer - Topic Exchange Pattern Matching
 *
 * Pattern: "order.*"
 * Eşleşen mesajlar:
 * - order.created
 * - order.updated
 * - order.cancelled
 * - order.completed
 * - order.shipped
 *
 * Eşleşmeyen mesajlar:
 * - order (1 kelime, pattern 2 kelime bekliyor)
 * - order.status.changed (3 kelime, pattern 2 kelime bekliyor)
 *
 * * (yıldız) = tam olarak bir kelime eşleşir
 */
@Slf4j
@Component
public class OrderConsumer {

    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE_NAME)
    public void receiveOrderMessage(@Payload MessageDto message, Message amqpMessage) {
        String routingKey = amqpMessage.getMessageProperties().getReceivedRoutingKey();

        log.info("═══════════════════════════════════════════════════════");
        log.info("🛒 ORDER CONSUMER - Pattern: 'order.*'");
        log.info("───────────────────────────────────────────────────────");
        log.info("🔑 Routing Key: {}", routingKey);
        log.info("📨 Message ID: {}", message.getId());
        log.info("👤 Sender: {}", message.getSender());
        log.info("💬 Content: {}", message.getContent());

        // Order event türünü belirle
        String orderEvent = extractOrderEvent(routingKey);
        log.info("📦 Order Event: {}", orderEvent);

        // Order event'ine göre işlem yap
        switch (orderEvent) {
            case "created":
                log.info("🆕 Order CREATED: {}", message.getContent());
                // Yeni sipariş oluşturma işlemleri
                // - Stok kontrolü
                // - Ödeme işlemi başlatma
                // - Email/SMS bildirimi
                break;

            case "updated":
                log.info("🔄 Order UPDATED: {}", message.getContent());
                // Sipariş güncelleme işlemleri
                // - Müşteri bilgilendirme
                // - Loglama
                break;

            case "cancelled":
                log.info("❌ Order CANCELLED: {}", message.getContent());
                // Sipariş iptal işlemleri
                // - Stok iade
                // - Ödeme iadesi
                // - Bildirim gönderme
                break;

            case "completed":
                log.info("✅ Order COMPLETED: {}", message.getContent());
                // Sipariş tamamlama işlemleri
                // - Fatura oluşturma
                // - Puan kazandırma
                // - Değerlendirme daveti
                break;

            case "shipped":
                log.info("📬 Order SHIPPED: {}", message.getContent());
                // Kargo gönderim işlemleri
                // - Kargo takip numarası gönderme
                // - SMS/Email bildirimi
                break;

            default:
                log.info("📋 Order event: {} - {}", orderEvent, message.getContent());
        }

        log.info("✅ Order event processed successfully");
        log.info("═══════════════════════════════════════════════════════\n");
    }

    private String extractOrderEvent(String routingKey) {
        String[] parts = routingKey.split("\\.");
        return parts.length > 1 ? parts[1] : "unknown";
    }
}
