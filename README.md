# Spring Boot RabbitMQ Producer & Consumer Demo

Spring Framework ile RabbitMQ Producer ve Consumer servisleri implementasyonu.

## Özellikler

- **RabbitMQ Producer**: Mesaj gönderme servisi
- **RabbitMQ Consumer**: Mesaj dinleme ve işleme servisi
- **Dead Letter Queue (DLQ)**: Başarısız mesajlar için
- **Publisher Confirms**: Mesaj gönderim onayı
- **Manual/Auto Acknowledgement**: Farklı ACK modları
- **REST API**: HTTP üzerinden test endpoint'leri
- **JSON Serialization**: Jackson ile JSON destek

## Teknolojiler

- Java 17
- Spring Boot 3.2.1
- Spring AMQP (RabbitMQ)
- Lombok
- Maven

## Ön Gereksinimler

### RabbitMQ Kurulumu

**Docker ile:**
```bash
docker run -d --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:3-management
```

**Homebrew ile (macOS):**
```bash
brew install rabbitmq
brew services start rabbitmq
```

RabbitMQ Management Console: http://localhost:15672 (guest/guest)

## Proje Yapısı

```
src/main/java/com/example/rabbitmq/
├── RabbitMQApplication.java          # Ana uygulama
├── config/
│   └── RabbitMQConfig.java           # RabbitMQ konfigürasyonu
├── model/
│   └── MessageDto.java               # Mesaj modeli
├── producer/
│   └── MessageProducer.java          # Producer servisi
├── consumer/
│   └── MessageConsumer.java          # Consumer servisi
└── controller/
    └── MessageController.java        # REST API
```

## Kurulum ve Çalıştırma

### 1. Projeyi Build Edin

```bash
mvn clean install
```

### 2. Uygulamayı Başlatın

```bash
mvn spring-boot:run
```

veya

```bash
java -jar target/rabbitmq-demo-1.0.0.jar
```

### 3. RabbitMQ Bağlantısını Kontrol Edin

Uygulama loglarında şu mesajı göreceksiniz:
```
Created new connection: connectionFactory#...
```

## Kullanım

### REST API Endpoints

#### 1. Basit Mesaj Gönderme (GET)

```bash
curl "http://localhost:8080/api/messages/send?content=Hello&sender=User1"
```

#### 2. JSON Body ile Mesaj Gönderme (POST)

```bash
curl -X POST http://localhost:8080/api/messages/send \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Test message",
    "sender": "TestUser",
    "metadata": {
      "priority": "HIGH",
      "type": "TEST",
      "version": "1.0"
    }
  }'
```

#### 3. Priority Mesaj Gönderme

```bash
curl -X POST "http://localhost:8080/api/messages/send/priority?content=Urgent&sender=Admin&priority=HIGH"
```

#### 4. Publisher Confirm ile Mesaj

```bash
curl -X POST http://localhost:8080/api/messages/send/confirm \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Confirmed message",
    "sender": "System"
  }'
```

#### 5. Bulk Mesaj Gönderme

```bash
curl -X POST "http://localhost:8080/api/messages/send/bulk?count=100&sender=BulkSystem"
```

#### 6. Health Check

```bash
curl http://localhost:8080/api/messages/health
```

### Producer Kullanımı (Kod)

```java
@Autowired
private MessageProducer messageProducer;

// Basit mesaj
messageProducer.sendMessage("Hello RabbitMQ", "User1");

// Priority mesaj
messageProducer.sendMessageWithPriority("Urgent task", "Admin", "HIGH");

// Custom MessageDto ile
MessageDto message = new MessageDto();
message.setContent("Custom message");
message.setSender("System");
messageProducer.sendMessage(message);
```

### Consumer Davranışı

Consumer otomatik olarak queue'yu dinler ve gelen mesajları işler:

**Auto Acknowledge:**
```java
@RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
public void receiveMessage(@Payload MessageDto message) {
    // Mesaj işlenir
    // Başarılı ise otomatik ACK
    // Hata fırlatılırsa retry sonrası DLQ'ya gider
}
```

**Manual Acknowledge:**
```java
@RabbitListener(queues = RabbitMQConfig.QUEUE_NAME, ackMode = "MANUAL")
public void receiveMessageWithManualAck(...) {
    // Başarılı: channel.basicAck(deliveryTag, false)
    // Başarısız: channel.basicNack(deliveryTag, false, false)
}
```

## Konfigürasyon

### application.yml

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

    publisher-confirm-type: correlated
    publisher-returns: true

    listener:
      simple:
        acknowledge-mode: auto
        prefetch: 10
        retry:
          enabled: true
          initial-interval: 3000
          max-attempts: 3
```

### Queue Yapılandırması

- **Main Queue**: `example.queue`
- **Exchange**: `example.exchange` (Direct)
- **Routing Key**: `example.routing.key`
- **DLQ**: `example.dlq`
- **DLQ Exchange**: `example.dlq.exchange`

## Özelleştirilmiş Retry Mekanizması

Proje özel retry intervallerine sahip:

```
1. İlk retry:    2 saniye sonra
2. İkinci retry: 5 saniye sonra
3. Üçüncü retry: 30 saniye sonra
4. Başarısız:    DLQ'ya gönderilir
```

Retry konfigürasyonu [application.yml](src/main/resources/application.yml):
```yaml
rabbitmq:
  retry:
    intervals: 2,5,30  # Saniye cinsinden
    max-attempts: 3
```

Custom retry implementation: [CustomRetryConfig.java](src/main/java/com/example/rabbitmq/config/CustomRetryConfig.java)

### Retry Mekanizmasını Test Etme

Kasıtlı olarak hata fırlatacak mesaj gönderin:

```bash
curl -X POST "http://localhost:8080/api/messages/send/test-failure?content=Test"
```

Logları izleyin:
```
Retry attempt #1 failed. Error: Simulated failure
Next retry will occur in 2 seconds

Retry attempt #2 failed. Error: Simulated failure
Next retry will occur in 5 seconds

Retry attempt #3 failed. Error: Simulated failure
Next retry will occur in 30 seconds

Message sent to DLQ: example.dlq
```

## Dead Letter Queue (DLQ) Yönetimi

Başarısız mesajlar DLQ'ya düştüğünde otomatik olarak:

✅ **Database'e kaydedilir** - H2 in-memory database
✅ **Hata analizi yapılır** - Hata tipi kategorize edilir
✅ **Alert gönderilir** - Log üzerinden bildirim
✅ **İstatistik tutulur** - Durum bazında raporlama

### DLQ İşlem Akışı

```
Message Failed (3 retry sonrası)
    ↓
Dead Letter Queue
    ↓
DLQHandlerService
    ├─→ Database'e kaydet (failed_messages tablosu)
    ├─→ Hata analizi yap (TIMEOUT, NULL_POINTER, DATABASE, vb.)
    ├─→ Alert gönder (Console log + gerçek uygulamada Email/Slack)
    └─→ İstatistik güncelle
```

### DLQ Management API

#### 1. Başarısız mesajları listele
```bash
curl http://localhost:8080/api/dlq/messages
```

#### 2. Status'e göre filtrele
```bash
curl http://localhost:8080/api/dlq/messages/status/NEW
```

Kullanılabilir status'ler:
- `NEW` - Yeni DLQ mesajı
- `INVESTIGATING` - İnceleniyor
- `RESOLVED` - Çözüldü
- `IGNORED` - Göz ardı edildi
- `RETRYING` - Yeniden deneniyor
- `FAILED` - Kalıcı hata

#### 3. İstatistikleri görüntüle
```bash
curl "http://localhost:8080/api/dlq/statistics?hours=24"
```

#### 4. Dashboard özeti
```bash
curl http://localhost:8080/api/dlq/dashboard
```

Çıktı örneği:
```json
{
  "byStatus": {
    "NEW": 5,
    "INVESTIGATING": 2,
    "RESOLVED": 10
  },
  "failedLast24Hours": 17,
  "failedLastHour": 5,
  "totalFailedMessages": 17,
  "newMessages": 5,
  "retryingMessages": 0
}
```

#### 5. Mesaj durumunu güncelle
```bash
curl -X PUT "http://localhost:8080/api/dlq/messages/1/status?status=INVESTIGATING&notes=Araştırılıyor"
```

#### 6. Mesajı yeniden işleme için işaretle
```bash
curl -X POST http://localhost:8080/api/dlq/messages/1/retry
```

#### 7. Eski mesajları temizle (retention policy)
```bash
curl -X DELETE http://localhost:8080/api/dlq/cleanup
```

### H2 Database Console

DLQ mesajlarını doğrudan database'den görüntüleyin:

```
URL: http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:rabbitmq_dlq
Username: sa
Password: (boş)
```

Yararlı SQL sorguları:
```sql
-- Tüm başarısız mesajları listele
SELECT * FROM failed_messages ORDER BY created_at DESC;

-- Status bazında sayım
SELECT status, COUNT(*) as count
FROM failed_messages
GROUP BY status;

-- Son 1 saatteki hatalar
SELECT * FROM failed_messages
WHERE created_at > DATEADD('HOUR', -1, CURRENT_TIMESTAMP);

-- Alert gönderilmemiş mesajlar
SELECT * FROM failed_messages
WHERE alert_sent = FALSE;

-- En çok hata alan sender'lar
SELECT sender, COUNT(*) as error_count
FROM failed_messages
GROUP BY sender
ORDER BY error_count DESC;
```

### DLQ Mesaj Yapısı

Database'e kaydedilen mesaj alanları:

| Alan | Açıklama |
|------|----------|
| `message_id` | Orijinal mesaj ID |
| `message_body` | JSON formatında mesaj içeriği |
| `sender` | Mesajı gönderen |
| `error_message` | Hata mesajı |
| `stack_trace` | Tam stack trace |
| `original_exchange` | Orijinal exchange adı |
| `original_routing_key` | Orijinal routing key |
| `retry_count` | Kaç kez retry denendi |
| `status` | Mesaj durumu |
| `created_at` | DLQ'ya düşme zamanı |
| `alert_sent` | Alert gönderildi mi |
| `notes` | Analiz notları |

## Monitoring

### RabbitMQ Management UI

http://localhost:15672 adresinde:
- Queue durumu
- Mesaj sayıları
- Consumer bağlantıları
- Exchange ve binding'ler

### Uygulama Logları

```bash
tail -f logs/application.log
```

Consumer logları:
```
Message received from queue: example.queue
Message ID: 12345
Content: Hello RabbitMQ
Sender: User1
```

## Use Case Senaryoları

Şimdi aşağıdaki use case'leri ekleyebiliriz:

1. **E-commerce Sipariş İşleme**
2. **Email/SMS Notification Service**
3. **Log Processing & Analytics**
4. **Image/Video Processing Pipeline**
5. **Microservice Communication**

Hangi use case'lerle devam etmek istersiniz?

## Troubleshooting

### RabbitMQ'ya bağlanamıyor

```bash
# RabbitMQ çalışıyor mu?
docker ps | grep rabbitmq

# Port açık mı?
nc -zv localhost 5672
```

### Consumer mesaj almıyor

- Queue ve binding'leri kontrol edin: Management UI
- Routing key'lerin eşleştiğinden emin olun
- Consumer'ın çalıştığını loglardan kontrol edin

## Geliştirme

Proje şu şekilde genişletilebilir:

- Multiple queue support
- Topic/Fanout exchange patterns
- Message priority queues
- Delayed message plugin
- Cluster configuration
- Metrics ve monitoring (Prometheus/Grafana)
