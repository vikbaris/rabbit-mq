package com.example.rabbitmq.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Retry Configuration
 * Özelleştirilmiş retry mekanizması:
 * - İlk retry: 2 saniye sonra
 * - İkinci retry: 5 saniye sonra
 * - Üçüncü retry: 30 saniye sonra
 * - Başarısız olursa DLQ'ya gider
 */
@Slf4j
@Configuration
public class CustomRetryConfig {

    @Value("${rabbitmq.retry.intervals:2,5,30}")
    private String retryIntervals;

    @Value("${rabbitmq.retry.max-attempts:3}")
    private int maxAttempts;

    /**
     * Custom retry intervals listesi oluştur
     */
    private List<Long> getRetryIntervals() {
        List<Long> intervals = new ArrayList<>();
        String[] parts = retryIntervals.split(",");
        for (String part : parts) {
            intervals.add(Long.parseLong(part.trim()) * 1000); // saniyeyi milisaniyeye çevir
        }
        return intervals;
    }

    /**
     * Custom Rabbit Listener Container Factory
     * Custom retry policy ile yapılandırılmış
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            MessageConverter messageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);

        factory.setMessageConverter(messageConverter);

        // Advice chain ile retry ve recovery
        factory.setAdviceChain(
                org.springframework.amqp.rabbit.config.RetryInterceptorBuilder
                        .stateless()
                        .retryOperations(createCustomRetryTemplate())
                        .recoverer(messageRecoverer())
                        .build()
        );

        return factory;
    }

    /**
     * Message Recoverer Bean
     * Başarısız mesajları DLQ'ya gönderir
     */
    @Bean
    public MessageRecoverer messageRecoverer() {
        return (message, cause) -> {
            log.error("==============================================");
            log.error("Recovering message after all retry attempts failed");
            log.error("Message ID: {}", message.getMessageProperties().getMessageId());
            log.error("Message Body: {}", new String(message.getBody()));
            log.error("Failure Cause: {}", cause.getMessage());
            log.error("==============================================");

            // DLQ'ya gönder - RabbitMQ otomatik olarak dead letter exchange'e gönderecek
            // Çünkü queue konfigürasyonunda x-dead-letter-exchange tanımlı
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException(
                    "Message processing failed after all retries", cause);
        };
    }

    /**
     * Custom RetryTemplate oluştur
     * Her retry için farklı bekleme süresi
     */
    private RetryTemplate createCustomRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Custom retry policy
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(maxAttempts);
        retryTemplate.setRetryPolicy(retryPolicy);

        // Custom backoff policy
        CustomIntervalBackOffPolicy backOffPolicy = new CustomIntervalBackOffPolicy();
        backOffPolicy.setRetryIntervals(getRetryIntervals());
        retryTemplate.setBackOffPolicy(backOffPolicy);

        // Retry listener
        retryTemplate.registerListener(new org.springframework.retry.RetryListener() {
            @Override
            public <T, E extends Throwable> void onError(
                    org.springframework.retry.RetryContext context,
                    org.springframework.retry.RetryCallback<T, E> callback,
                    Throwable throwable) {

                int retryCount = context.getRetryCount();
                log.warn("Retry attempt #{} failed. Error: {}", retryCount, throwable.getMessage());

                if (retryCount < maxAttempts) {
                    List<Long> intervals = getRetryIntervals();
                    long nextBackoff = retryCount <= intervals.size()
                            ? intervals.get(retryCount - 1)
                            : intervals.get(intervals.size() - 1);

                    log.info("Next retry will occur in {} seconds", nextBackoff / 1000);
                }
            }
        });

        return retryTemplate;
    }

    /**
     * Custom BackOffPolicy
     * Her retry için özelleştirilmiş bekleme süresi
     */
    public static class CustomIntervalBackOffPolicy extends FixedBackOffPolicy {

        private List<Long> retryIntervals;
        private int currentAttempt = 0;

        public void setRetryIntervals(List<Long> retryIntervals) {
            this.retryIntervals = retryIntervals;
        }

        @Override
        protected void doBackOff() throws org.springframework.retry.backoff.BackOffInterruptedException {
            try {
                long backoffPeriod = getBackOffPeriod();
                Thread.sleep(backoffPeriod);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new org.springframework.retry.backoff.BackOffInterruptedException("Thread interrupted", e);
            } finally {
                currentAttempt++;
            }
        }

        @Override
        public long getBackOffPeriod() {
            if (retryIntervals == null || retryIntervals.isEmpty()) {
                return super.getBackOffPeriod();
            }

            // Mevcut attempt için interval döndür
            int index = Math.min(currentAttempt, retryIntervals.size() - 1);
            return retryIntervals.get(index);
        }
    }
}
