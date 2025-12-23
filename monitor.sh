#!/bin/bash

# RabbitMQ Monitoring Script
# Her 3 saniyede bir DLQ durumunu kontrol eder

echo "🔍 RabbitMQ DLQ Monitoring Started..."
echo "Press Ctrl+C to stop"
echo ""

while true; do
    clear
    echo "═══════════════════════════════════════════════════════════"
    echo "  📊 RabbitMQ DLQ Dashboard - $(date '+%H:%M:%S')"
    echo "═══════════════════════════════════════════════════════════"
    echo ""

    # DLQ Dashboard
    echo "📈 DLQ Statistics:"
    curl -s http://localhost:8080/api/dlq/dashboard | jq '{
        total: .totalFailedMessages,
        new: .newMessages,
        last_hour: .failedLastHour,
        last_24h: .failedLast24Hours,
        by_status: .byStatus
    }'

    echo ""
    echo "───────────────────────────────────────────────────────────"
    echo ""

    # Son 5 mesaj
    echo "📋 Latest Failed Messages:"
    curl -s http://localhost:8080/api/dlq/messages | jq -r '
        .[:5] | .[] |
        "ID: \(.id) | MessageID: \(.messageId) | Status: \(.status) | Error: \(.errorMessage[:50])..."
    '

    echo ""
    echo "═══════════════════════════════════════════════════════════"

    sleep 3
done
