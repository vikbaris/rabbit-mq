package com.example.rabbitmq.controller;

import com.example.rabbitmq.entity.FailedMessage;
import com.example.rabbitmq.service.dlq.DLQHandlerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DLQ Management REST Controller
 * Dead Letter Queue mesajlarını yönetmek için API
 */
@Tag(name = "DLQ Management", description = "Dead Letter Queue yönetimi ve monitoring API'leri")
@Slf4j
@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
public class DLQManagementController {

    private final DLQHandlerService dlqHandlerService;

    @Operation(
            summary = "Tüm başarısız mesajları listele",
            description = "DLQ'ya düşen tüm mesajları database'den getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Başarılı - Mesaj listesi döndürüldü"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @GetMapping("/messages")
    public ResponseEntity<List<FailedMessage>> getAllFailedMessages() {
        log.info("REST request to get all failed messages");
        List<FailedMessage> messages = dlqHandlerService.getAllFailedMessages();
        return ResponseEntity.ok(messages);
    }

    @Operation(
            summary = "Status'e göre başarısız mesajları filtrele",
            description = "Belirli bir durumdaki (NEW, INVESTIGATING, RESOLVED, vb.) mesajları getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Başarılı - Filtrelenmiş mesaj listesi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz status değeri"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @GetMapping("/messages/status/{status}")
    public ResponseEntity<List<FailedMessage>> getFailedMessagesByStatus(
            @Parameter(
                    description = "Mesaj durumu",
                    example = "NEW",
                    required = true
            )
            @PathVariable FailedMessage.MessageStatus status) {

        log.info("REST request to get failed messages by status: {}", status);
        List<FailedMessage> messages = dlqHandlerService.getFailedMessagesByStatus(status);
        return ResponseEntity.ok(messages);
    }

    /**
     * DLQ istatistikleri
     * GET /api/dlq/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics(
            @RequestParam(defaultValue = "24") int hours) {

        log.info("REST request to get DLQ statistics for last {} hours", hours);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFailedLast" + hours + "Hours", dlqHandlerService.getFailedMessageCountSince(hours));
        stats.put("byStatus", dlqHandlerService.getStatisticsByStatus());
        stats.put("timestamp", java.time.LocalDateTime.now());

        return ResponseEntity.ok(stats);
    }

    /**
     * Mesaj durumunu güncelle
     * PUT /api/dlq/messages/{id}/status
     */
    @PutMapping("/messages/{id}/status")
    public ResponseEntity<Map<String, String>> updateMessageStatus(
            @PathVariable Long id,
            @RequestParam FailedMessage.MessageStatus status,
            @RequestParam(required = false) String notes) {

        log.info("REST request to update message {} status to {}", id, status);

        dlqHandlerService.updateMessageStatus(id, status, notes);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Message status updated");
        response.put("id", id.toString());
        response.put("newStatus", status.toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Mesajı yeniden işleme için işaretle
     * POST /api/dlq/messages/{id}/retry
     */
    @PostMapping("/messages/{id}/retry")
    public ResponseEntity<Map<String, String>> scheduleForRetry(@PathVariable Long id) {

        log.info("REST request to schedule message {} for retry", id);

        dlqHandlerService.scheduleForRetry(id);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Message scheduled for retry");
        response.put("id", id.toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Eski mesajları temizle
     * DELETE /api/dlq/cleanup
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, String>> cleanupOldMessages() {

        log.info("REST request to cleanup old DLQ messages");

        dlqHandlerService.cleanupOldMessages();

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Old messages cleaned up");

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "📊 DLQ Dashboard - Özet bilgiler",
            description = """
                    DLQ'daki mesajların özet istatistiklerini getirir:
                    - Status bazında gruplandırılmış sayılar
                    - Son 1 saat ve 24 saatteki hata sayıları
                    - Toplam başarısız mesaj sayısı
                    - Yeni ve retry bekleyen mesaj sayıları

                    Bu endpoint monitoring ve dashboard uygulamaları için idealdir.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Başarılı - Dashboard bilgileri döndürüldü"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {

        log.info("REST request to get DLQ dashboard");

        Map<String, Object> dashboard = new HashMap<>();

        // Durum bazında sayılar
        Map<String, Long> byStatus = dlqHandlerService.getStatisticsByStatus();
        dashboard.put("byStatus", byStatus);

        // Son 24 saat
        long last24h = dlqHandlerService.getFailedMessageCountSince(24);
        dashboard.put("failedLast24Hours", last24h);

        // Son 1 saat
        long last1h = dlqHandlerService.getFailedMessageCountSince(1);
        dashboard.put("failedLastHour", last1h);

        // Toplam
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        dashboard.put("totalFailedMessages", total);

        // Yeni mesajlar (alert gönderilmemiş)
        long newMessages = byStatus.getOrDefault("NEW", 0L);
        dashboard.put("newMessages", newMessages);

        // Yeniden denenmesi planlananlar
        long retrying = byStatus.getOrDefault("RETRYING", 0L);
        dashboard.put("retryingMessages", retrying);

        dashboard.put("timestamp", java.time.LocalDateTime.now());

        return ResponseEntity.ok(dashboard);
    }

    /**
     * Health check
     * GET /api/dlq/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "DLQ Management API");
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}
