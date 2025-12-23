package com.example.rabbitmq.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger Configuration
 * API dokümantasyonu ve test arayüzü
 */
@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI rabbitMQOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RabbitMQ Producer & Consumer API")
                        .description("""
                                Spring Boot RabbitMQ Producer ve Consumer servisleri için REST API.

                                ## Özellikler

                                - **Message Producer**: Farklı yöntemlerle mesaj gönderme
                                - **Dead Letter Queue (DLQ) Management**: Başarısız mesajları yönetme
                                - **Custom Retry**: 2s, 5s, 30s retry intervalları
                                - **Database Integration**: H2 ile DLQ mesaj loglama

                                ## Retry Mekanizması

                                Mesajlar hata aldığında:
                                1. İlk retry: 2 saniye sonra
                                2. İkinci retry: 5 saniye sonra
                                3. Üçüncü retry: 30 saniye sonra
                                4. Başarısız: DLQ'ya gönderilir

                                ## Önemli Endpoint'ler

                                - `/api/messages/send/test-failure` - Retry mekanizmasını test et
                                - `/api/dlq/dashboard` - DLQ özet bilgisi
                                - `/api/dlq/messages` - Başarısız mesajları listele
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("RabbitMQ Demo")
                                .email("support@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("Docker Container")
                ));
    }
}
