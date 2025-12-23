#!/bin/bash

echo "🔍 RabbitMQ Connection Check"
echo "════════════════════════════════════════"
echo ""

# Docker check
echo "1️⃣  Docker Status:"
if docker info > /dev/null 2>&1; then
    echo "   ✅ Docker is running"
else
    echo "   ❌ Docker is NOT running"
    echo "   → Start Docker Desktop first"
    exit 1
fi
echo ""

# RabbitMQ container check
echo "2️⃣  RabbitMQ Container:"
if docker ps | grep -q rabbitmq; then
    echo "   ✅ RabbitMQ container is running"
    CONTAINER_ID=$(docker ps | grep rabbitmq | awk '{print $1}')
    echo "   Container ID: $CONTAINER_ID"
else
    echo "   ❌ RabbitMQ container is NOT running"
    echo "   → Run: docker-compose up -d"
    echo "   → Or: docker start rabbitmq"
    exit 1
fi
echo ""

# Port check
echo "3️⃣  Port Availability:"
if nc -z localhost 5672 2>/dev/null; then
    echo "   ✅ AMQP port 5672 is accessible"
else
    echo "   ❌ AMQP port 5672 is NOT accessible"
    exit 1
fi

if nc -z localhost 15672 2>/dev/null; then
    echo "   ✅ Management UI port 15672 is accessible"
else
    echo "   ⚠️  Management UI port 15672 is NOT accessible"
fi
echo ""

# Health check
echo "4️⃣  RabbitMQ Health:"
if docker exec $CONTAINER_ID rabbitmq-diagnostics -q ping > /dev/null 2>&1; then
    echo "   ✅ RabbitMQ is healthy and responding"
else
    echo "   ⚠️  RabbitMQ is starting or unhealthy"
    echo "   → Wait a few seconds and try again"
fi
echo ""

echo "════════════════════════════════════════"
echo "✅ All checks passed! RabbitMQ is ready."
echo ""
echo "You can now start your application with:"
echo "   mvn spring-boot:run"
echo ""
echo "Access Management UI at:"
echo "   http://localhost:15672 (guest/guest)"
