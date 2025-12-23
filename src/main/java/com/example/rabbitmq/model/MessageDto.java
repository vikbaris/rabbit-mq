package com.example.rabbitmq.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * RabbitMQ üzerinden gönderilecek mesaj modeli
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto implements Serializable {

    @JsonProperty("id")
    private String id;

    @JsonProperty("content")
    private String content;

    @JsonProperty("sender")
    private String sender;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("metadata")
    private MessageMetadata metadata;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageMetadata {
        @JsonProperty("priority")
        private String priority;

        @JsonProperty("type")
        private String type;

        @JsonProperty("version")
        private String version;
    }
}
