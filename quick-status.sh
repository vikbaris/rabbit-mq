#!/bin/bash

echo "🔍 RabbitMQ Quick Status Check"
echo "════════════════════════════════════════════════════════"
echo ""

echo "1️⃣  Application Health:"
curl -s http://localhost:8080/api/messages/health | jq -r '"Status: " + .status'
echo ""

echo "2️⃣  DLQ Dashboard:"
curl -s http://localhost:8080/api/dlq/dashboard | jq '{
    "Total Failed": .totalFailedMessages,
    "Last Hour": .failedLastHour,
    "New Messages": .newMessages,
    "Status Breakdown": .byStatus
}'
echo ""

echo "3️⃣  Recent Failed Messages:"
curl -s http://localhost:8080/api/dlq/messages | jq -r '
    if length == 0 then
        "No failed messages yet"
    else
        .[:3] | .[] |
        "  • ID: \(.id) | MsgID: \(.messageId) | Status: \(.status) | Time: \(.createdAt)"
    end
'
echo ""

echo "4️⃣  RabbitMQ Queues (via Management API):"
curl -s -u guest:guest http://localhost:15672/api/queues | jq -r '
    .[] |
    select(.name | startswith("example")) |
    "  • \(.name): Ready=\(.messages_ready), Unacked=\(.messages_unacknowledged)"
'
echo ""
echo "════════════════════════════════════════════════════════"
