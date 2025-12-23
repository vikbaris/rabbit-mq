package com.example.rabbitmq.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Failed Message Entity
 * DLQ'ya düşen mesajları database'de tutar
 */
@Entity
@Table(name = "failed_messages", indexes = {
        @Index(name = "idx_message_id", columnList = "messageId"),
        @Index(name = "idx_created_at", columnList = "createdAt"),
        @Index(name = "idx_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Orijinal mesaj ID'si
     */
    @Column(nullable = false, length = 100)
    private String messageId;

    /**
     * Mesaj içeriği (JSON)
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String messageBody;

    /**
     * Mesaj sender bilgisi
     */
    @Column(length = 100)
    private String sender;

    /**
     * Hata mesajı
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Stack trace
     */
    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    /**
     * Orijinal exchange
     */
    @Column(length = 100)
    private String originalExchange;

    /**
     * Orijinal routing key
     */
    @Column(length = 100)
    private String originalRoutingKey;

    /**
     * Retry sayısı
     */
    @Column(nullable = false)
    private Integer retryCount;

    /**
     * DLQ'ya düşme zamanı
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * Son işlem zamanı
     */
    @Column
    private LocalDateTime updatedAt;

    /**
     * Mesaj durumu
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageStatus status;

    /**
     * İşlenme notları
     */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Alert gönderildi mi
     */
    @Column(nullable = false)
    private Boolean alertSent;

    /**
     * Yeniden işleme denenecek mi
     */
    @Column(nullable = false)
    private Boolean retryScheduled;

    /**
     * Mesaj durumu enum
     */
    public enum MessageStatus {
        NEW,           // Yeni DLQ mesajı
        INVESTIGATING, // İnceleniyor
        RESOLVED,      // Çözüldü
        IGNORED,       // Göz ardı edildi
        RETRYING,      // Yeniden deneniyor
        FAILED         // Kalıcı hata
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = MessageStatus.NEW;
        }
        if (alertSent == null) {
            alertSent = false;
        }
        if (retryScheduled == null) {
            retryScheduled = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
