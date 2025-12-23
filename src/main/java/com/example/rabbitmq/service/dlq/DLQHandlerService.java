package com.example.rabbitmq.service.dlq;

import com.example.rabbitmq.config.RabbitMQConfig;
import com.example.rabbitmq.entity.FailedMessage;
import com.example.rabbitmq.model.MessageDto;
import com.example.rabbitmq.repository.FailedMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DLQ Handler Service
 * Dead Letter Queue'dan gelen mesajları işler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DLQHandlerService {

    private final FailedMessageRepository failedMessageRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.dlq.alert-enabled:true}")
    private boolean alertEnabled;

    @Value("${rabbitmq.dlq.retention-hours:24}")
    private int retentionHours;

    /**
     * DLQ'dan gelen mesajı işle ve database'e kaydet
     */
    @Transactional
    public void handleFailedMessage(MessageDto message, Message rawMessage, Throwable cause) {
        try {
            log.info("Handling failed message from DLQ: {}", message.getId());

            // Failed message entity oluştur
            FailedMessage failedMessage = createFailedMessageEntity(message, rawMessage, cause);

            // Database'e kaydet
            failedMessageRepository.save(failedMessage);

            log.info("Failed message saved to database: ID={}, MessageID={}",
                    failedMessage.getId(), failedMessage.getMessageId());

            // Analiz yap
            analyzeFailure(failedMessage, cause);

            // Alert gönder (eğer enabled ise)
            if (alertEnabled && !failedMessage.getAlertSent()) {
                sendAlert(failedMessage);
                failedMessage.setAlertSent(true);
                failedMessageRepository.save(failedMessage);
            }

        } catch (Exception e) {
            log.error("Error handling failed message: {}", e.getMessage(), e);
        }
    }

    /**
     * Failed message entity oluştur
     */
    private FailedMessage createFailedMessageEntity(
            MessageDto message,
            Message rawMessage,
            Throwable cause) {

        Map<String, Object> headers = rawMessage.getMessageProperties().getHeaders();

        return FailedMessage.builder()
                .messageId(message.getId())
                .messageBody(convertToJson(message))
                .sender(message.getSender())
                .errorMessage(cause != null ? cause.getMessage() : "Unknown error")
                .stackTrace(getStackTraceAsString(cause))
                .originalExchange(rawMessage.getMessageProperties().getReceivedExchange())
                .originalRoutingKey(rawMessage.getMessageProperties().getReceivedRoutingKey())
                .retryCount(getRetryCount(headers))
                .status(FailedMessage.MessageStatus.NEW)
                .alertSent(false)
                .retryScheduled(false)
                .build();
    }

    /**
     * Hata analizini yap
     */
    private void analyzeFailure(FailedMessage failedMessage, Throwable cause) {
        String analysis = analyzeErrorType(cause);
        failedMessage.setNotes(analysis);

        log.info("Failure analysis for message {}: {}", failedMessage.getMessageId(), analysis);
    }

    /**
     * Hata tipini analiz et
     */
    private String analyzeErrorType(Throwable cause) {
        if (cause == null) {
            return "Unknown error - no exception details available";
        }

        String errorClass = cause.getClass().getSimpleName();
        String errorMessage = cause.getMessage();

        // Yaygın hata tiplerini kategorize et
        if (errorClass.contains("Timeout")) {
            return "TIMEOUT - Message processing exceeded time limit. Consider optimizing the consumer logic.";
        } else if (errorClass.contains("NullPointer")) {
            return "NULL_POINTER - Null value encountered. Check data validation in the consumer.";
        } else if (errorClass.contains("Json") || errorClass.contains("Jackson")) {
            return "SERIALIZATION - JSON parsing error. Verify message format compatibility.";
        } else if (errorClass.contains("Database") || errorClass.contains("SQL")) {
            return "DATABASE - Database operation failed. Check connection and query validity.";
        } else if (errorClass.contains("Network") || errorClass.contains("Connection")) {
            return "NETWORK - Network connectivity issue. Verify external service availability.";
        } else {
            return String.format("GENERAL_ERROR - %s: %s", errorClass, errorMessage);
        }
    }

    /**
     * Alert gönder (simüle edilmiş)
     */
    private void sendAlert(FailedMessage failedMessage) {
        log.warn("╔════════════════════════════════════════════════════════════╗");
        log.warn("║              DLQ ALERT - MESSAGE FAILED                    ║");
        log.warn("╠════════════════════════════════════════════════════════════╣");
        log.warn("║ Message ID: {}", String.format("%-45s", failedMessage.getMessageId()) + "║");
        log.warn("║ Sender: {}", String.format("%-49s", failedMessage.getSender()) + "║");
        log.warn("║ Error: {}", String.format("%-50s",
                truncate(failedMessage.getErrorMessage(), 50)) + "║");
        log.warn("║ Retry Count: {}", String.format("%-44s", failedMessage.getRetryCount()) + "║");
        log.warn("║ Timestamp: {}", String.format("%-46s", failedMessage.getCreatedAt()) + "║");
        log.warn("╚════════════════════════════════════════════════════════════╝");

        // Gerçek implementasyonda:
        // - Email gönder
        // - Slack notification
        // - PagerDuty alert
        // - Monitoring sistemine log
    }

    /**
     * MessageDto'yu JSON string'e çevir
     */
    private String convertToJson(MessageDto message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Error converting message to JSON: {}", e.getMessage());
            return message.toString();
        }
    }

    /**
     * Stack trace'i string'e çevir
     */
    private String getStackTraceAsString(Throwable cause) {
        if (cause == null) {
            return null;
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        cause.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * Retry count'u header'dan al
     */
    private Integer getRetryCount(Map<String, Object> headers) {
        Object retryHeader = headers.get("x-death");
        if (retryHeader instanceof List) {
            List<?> deaths = (List<?>) retryHeader;
            if (!deaths.isEmpty() && deaths.get(0) instanceof Map) {
                Map<?, ?> death = (Map<?, ?>) deaths.get(0);
                Object count = death.get("count");
                if (count instanceof Number) {
                    return ((Number) count).intValue();
                }
            }
        }
        return 0;
    }

    /**
     * String'i truncate et
     */
    private String truncate(String str, int length) {
        if (str == null) {
            return "";
        }
        return str.length() <= length ? str : str.substring(0, length - 3) + "...";
    }

    /**
     * Tüm başarısız mesajları getir
     */
    public List<FailedMessage> getAllFailedMessages() {
        return failedMessageRepository.findAll();
    }

    /**
     * Status'e göre başarısız mesajları getir
     */
    public List<FailedMessage> getFailedMessagesByStatus(FailedMessage.MessageStatus status) {
        return failedMessageRepository.findByStatus(status);
    }

    /**
     * Son N saatte başarısız olan mesaj sayısı
     */
    public long getFailedMessageCountSince(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return failedMessageRepository.countFailedMessagesSince(since);
    }

    /**
     * Status bazında istatistik
     */
    public Map<String, Long> getStatisticsByStatus() {
        List<Object[]> results = failedMessageRepository.countByStatusGrouped();
        return results.stream()
                .collect(java.util.stream.Collectors.toMap(
                        arr -> arr[0].toString(),
                        arr -> ((Number) arr[1]).longValue()
                ));
    }

    /**
     * Eski mesajları temizle (retention policy)
     */
    @Transactional
    public void cleanupOldMessages() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(retentionHours);
        failedMessageRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Cleaned up failed messages older than {} hours", retentionHours);
    }

    /**
     * Mesaj durumunu güncelle
     */
    @Transactional
    public void updateMessageStatus(Long id, FailedMessage.MessageStatus newStatus, String notes) {
        failedMessageRepository.findById(id).ifPresent(message -> {
            message.setStatus(newStatus);
            if (notes != null) {
                message.setNotes(message.getNotes() + "\n" + notes);
            }
            failedMessageRepository.save(message);
            log.info("Updated message {} status to {}", id, newStatus);
        });
    }

    /**
     * Mesajı yeniden işleme için işaretle ve HEMEN tekrar queue'ya gönder
     */
    @Transactional
    public void scheduleForRetry(Long id) {
        failedMessageRepository.findById(id).ifPresent(failedMessage -> {
            try {
                log.info("==============================================");
                log.info("🔄 RETRY: Starting retry for message ID: {}", failedMessage.getMessageId());

                // 1. MessageDto'yu JSON'dan deserialize et
                MessageDto messageDto = objectMapper.readValue(
                        failedMessage.getMessageBody(),
                        MessageDto.class
                );

                log.info("🔄 RETRY: Message deserialized - Sender: {}, Content: {}",
                        messageDto.getSender(),
                        messageDto.getContent());

                // 2. Mesajı tekrar ana queue'ya gönder
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_NAME,
                        RabbitMQConfig.ROUTING_KEY,
                        messageDto
                );

                log.info("🔄 RETRY: Message successfully sent back to queue");

                // 3. Database kaydını güncelle
                failedMessage.setRetryScheduled(true);
                failedMessage.setStatus(FailedMessage.MessageStatus.RETRYING);
                failedMessage.setNotes(
                        (failedMessage.getNotes() != null ? failedMessage.getNotes() + "\n" : "") +
                        "[" + LocalDateTime.now() + "] Manual retry triggered - Message sent back to queue"
                );
                failedMessageRepository.save(failedMessage);

                log.info("🔄 RETRY: Database updated - Status: RETRYING");
                log.info("==============================================");

            } catch (Exception e) {
                log.error("❌ RETRY FAILED: Error retrying message {}: {}",
                        failedMessage.getMessageId(),
                        e.getMessage(),
                        e);

                // Hata durumunda durumu güncelle
                failedMessage.setStatus(FailedMessage.MessageStatus.FAILED);
                failedMessage.setNotes(
                        (failedMessage.getNotes() != null ? failedMessage.getNotes() + "\n" : "") +
                        "[" + LocalDateTime.now() + "] Retry failed: " + e.getMessage()
                );
                failedMessageRepository.save(failedMessage);

                throw new RuntimeException("Failed to retry message", e);
            }
        });
    }
}
