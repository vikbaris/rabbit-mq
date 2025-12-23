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

## Topic Exchange - Pattern Matching

**Topic Exchange**, routing key üzerinde pattern matching (kalıp eşleştirme) yaparak mesajları ilgili queue'lara otomatik olarak yönlendirir.

### Wildcard Karakterleri

- **`*` (yıldız)** = Tam olarak **bir kelime** eşleşir
- **`#` (hash)** = **Sıfır veya daha fazla kelime** eşleşir

### Kullanılan Yapı

```
Topic Exchange: topic.exchange

Queue ve Pattern'ler:
├── log.queue              → Pattern: "log.#"           (log.*, log.*.*, vb)
├── notification.queue     → Pattern: "notification.*"  (notification.email, notification.sms)
├── analytics.queue        → Pattern: "*.analytics"     (user.analytics, order.analytics)
└── order.queue            → Pattern: "order.*"         (order.created, order.updated, vb)
```

### Consumer'lar

- [LogConsumer.java](src/main/java/com/example/rabbitmq/consumer/topic/LogConsumer.java) - Log mesajlarını işler
- [NotificationConsumer.java](src/main/java/com/example/rabbitmq/consumer/topic/NotificationConsumer.java) - Bildirimleri işler
- [AnalyticsConsumer.java](src/main/java/com/example/rabbitmq/consumer/topic/AnalyticsConsumer.java) - Analytics verilerini işler
- [OrderConsumer.java](src/main/java/com/example/rabbitmq/consumer/topic/OrderConsumer.java) - Sipariş eventlerini işler

### REST API Endpoints

#### 1. Log Mesajı Gönder (Pattern: `log.#`)

```bash
# Basit log
curl -X POST "http://localhost:8080/api/messages/topic/log?level=error&content=Database%20error&sender=System"

# Kategorili log (log.warning.security)
curl -X POST "http://localhost:8080/api/messages/topic/log?level=warning&category=security&content=Suspicious%20activity&sender=SecurityService"

# Detaylı log (log.info.database.query)
curl -X POST "http://localhost:8080/api/messages/topic/log?level=info&category=database&content=Slow%20query&sender=MonitoringService"
```

**Eşleşen Mesajlar:**
- `log.error` ✅
- `log.info.security` ✅
- `log.warning.database.timeout` ✅

#### 2. Notification Gönder (Pattern: `notification.*`)

```bash
# Email notification
curl -X POST "http://localhost:8080/api/messages/topic/notification?type=email&content=Order%20shipped&sender=OrderService"

# SMS notification
curl -X POST "http://localhost:8080/api/messages/topic/notification?type=sms&content=Verification%20code&sender=AuthService"

# Push notification
curl -X POST "http://localhost:8080/api/messages/topic/notification?type=push&content=New%20message&sender=ChatService"
```

**Eşleşen Mesajlar:**
- `notification.email` ✅
- `notification.sms` ✅
- `notification.push` ✅

**Eşleşmeyen Mesajlar:**
- `notification` ❌ (1 kelime, 2 bekleniyor)
- `notification.email.urgent` ❌ (3 kelime, 2 bekleniyor)

#### 3. Analytics Gönder (Pattern: `*.analytics`)

```bash
# User analytics
curl -X POST "http://localhost:8080/api/messages/topic/analytics?source=user&content=User%20login&sender=UserService"

# Order analytics
curl -X POST "http://localhost:8080/api/messages/topic/analytics?source=order&content=Order%20completed&sender=OrderService"

# Payment analytics
curl -X POST "http://localhost:8080/api/messages/topic/analytics?source=payment&content=Payment%20success&sender=PaymentService"
```

**Eşleşen Mesajlar:**
- `user.analytics` ✅
- `order.analytics` ✅
- `payment.analytics` ✅

**Eşleşmeyen Mesajlar:**
- `analytics` ❌ (1 kelime, 2 bekleniyor)
- `user.order.analytics` ❌ (3 kelime, 2 bekleniyor)

#### 4. Order Event Gönder (Pattern: `order.*`)

```bash
# Order created
curl -X POST "http://localhost:8080/api/messages/topic/order?event=created&content=Order%20%2312345%20created&sender=OrderService"

# Order updated
curl -X POST "http://localhost:8080/api/messages/topic/order?event=updated&content=Order%20updated&sender=OrderService"

# Order cancelled
curl -X POST "http://localhost:8080/api/messages/topic/order?event=cancelled&content=Order%20cancelled&sender=OrderService"
```

**Eşleşen Mesajlar:**
- `order.created` ✅
- `order.updated` ✅
- `order.cancelled` ✅
- `order.completed` ✅

#### 5. Tüm Pattern'leri Test Et

```bash
curl -X POST http://localhost:8080/api/messages/topic/test-all
```

Bu endpoint şu mesajları gönderir:
- `log.error.database` → `log.queue`
- `notification.email` → `notification.queue`
- `user.analytics` → `analytics.queue`
- `order.created` → `order.queue`

### Topic Exchange'in Faydaları

| Özellik | Direct Exchange | Topic Exchange |
|---------|-----------------|----------------|
| **Routing** | Tam eşleşme | Pattern matching |
| **Scaling** | Hep birlikte | Bağımsız |
| **Yeni Queue** | Kod değişikliği | Sadece config |
| **Performance** | Consumer'da filter | RabbitMQ'da filter |
| **Monitoring** | Toplam mesaj | Queue bazında |

### Gerçek Hayat Örneği

**E-ticaret Sipariş Oluşturulduğunda:**

```
sendOrderEvent("created", "Order #12345", "OrderService")
         ↓ (routing key: "order.created")
Topic Exchange
         ↓ Pattern matching
    ┌─────────────────────────────────┐
    ↓                                  ↓
order.queue                    notification.queue
(order.* pattern)              (notification.* pattern)
    ↓                                  ↓
OrderService                   NotificationService
(Siparişi kaydet)              (Email gönder)

    ↓ (Aynı mesaj, 5 queue'ya gider)
    ├─→ order.queue (Sipariş işleme)
    ├─→ notification.queue (Email gönder)
    ├─→ analytics.queue (Satış metriği)
    ├─→ inventory.queue (Stok güncelle)
    └─→ invoice.queue (Fatura oluştur)
```

### Kod Örneği

```java
// Producer - Mesaj gönder
@Autowired
private MessageProducer messageProducer;

// Log gönder
messageProducer.sendLogMessage("error", "database", "Connection timeout", "System");

// Notification gönder
messageProducer.sendNotification("email", "Order confirmed", "OrderService");

// Analytics gönder
messageProducer.sendAnalytics("user", "Login from mobile", "UserService");

// Order event gönder
messageProducer.sendOrderEvent("created", "Order #12345", "OrderService");
```

### Swagger UI ile Test

Topic Exchange endpoint'leri Swagger UI'da görüntüleyin:

```
http://localhost:8080/swagger-ui/index.html
```

**Bölüm:** "Message Producer" → "Topic Exchange" endpoints

---

## Use Case Senaryoları

Şimdi aşağıdaki use case'leri ekleyebiliriz:

1. **E-commerce Sipariş İşleme** (Topic Exchange ile)
2. **Email/SMS Notification Service** (Fanout Exchange ile)
3. **Log Processing & Analytics** (Topic Exchange ile)
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
