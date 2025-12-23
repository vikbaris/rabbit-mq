package com.example.rabbitmq.repository;

import com.example.rabbitmq.entity.FailedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Failed Message Repository
 * DLQ'daki mesajları yönetir
 */
@Repository
public interface FailedMessageRepository extends JpaRepository<FailedMessage, Long> {

    /**
     * Mesaj ID'sine göre bul
     */
    Optional<FailedMessage> findByMessageId(String messageId);

    /**
     * Status'e göre bul
     */
    List<FailedMessage> findByStatus(FailedMessage.MessageStatus status);

    /**
     * Alert gönderilmemiş mesajları bul
     */
    List<FailedMessage> findByAlertSentFalse();

    /**
     * Yeniden denenmesi planlanan mesajları bul
     */
    List<FailedMessage> findByRetryScheduledTrue();

    /**
     * Belirli tarihten sonra oluşturulan mesajları bul
     */
    List<FailedMessage> findByCreatedAtAfter(LocalDateTime after);

    /**
     * Belirli tarihten önce oluşturulan mesajları bul
     */
    List<FailedMessage> findByCreatedAtBefore(LocalDateTime before);

    /**
     * Status ve tarih aralığına göre bul
     */
    List<FailedMessage> findByStatusAndCreatedAtBetween(
            FailedMessage.MessageStatus status,
            LocalDateTime start,
            LocalDateTime end
    );

    /**
     * Sender'a göre bul
     */
    List<FailedMessage> findBySender(String sender);

    /**
     * Status'e göre sayım
     */
    long countByStatus(FailedMessage.MessageStatus status);

    /**
     * Son N günün başarısız mesaj sayısı
     */
    @Query("SELECT COUNT(f) FROM FailedMessage f WHERE f.createdAt >= :since")
    long countFailedMessagesSince(LocalDateTime since);

    /**
     * Status bazında gruplama
     */
    @Query("SELECT f.status, COUNT(f) FROM FailedMessage f GROUP BY f.status")
    List<Object[]> countByStatusGrouped();

    /**
     * Eski mesajları sil (retention policy)
     */
    void deleteByCreatedAtBefore(LocalDateTime before);
}
