package com.example.rabbitmq.controller;

import com.example.rabbitmq.model.MessageDto;
import com.example.rabbitmq.producer.MessageProducer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for testing RabbitMQ Producer
 * HTTP endpoint'leri ile mesaj gönderme
 */
@Tag(name = "Message Producer", description = "RabbitMQ mesaj gönderme API'leri")
@Slf4j
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageProducer messageProducer;

    @Operation(
            summary = "Basit mesaj gönder",
            description = "Query parametreleri ile RabbitMQ'ya basit mesaj gönderir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mesaj başarıyla gönderildi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @GetMapping("/send")
    public ResponseEntity<Map<String, String>> sendSimpleMessage(
            @Parameter(description = "Mesaj içeriği", required = true, example = "Hello RabbitMQ")
            @RequestParam String content,
            @Parameter(description = "Mesaj gönderen", example = "User1")
            @RequestParam(defaultValue = "Anonymous") String sender) {

        log.info("REST request to send message - Content: {}, Sender: {}", content, sender);

        messageProducer.sendMessage(content, sender);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Message sent to RabbitMQ");
        response.put("content", content);
        response.put("sender", sender);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "JSON body ile mesaj gönder",
            description = "Detaylı MessageDto objesi ile RabbitMQ'ya mesaj gönderir",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Gönderilecek mesaj objesi",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = MessageDto.class),
                            examples = @ExampleObject(
                                    name = "Örnek Mesaj",
                                    value = """
                                            {
                                              "content": "Test mesajı",
                                              "sender": "User1",
                                              "metadata": {
                                                "priority": "HIGH",
                                                "type": "INFO",
                                                "version": "1.0"
                                              }
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mesaj başarıyla gönderildi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz mesaj formatı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendMessage(@RequestBody MessageDto message) {

        log.info("REST request to send message - ID: {}", message.getId());

        // ID yoksa oluştur
        if (message.getId() == null || message.getId().isEmpty()) {
            message.setId(UUID.randomUUID().toString());
        }

        // Timestamp yoksa oluştur
        if (message.getTimestamp() == null) {
            message.setTimestamp(LocalDateTime.now());
        }

        messageProducer.sendMessage(message);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Message sent to RabbitMQ");
        response.put("messageId", message.getId());

        return ResponseEntity.ok(response);
    }

    /**
     * Priority ile mesaj gönderme
     * POST /api/messages/send/priority
     */
    @PostMapping("/send/priority")
    public ResponseEntity<Map<String, String>> sendPriorityMessage(
            @RequestParam String content,
            @RequestParam String sender,
            @RequestParam(defaultValue = "NORMAL") String priority) {

        log.info("REST request to send priority message - Priority: {}", priority);

        messageProducer.sendMessageWithPriority(content, sender, priority);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Priority message sent to RabbitMQ");
        response.put("priority", priority);

        return ResponseEntity.ok(response);
    }

    /**
     * Publisher confirm ile mesaj gönderme
     * POST /api/messages/send/confirm
     */
    @PostMapping("/send/confirm")
    public ResponseEntity<Map<String, String>> sendMessageWithConfirm(@RequestBody MessageDto message) {

        log.info("REST request to send message with confirm");

        if (message.getId() == null || message.getId().isEmpty()) {
            message.setId(UUID.randomUUID().toString());
        }

        if (message.getTimestamp() == null) {
            message.setTimestamp(LocalDateTime.now());
        }

        messageProducer.sendMessageWithConfirm(message);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Message sent with publisher confirm");
        response.put("messageId", message.getId());

        return ResponseEntity.ok(response);
    }

    /**
     * Bulk mesaj gönderme
     * POST /api/messages/send/bulk?count=10
     */
    @PostMapping("/send/bulk")
    public ResponseEntity<Map<String, Object>> sendBulkMessages(
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "BulkSender") String sender) {

        log.info("REST request to send {} bulk messages", count);

        for (int i = 0; i < count; i++) {
            String content = "Bulk message #" + (i + 1);
            messageProducer.sendMessage(content, sender);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Bulk messages sent to RabbitMQ");
        response.put("count", count);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "🧪 Retry mekanizmasını test et",
            description = """
                    Kasıtlı olarak hata fırlatacak bir mesaj gönderir.

                    Bu mesaj consumer tarafından işlenirken hata verecek ve retry mekanizması tetiklenecektir:
                    - İlk retry: 2 saniye sonra
                    - İkinci retry: 5 saniye sonra
                    - Üçüncü retry: 30 saniye sonra
                    - Sonuç: DLQ'ya gönderilir (~40 saniye sonra)

                    Logları ve DLQ dashboard'u izleyerek retry akışını görebilirsiniz.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Test mesajı başarıyla gönderildi - Retry akışı başladı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PostMapping("/send/test-failure")
    public ResponseEntity<Map<String, String>> sendFailingMessage(
            @Parameter(description = "Test mesaj içeriği", example = "Test Retry Mechanism")
            @RequestParam(defaultValue = "Test Failure Message") String content) {

        log.info("REST request to send failing message for retry testing");

        // Özel bir metadata ile mesaj oluştur
        // Consumer bu metadata'yı görünce kasıtlı hata fırlatacak
        MessageDto.MessageMetadata metadata = new MessageDto.MessageMetadata(
                "HIGH",
                "TEST_FAILURE",  // Bu tip consumer'da hata fırlatacak
                "1.0"
        );

        MessageDto message = new MessageDto(
                UUID.randomUUID().toString(),
                content,
                "TestSender",
                LocalDateTime.now(),
                metadata
        );

        messageProducer.sendMessage(message);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Failing message sent - will trigger retries and eventually DLQ");
        response.put("messageId", message.getId());
        response.put("retrySchedule", "2s, 5s, 30s then DLQ");

        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint
     * GET /api/messages/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "RabbitMQ Producer API");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    // ========== TOPIC EXCHANGE TEST ENDPOINTS ==========

    @Operation(
            summary = "📋 Log mesajı gönder (Topic Exchange)",
            description = """
                    Pattern matching ile log mesajı gönderir.

                    Routing Key Pattern: "log.{level}.{category}"
                    - level: error, info, warning, debug
                    - category: (opsiyonel) database, security, network vb.

                    Örnekler:
                    - log.error
                    - log.info.security
                    - log.warning.database

                    Log queue pattern: "log.#" (tüm log mesajlarını yakalar)
                    """
    )
    @PostMapping("/topic/log")
    public ResponseEntity<Map<String, String>> sendLogMessage(
            @Parameter(description = "Log seviyesi", required = true, example = "error")
            @RequestParam String level,
            @Parameter(description = "Log kategorisi", example = "database")
            @RequestParam(required = false) String category,
            @Parameter(description = "Log mesajı", required = true, example = "Database connection timeout")
            @RequestParam String content,
            @Parameter(description = "Gönderen", example = "LogService")
            @RequestParam(defaultValue = "System") String sender) {

        log.info("📋 REST: Sending LOG message - Level: {}, Category: {}", level, category);
        messageProducer.sendLogMessage(level, category, content, sender);

        String routingKey = category != null && !category.isEmpty()
                ? String.format("log.%s.%s", level, category)
                : String.format("log.%s", level);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Log message sent to topic exchange");
        response.put("routingKey", routingKey);
        response.put("pattern", "log.#");
        response.put("queue", "log.queue");

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "🔔 Notification gönder (Topic Exchange)",
            description = """
                    Pattern matching ile notification gönderir.

                    Routing Key Pattern: "notification.{type}"
                    - type: email, sms, push

                    Örnekler:
                    - notification.email ✅ (eşleşir)
                    - notification.sms ✅ (eşleşir)
                    - notification.email.urgent ❌ (eşleşmez, 3 kelime)

                    Notification queue pattern: "notification.*" (sadece 2 kelimeli)
                    """
    )
    @PostMapping("/topic/notification")
    public ResponseEntity<Map<String, String>> sendNotification(
            @Parameter(description = "Notification türü", required = true, example = "email")
            @RequestParam String type,
            @Parameter(description = "Notification içeriği", required = true, example = "Your order has been shipped")
            @RequestParam String content,
            @Parameter(description = "Gönderen", example = "NotificationService")
            @RequestParam(defaultValue = "System") String sender) {

        log.info("🔔 REST: Sending NOTIFICATION - Type: {}", type);
        messageProducer.sendNotification(type, content, sender);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Notification sent to topic exchange");
        response.put("routingKey", String.format("notification.%s", type));
        response.put("pattern", "notification.*");
        response.put("queue", "notification.queue");

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "📊 Analytics event gönder (Topic Exchange)",
            description = """
                    Pattern matching ile analytics eventi gönderir.

                    Routing Key Pattern: "{source}.analytics"
                    - source: user, order, payment, product

                    Örnekler:
                    - user.analytics ✅ (eşleşir)
                    - order.analytics ✅ (eşleşir)
                    - user.order.analytics ❌ (eşleşmez, 3 kelime)

                    Analytics queue pattern: "*.analytics" (son kelime analytics olmalı)
                    """
    )
    @PostMapping("/topic/analytics")
    public ResponseEntity<Map<String, String>> sendAnalytics(
            @Parameter(description = "Analytics kaynağı", required = true, example = "user")
            @RequestParam String source,
            @Parameter(description = "Analytics verisi", required = true, example = "User logged in from mobile")
            @RequestParam String content,
            @Parameter(description = "Gönderen", example = "AnalyticsService")
            @RequestParam(defaultValue = "System") String sender) {

        log.info("📊 REST: Sending ANALYTICS - Source: {}", source);
        messageProducer.sendAnalytics(source, content, sender);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Analytics event sent to topic exchange");
        response.put("routingKey", String.format("%s.analytics", source));
        response.put("pattern", "*.analytics");
        response.put("queue", "analytics.queue");

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "🛒 Order event gönder (Topic Exchange)",
            description = """
                    Pattern matching ile order eventi gönderir.

                    Routing Key Pattern: "order.{event}"
                    - event: created, updated, cancelled, completed, shipped

                    Örnekler:
                    - order.created ✅ (eşleşir)
                    - order.cancelled ✅ (eşleşir)
                    - order.status.changed ❌ (eşleşmez, 3 kelime)

                    Order queue pattern: "order.*" (order ile başlayan 2 kelimeli)
                    """
    )
    @PostMapping("/topic/order")
    public ResponseEntity<Map<String, String>> sendOrderEvent(
            @Parameter(description = "Order event türü", required = true, example = "created")
            @RequestParam String event,
            @Parameter(description = "Order detayı", required = true, example = "Order #12345 created with 3 items")
            @RequestParam String content,
            @Parameter(description = "Gönderen", example = "OrderService")
            @RequestParam(defaultValue = "System") String sender) {

        log.info("🛒 REST: Sending ORDER EVENT - Event: {}", event);
        messageProducer.sendOrderEvent(event, content, sender);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Order event sent to topic exchange");
        response.put("routingKey", String.format("order.%s", event));
        response.put("pattern", "order.*");
        response.put("queue", "order.queue");

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "🧪 Topic Exchange pattern matching testi",
            description = """
                    Tüm topic exchange pattern'lerini test eder.

                    Bu endpoint:
                    1. Log mesajı (log.error.database)
                    2. Notification (notification.email)
                    3. Analytics (user.analytics)
                    4. Order event (order.created)

                    gönderir ve her birinin hangi queue'ya gittiğini gösterir.

                    Logları kontrol ederek pattern matching'in nasıl çalıştığını görebilirsiniz.
                    """
    )
    @PostMapping("/topic/test-all")
    public ResponseEntity<Map<String, Object>> testAllTopicPatterns() {

        log.info("🧪 REST: Testing ALL topic exchange patterns");

        // 1. Log mesajı
        messageProducer.sendLogMessage("error", "database", "Connection timeout after 30 seconds", "TestService");

        // 2. Notification
        messageProducer.sendNotification("email", "Your order has been confirmed", "TestService");

        // 3. Analytics
        messageProducer.sendAnalytics("user", "User login from IP: 192.168.1.1", "TestService");

        // 4. Order event
        messageProducer.sendOrderEvent("created", "Order #12345 created successfully", "TestService");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "All topic exchange patterns tested");
        response.put("sentMessages", Map.of(
                "log", "log.error.database → log.queue",
                "notification", "notification.email → notification.queue",
                "analytics", "user.analytics → analytics.queue",
                "order", "order.created → order.queue"
        ));
        response.put("info", "Check logs to see pattern matching in action!");

        return ResponseEntity.ok(response);
    }
}
