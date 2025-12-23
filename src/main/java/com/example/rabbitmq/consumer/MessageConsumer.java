package com.example.rabbitmq.consumer;

import com.example.rabbitmq.config.RabbitMQConfig;
import com.example.rabbitmq.model.MessageDto;
import com.example.rabbitmq.service.dlq.DLQHandlerService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * RabbitMQ Consumer Service
 * Queue'dan gelen mesajları dinler ve işler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageConsumer {

    private final DLQHandlerService dlqHandlerService;

    /**
     * Basit mesaj dinleme
     * Auto-acknowledge modu kullanır
     * @param message Gelen mesaj
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void receiveMessage(@Payload MessageDto message) {
        try {
            log.info("==============================================");
            log.info("Message received from queue: {}", RabbitMQConfig.QUEUE_NAME);
            log.info("Message ID: {}", message.getId());
            log.info("Content: {}", message.getContent());
            log.info("Sender: {}", message.getSender());
            log.info("Timestamp: {}", message.getTimestamp());
            log.info("Priority: {}", message.getMetadata().getPriority());
            log.info("Type: {}", message.getMetadata().getType());
            log.info("==============================================");

            // İş mantığı burada işlenir
            processMessage(message);

        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
            // Hata durumunda mesaj DLQ'ya gönderilir (retry politikası uygulanır)
            throw new RuntimeException("Message processing failed", e);
        }
    }

    /**
     * Manual acknowledge ile mesaj dinleme
     * Mesaj işlendikten sonra manuel olarak acknowledge edilir
     * @param message Gelen mesaj
     * @param channel RabbitMQ channel
     * @param deliveryTag Mesaj delivery tag
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME, ackMode = "MANUAL")
    public void receiveMessageWithManualAck(
            @Payload MessageDto message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {

        try {
            log.info("Received message with manual ACK - ID: {}", message.getId());

            // İş mantığı
            processMessage(message);

            // Manuel acknowledge
            channel.basicAck(deliveryTag, false);
            log.info("Message acknowledged successfully - ID: {}", message.getId());

        } catch (Exception e) {
            log.error("Error processing message, will NACK: {}", e.getMessage(), e);

            try {
                // Hata durumunda NACK gönder (requeue = false, DLQ'ya gider)
                channel.basicNack(deliveryTag, false, false);
                log.info("Message sent to DLQ - ID: {}", message.getId());
            } catch (IOException ioException) {
                log.error("Error sending NACK: {}", ioException.getMessage(), ioException);
            }
        }
    }

    /**
     * Dead Letter Queue listener
     * Başarısız mesajları dinler ve DLQHandlerService ile işler
     * @param message Başarısız mesaj
     * @param failedMessage Ham mesaj
     */
    @RabbitListener(queues = RabbitMQConfig.DLQ_QUEUE_NAME)
    public void receiveDeadLetterMessage(
            @Payload MessageDto message,
            Message failedMessage) {

        log.warn("==============================================");
        log.warn("Dead Letter Message received!");
        log.warn("Message ID: {}", message.getId());
        log.warn("Original Exchange: {}", failedMessage.getMessageProperties().getReceivedExchange());
        log.warn("Original Routing Key: {}", failedMessage.getMessageProperties().getReceivedRoutingKey());
        log.warn("Failure Reason: {}", failedMessage.getMessageProperties().getHeaders().get("x-first-death-reason"));
        log.warn("==============================================");

        try {
            // DLQ Handler Service ile işle
            // Bu servis:
            // 1. Mesajı database'e kaydeder
            // 2. Hata analizini yapar
            // 3. Alert gönderir
            // 4. İstatistik tutar

            // Hatayı simüle et (gerçek senaryoda exception stacktrace'den gelir)
            Throwable simulatedCause = new RuntimeException(
                "Message failed after all retry attempts: " +
                failedMessage.getMessageProperties().getHeaders().get("x-first-death-reason")
            );

            dlqHandlerService.handleFailedMessage(message, failedMessage, simulatedCause);

            log.info("DLQ message handled successfully and saved to database");

        } catch (Exception e) {
            log.error("Error handling DLQ message: {}", e.getMessage(), e);
        }
    }

    /**
     * Mesajı işleyen iş mantığı
     * @param message İşlenecek mesaj
     */
    private void processMessage(MessageDto message) {
        log.info("Processing message - ID: {}, Type: {}",
                message.getId(),
                message.getMetadata() != null ? message.getMetadata().getType() : "N/A");

        // Test amaçlı hata simülasyonu
        if (message.getMetadata() != null && "TEST_FAILURE".equals(message.getMetadata().getType())) {
            log.warn("TEST_FAILURE message detected - simulating failure for retry testing");
            throw new RuntimeException("Simulated failure for testing retry mechanism");
        }

        // Burada gerçek iş mantığınız olacak
        // Örnek: Database kaydı, API çağrısı, dosya işleme, vb.

        // Simüle edilmiş işlem süresi
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("Message processed successfully - ID: {}", message.getId());
    }
}
