# Performance Testing on MacBook - Developer Guide

**Target Audience:** Developers testing on local MacBook environments
**Purpose:** Validate functionality, identify bottlenecks, and perform small-scale performance testing
**Recommended MacBook Specs:** M1/M2/M3 with 16GB+ RAM (8GB minimum)

---

## Table of Contents
1. [Environment Limitations](#1-environment-limitations)
2. [MacBook Environment Setup](#2-macbook-environment-setup)
3. [Scaled-Down Test Scenarios](#3-scaled-down-test-scenarios)
4. [Testing Tools for MacBook](#4-testing-tools-for-macbook)
5. [Resource Monitoring](#5-resource-monitoring)
6. [Interpreting Results](#6-interpreting-results)
7. [When to Use Cloud Testing](#7-when-to-use-cloud-testing)

---

## 1. Environment Limitations

### 1.1 MacBook vs Production Comparison

| Component | Production Plan | MacBook Reality | Impact |
|-----------|----------------|-----------------|--------|
| **App Instances** | 3 instances (12 cores total) | 1 instance (2-4 cores) | ⚠️ No load balancing testing |
| **Redis** | 6-node cluster (12 cores) | 1 single instance (1 core) | ⚠️ No cluster failover testing |
| **MySQL** | Dedicated 8-core server | Shared Docker container | ⚠️ Limited concurrent connections |
| **Concurrent Users** | 10,000+ users | 100-1,000 users | ⚠️ Cannot test full production load |
| **Test Duration** | 4-hour endurance | 30-min to 1-hour | ⚠️ Limited long-term stability testing |
| **Network** | 10Gbps dedicated | Shared WiFi/localhost | ⚠️ Network not a bottleneck |

### 1.2 What You CAN Test on MacBook ✅

| Test Type | Feasibility | Value |
|-----------|-------------|-------|
| **Functional Testing** | ✅ Excellent | Verify features work correctly |
| **Small-Scale Load Testing** | ✅ Good (100-500 users) | Identify code-level bottlenecks |
| **Cache Strategy Testing** | ✅ Excellent | Verify cache hit rates, TTL logic |
| **Distributed Lock Testing** | ✅ Good | Test lock acquisition/release |
| **API Response Time** | ✅ Good | Baseline performance metrics |
| **Resource Profiling** | ✅ Excellent | JVM heap, GC analysis |
| **Database Query Analysis** | ✅ Excellent | Identify slow queries |

### 1.3 What You CANNOT Test on MacBook ❌

| Test Type | Why Not | Alternative |
|-----------|---------|-------------|
| **High Concurrency (10K+ users)** | Insufficient CPU/Memory | Use cloud services (AWS, GCP) |
| **Load Balancer Behavior** | Single instance only | Cloud environment required |
| **Redis Cluster Failover** | No cluster setup | Use Redis Cloud or AWS ElastiCache |
| **Multi-Region Testing** | No geographic distribution | Cloud infrastructure required |
| **72-Hour Endurance** | Ties up development machine | Use dedicated test environment |

---

## 2. MacBook Environment Setup

### 2.1 Resource Allocation for Docker

**Recommended Docker Desktop Settings:**

1. Open Docker Desktop → Settings → Resources

**For 16GB MacBook:**
```yaml
CPUs: 6 cores (leave 2 for macOS)
Memory: 10 GB (leave 6 for macOS)
Swap: 2 GB
Disk: 60 GB
```

**For 8GB MacBook (Minimum):**
```yaml
CPUs: 4 cores (leave 2 for macOS)
Memory: 5 GB (leave 3 for macOS)
Swap: 1 GB
Disk: 40 GB
```

**For M1/M2/M3 MacBook (32GB RAM):**
```yaml
CPUs: 8 cores (leave 2-4 for macOS)
Memory: 20 GB (leave 12 for macOS)
Swap: 4 GB
Disk: 100 GB
```

### 2.2 Lightweight Docker Compose for MacBook

Create `docker-env/docker-compose.macbook.yml`:

```yaml
version: "3.8"

services:
  # MySQL - Lightweight Configuration
  mysql:
    image: mysql:8.0
    container_name: mysql-dev
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: 123
      MYSQL_DATABASE: ecommerce
      TZ: Asia/Shanghai
    volumes:
      - ./mysql/conf/my-macbook.cnf:/etc/mysql/conf.d/my.cnf
      - ./mysql/data:/var/lib/mysql
      - ./mysql/init:/docker-entrypoint-initdb.d
    command: --default-authentication-plugin=mysql_native_password
    networks:
      - ecommerce-net
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          memory: 1G

  # Redis - Single Instance
  redis:
    image: redis:7.0-alpine
    container_name: redis-dev
    ports:
      - "6379:6379"
    environment:
      TZ: Asia/Shanghai
    volumes:
      - ./redis/conf/redis-macbook.conf:/etc/redis/redis.conf
      - ./redis/data:/data
    command: redis-server /etc/redis/redis.conf
    networks:
      - ecommerce-net
    deploy:
      resources:
        limits:
          cpus: '1'
          memory: 1G
        reservations:
          memory: 512M

  # Application - Single Instance
  app:
    build:
      context: ..
      dockerfile: docker-env/Dockerfile
    container_name: ecommerce-app-dev
    ports:
      - "8081:8081"
    environment:
      SPRING_PROFILES_ACTIVE: local
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/ecommerce
      SPRING_REDIS_HOST: redis
      SPRING_REDIS_PORT: 6379
      # JVM Settings for MacBook
      JAVA_OPTS: >-
        -Xms512m
        -Xmx2g
        -XX:+UseG1GC
        -XX:MaxGCPauseMillis=200
        -XX:+HeapDumpOnOutOfMemoryError
        -XX:HeapDumpPath=/tmp/heapdump.hprof
    volumes:
      - ../target/hm-dianping-0.0.1-SNAPSHOT.jar:/app/app.jar
    networks:
      - ecommerce-net
    depends_on:
      - mysql
      - redis
    deploy:
      resources:
        limits:
          cpus: '3'
          memory: 3G
        reservations:
          memory: 1G

  # Nginx - Optional (for frontend)
  nginx:
    image: nginx:alpine
    container_name: nginx-dev
    ports:
      - "8080:8080"
    volumes:
      - ./nginx/nginx.macbook.conf:/etc/nginx/nginx.conf
      - ./nginx/html:/usr/share/nginx/html
    networks:
      - ecommerce-net
    depends_on:
      - app
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 256M

  # Redis Commander - GUI for Redis
  redis-commander:
    image: rediscommander/redis-commander:latest
    container_name: redis-commander
    ports:
      - "8082:8081"
    environment:
      REDIS_HOSTS: local:redis:6379
    networks:
      - ecommerce-net
    depends_on:
      - redis
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 256M

networks:
  ecommerce-net:
    driver: bridge
```

### 2.3 MySQL Configuration for MacBook

Create `docker-env/mysql/conf/my-macbook.cnf`:

```ini
[mysqld]
# Character set
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci

# Performance - Optimized for MacBook
max_connections=100                    # Reduced from 500
innodb_buffer_pool_size=512M          # Reduced from 12G
innodb_log_file_size=256M             # Reduced from 2G
innodb_flush_log_at_trx_commit=2      # Less durable, better performance
innodb_flush_method=O_DIRECT

# Query Cache (disabled for MySQL 8.0)
query_cache_type=0
query_cache_size=0

# Logging (minimal for development)
slow_query_log=1
slow_query_log_file=/var/log/mysql/slow-query.log
long_query_time=1

# Temp tables
tmp_table_size=64M
max_heap_table_size=64M

# Thread settings
thread_cache_size=16
table_open_cache=400

[client]
default-character-set=utf8mb4
```

### 2.4 Redis Configuration for MacBook

Create `docker-env/redis/conf/redis-macbook.conf`:

```conf
# Network
bind 0.0.0.0
port 6379
protected-mode no

# Memory - Optimized for MacBook
maxmemory 512mb
maxmemory-policy allkeys-lru

# Persistence - Relaxed for development
save 900 1
save 300 10
save 60 10000
appendonly yes
appendfsync everysec

# Performance
tcp-backlog 511
timeout 0
tcp-keepalive 300

# Logging
loglevel notice
logfile ""

# Database
databases 16
```

### 2.5 Start MacBook Environment

```bash
# Navigate to docker-env directory
cd /Users/paulyang/AI/ClaudeProject/High-Concurrency_E-Commerce_Caching_System/docker-env

# Start services
docker-compose -f docker-compose.macbook.yml up -d

# Check status
docker-compose -f docker-compose.macbook.yml ps

# View logs
docker-compose -f docker-compose.macbook.yml logs -f app

# Initialize Redis Stream
docker exec -it redis-dev redis-cli XGROUP CREATE stream.orders g1 0 MKSTREAM

# Access Redis Commander GUI
open http://localhost:8082
```

### 2.6 Verify Setup

```bash
# Check application health
curl http://localhost:8081/actuator/health

# Check Redis
docker exec -it redis-dev redis-cli ping
# Expected: PONG

# Check MySQL
docker exec -it mysql-dev mysql -uroot -p123 -e "SELECT VERSION();"

# Check resource usage
docker stats --no-stream
```

---

## 3. Scaled-Down Test Scenarios

### 3.1 MacBook Test Scope

| Feature | Production Target | MacBook Target | Purpose |
|---------|------------------|----------------|---------|
| **Seckill** | 10,000 users | 100-500 users | Verify logic, find bottlenecks |
| **Cache Queries** | 50,000 QPS | 1,000-5,000 QPS | Test cache strategies |
| **User Login** | 5,000 TPS | 100-500 TPS | Verify auth flow |
| **GEO Queries** | 10,000 QPS | 500-2,000 QPS | Test geospatial logic |

### 3.2 Test Scenario 1: Seckill Functionality Test

**Objective:** Verify seckill logic works correctly under moderate load

**Configuration:**
```yaml
Thread Group:
  - Threads: 500
  - Ramp-Up: 10 seconds
  - Duration: 60 seconds
  - Voucher ID: 1 (100 inventory)
```

**JMeter Test Plan:** `tests/macbook/seckill_500_users.jmx`

**Expected Results (MacBook):**
- Success Rate: > 98%
- P99 Response Time: < 500ms (acceptable on MacBook)
- Inventory Accuracy: 100%
- Duplicate Orders: 0

**Execute:**
```bash
jmeter -n -t tests/macbook/seckill_500_users.jmx \
  -l results/macbook_seckill_500.jtl \
  -e -o reports/macbook_seckill_500

open reports/macbook_seckill_500/index.html
```

### 3.3 Test Scenario 2: Cache Performance Test

**Objective:** Validate cache hit rate and response time

**Configuration:**
```yaml
Thread Group:
  - Threads: 100
  - Ramp-Up: 5 seconds
  - Duration: 300 seconds
  - Shop Query Pattern: 80% hot (1-100), 20% cold (1-10000)
```

**Expected Results:**
- Cache Hit Rate: > 90%
- P99 Response Time (Cache Hit): < 20ms
- P99 Response Time (Cache Miss): < 200ms

### 3.4 Test Scenario 3: Resource Profiling

**Objective:** Identify memory leaks and GC issues

**Configuration:**
```yaml
Thread Group:
  - Threads: 200
  - Duration: 30 minutes
  - Mixed workload (shop queries + seckill)
```

**Monitor:**
- JVM heap usage trend
- GC frequency and duration
- Thread count
- Database connection pool

### 3.5 Test Scenario 4: Distributed Lock Verification

**Objective:** Ensure distributed locks prevent duplicate orders

**Configuration:**
```yaml
Thread Group:
  - Threads: 100
  - Simultaneous execution (no ramp-up)
  - Same voucher, same user tokens
  - Repeat: 10 times
```

**Expected Results:**
- Each user gets exactly 1 order (no duplicates)
- Lock acquisition success rate: > 99%
- No deadlocks detected

---

## 4. Testing Tools for MacBook

### 4.1 Lightweight Load Testing

#### Option 1: Apache JMeter (Recommended)

**Install:**
```bash
brew install jmeter
```

**MacBook-Optimized Settings:**

Edit `~/apache-jmeter-5.x/bin/jmeter` (or jmeter.sh):
```bash
# Heap settings for MacBook
HEAP="-Xms512m -Xmx2g"
```

**Run in GUI Mode (for test development):**
```bash
jmeter
```

**Run in CLI Mode (for actual testing):**
```bash
jmeter -n -t tests/macbook/test_plan.jmx -l results/results.jtl
```

#### Option 2: wrk (Ultra-Lightweight)

**Install:**
```bash
brew install wrk
```

**Quick Seckill Test:**
```bash
# 100 connections, 10 threads, 30 seconds
wrk -t10 -c100 -d30s \
  -H "authorization: your-token-here" \
  --latency \
  http://localhost:8081/voucher-order/seckill/1
```

**Sample wrk Lua Script** (`tests/macbook/seckill.lua`):
```lua
-- Load user tokens
tokens = {}
local file = io.open("test_data/tokens.txt", "r")
for line in file:lines() do
    table.insert(tokens, line)
end
file:close()

-- Counter for token rotation
counter = 0

request = function()
    counter = counter + 1
    local token = tokens[(counter % #tokens) + 1]

    return wrk.format("POST", "/voucher-order/seckill/1",
        {["authorization"] = token},
        nil)
end

response = function(status, headers, body)
    if status ~= 200 then
        print("Error: " .. status .. " - " .. body)
    end
end
```

**Execute:**
```bash
wrk -t4 -c100 -d30s \
  -s tests/macbook/seckill.lua \
  --latency \
  http://localhost:8081
```

#### Option 3: Gatling (For Beautiful Reports)

**Install:**
```bash
brew install gatling
```

**Lighter than JMeter, better reports**

### 4.2 Monitoring Tools

#### Option 1: Activity Monitor (Built-in)

```bash
# Open Activity Monitor
open -a "Activity Monitor"

# Filter for Docker processes
# Watch: Docker, mysql-dev, redis-dev, ecommerce-app-dev
```

#### Option 2: docker stats (CLI)

```bash
# Real-time resource usage
docker stats

# Watch specific containers
docker stats mysql-dev redis-dev ecommerce-app-dev
```

#### Option 3: VisualVM (JVM Profiling)

**Install:**
```bash
brew install --cask visualvm
```

**Connect to Docker Application:**
```bash
# 1. Enable JMX in docker-compose.macbook.yml
# Add to app service environment:
JAVA_OPTS: >-
  -Dcom.sun.management.jmxremote
  -Dcom.sun.management.jmxremote.port=9010
  -Dcom.sun.management.jmxremote.rmi.port=9010
  -Dcom.sun.management.jmxremote.authenticate=false
  -Dcom.sun.management.jmxremote.ssl=false
  -Djava.rmi.server.hostname=localhost

# Add port mapping:
ports:
  - "8081:8081"
  - "9010:9010"

# 2. Restart container
docker-compose -f docker-compose.macbook.yml restart app

# 3. Open VisualVM and connect to localhost:9010
visualvm
```

#### Option 4: Redis CLI Monitor

```bash
# Monitor all Redis commands in real-time
docker exec -it redis-dev redis-cli MONITOR

# Check memory usage
docker exec -it redis-dev redis-cli INFO memory

# Check hit rate
docker exec -it redis-dev redis-cli INFO stats | grep keyspace
```

#### Option 5: MySQL Performance

```bash
# Show current queries
docker exec -it mysql-dev mysql -uroot -p123 \
  -e "SHOW PROCESSLIST;"

# Check slow queries
docker exec -it mysql-dev mysql -uroot -p123 \
  -e "SELECT * FROM mysql.slow_log LIMIT 10;"

# Monitor InnoDB status
docker exec -it mysql-dev mysql -uroot -p123 \
  -e "SHOW ENGINE INNODB STATUS\G"
```

### 4.3 Lightweight Prometheus + Grafana (Optional)

**Only if you have 16GB+ RAM:**

Add to `docker-compose.macbook.yml`:
```yaml
  prometheus:
    image: prom/prometheus:v2.47.0
    container_name: prometheus-dev
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus-macbook.yml:/etc/prometheus/prometheus.yml
    networks:
      - ecommerce-net
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 512M

  grafana:
    image: grafana/grafana:10.1.0
    container_name: grafana-dev
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    networks:
      - ecommerce-net
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 512M
```

---

## 5. Resource Monitoring

### 5.1 Pre-Test System Check

**Before running tests, ensure your MacBook has available resources:**

```bash
# Check CPU load (should be < 2.0 on 8-core Mac)
uptime

# Check available memory
vm_stat | perl -ne '/page size of (\d+)/ and $size=$1; /Pages\s+([^:]+)[^\d]+(\d+)/ and printf("%-16s % 16.2f Mi\n", "$1:", $2 * $size / 1048576);'

# Check disk space (need at least 20GB free)
df -h

# Check Docker resource usage
docker system df
```

**If Resources are Low:**
```bash
# Clean up Docker
docker system prune -a --volumes

# Stop unnecessary applications
# Close Chrome, Slack, etc.

# Restart Docker Desktop
```

### 5.2 During-Test Monitoring

**Terminal 1: Application Logs**
```bash
docker-compose -f docker-compose.macbook.yml logs -f app
```

**Terminal 2: Resource Monitoring**
```bash
watch -n 1 docker stats --no-stream
```

**Terminal 3: Redis Monitoring**
```bash
docker exec -it redis-dev redis-cli MONITOR
```

**Terminal 4: Test Execution**
```bash
jmeter -n -t tests/macbook/test_plan.jmx -l results/results.jtl
```

### 5.3 Warning Signs to Stop Test

⚠️ **Stop the test immediately if:**
- CPU usage > 95% for more than 30 seconds
- Memory pressure (swap usage > 2GB)
- MacBook becomes unresponsive
- Disk I/O wait > 50%
- Docker containers restarting unexpectedly

```bash
# Gracefully stop test
# Press Ctrl+C in JMeter terminal

# If system is frozen, force stop Docker
docker-compose -f docker-compose.macbook.yml down

# Or force quit Docker Desktop
killall Docker
```

---

## 6. Interpreting Results

### 6.1 MacBook vs Production Expectations

**Response Time Adjustment:**
```
MacBook P99 = Production P99 × 1.5 to 2.5

Example:
- Production Target: 200ms
- MacBook Acceptable: 300-500ms
```

**Throughput Adjustment:**
```
MacBook TPS = Production TPS × 0.05 to 0.1

Example:
- Production Target: 10,000 TPS
- MacBook Acceptable: 500-1,000 TPS
```

### 6.2 What Good Results Look Like (MacBook)

| Metric | Good | Acceptable | Poor |
|--------|------|------------|------|
| **Seckill P99 (500 users)** | < 300ms | < 500ms | > 1000ms |
| **Cache Hit Rate** | > 95% | > 90% | < 85% |
| **Error Rate** | < 1% | < 5% | > 10% |
| **Inventory Accuracy** | 100% | 100% | < 100% |
| **CPU Usage** | < 70% | < 85% | > 90% |
| **Memory Usage** | < 80% | < 90% | > 95% |

### 6.3 Finding Bottlenecks

**If Response Times are Slow:**

1. **Check Database Queries:**
```bash
# Find slow queries
docker exec -it mysql-dev mysql -uroot -p123 ecommerce \
  -e "SELECT * FROM performance_schema.events_statements_summary_by_digest
      ORDER BY SUM_TIMER_WAIT DESC LIMIT 10\G"
```

2. **Check Redis Latency:**
```bash
docker exec -it redis-dev redis-cli --latency
# Good: < 1ms
# Acceptable: < 5ms
# Poor: > 10ms
```

3. **Profile JVM:**
- Use VisualVM to identify:
  - Memory leaks (increasing heap over time)
  - Excessive GC (should be < 5% of time)
  - Thread contention
  - Hot spots in code

**If Tests Fail (Errors):**

1. **Check Application Logs:**
```bash
docker-compose -f docker-compose.macbook.yml logs app | grep ERROR
```

2. **Check for Deadlocks:**
```bash
# MySQL
docker exec -it mysql-dev mysql -uroot -p123 \
  -e "SHOW ENGINE INNODB STATUS\G" | grep -A 20 "LATEST DETECTED DEADLOCK"

# Redis (check for lock timeouts)
docker exec -it redis-dev redis-cli KEYS "lock:*"
```

3. **Verify Distributed Lock Logic:**
```bash
# During test, watch lock operations
docker exec -it redis-dev redis-cli MONITOR | grep "lock:"
```

---

## 7. When to Use Cloud Testing

### 7.1 MacBook is Sufficient For:

✅ **Development Phase:**
- Feature verification
- Unit/integration testing
- Code-level profiling
- Cache strategy validation
- Basic load testing (< 500 users)

### 7.2 Cloud Testing is Required For:

❌ **Production Validation:**
- High concurrency testing (> 1,000 users)
- Load balancer testing
- Redis cluster failover
- Multi-region latency
- Endurance testing (> 4 hours)
- Actual production capacity validation

### 7.3 Cloud Testing Options

#### Option 1: AWS Free Tier (12 months free)

**Resources:**
- EC2 t2.micro (1 vCPU, 1GB RAM) - Application
- RDS t2.micro (MySQL) - Database
- ElastiCache (Redis) - Cache

**Estimated Cost:** $0 (within free tier limits)

**Setup:**
```bash
# Use Terraform or CloudFormation
# Deploy docker-compose.prod.yml to EC2
```

#### Option 2: Digital Ocean (Simple, Affordable)

**Droplet Configuration:**
- $12/month: 2 vCPU, 2GB RAM
- $24/month: 2 vCPU, 4GB RAM (recommended)

**Managed Services:**
- Managed MySQL: $15/month
- Managed Redis: $15/month

**Total:** ~$54/month for realistic testing

#### Option 3: Render.com / Railway.app (Hobby Projects)

**Free tier available for small-scale testing**

### 7.4 When to Graduate from MacBook

**Indicators you need cloud testing:**

1. **MacBook Performance:**
   - Tests take > 5 minutes to complete
   - MacBook becomes unusable during tests
   - Docker containers crash from resource exhaustion

2. **Test Requirements:**
   - Need to test > 1,000 concurrent users
   - Need to run tests overnight
   - Need to test Redis cluster behavior
   - Need to validate actual production capacity

3. **Business Needs:**
   - Preparing for production launch
   - Need commercial-grade performance reports
   - Stakeholders require capacity guarantees

---

## 8. Quick Start Guide

### 8.1 First-Time Setup (15 minutes)

```bash
# Step 1: Navigate to project
cd /Users/paulyang/AI/ClaudeProject/High-Concurrency_E-Commerce_Caching_System

# Step 2: Build application
mvn clean package -DskipTests

# Step 3: Start MacBook environment
cd docker-env
docker-compose -f docker-compose.macbook.yml up -d

# Step 4: Wait for services (2-3 minutes)
sleep 120

# Step 5: Initialize Redis
docker exec -it redis-dev redis-cli XGROUP CREATE stream.orders g1 0 MKSTREAM

# Step 6: Verify setup
curl http://localhost:8081/actuator/health
```

### 8.2 Run Your First Test (5 minutes)

```bash
# Option 1: JMeter GUI (for learning)
jmeter

# Then:
# - Open: tests/macbook/seckill_100_users.jmx
# - Click green "Start" button
# - Watch results in listeners

# Option 2: JMeter CLI (for actual testing)
jmeter -n -t tests/macbook/seckill_100_users.jmx \
  -l results/first_test.jtl \
  -e -o reports/first_test

# Option 3: wrk (quickest)
wrk -t4 -c50 -d30s --latency \
  http://localhost:8081/shop/1
```

### 8.3 View Results

```bash
# JMeter HTML Report
open reports/first_test/index.html

# Docker Stats
docker stats --no-stream

# Application Logs
docker-compose -f docker-compose.macbook.yml logs app | tail -100
```

---

## 9. Troubleshooting

### 9.1 Common Issues

**Issue: Docker containers won't start**
```bash
# Check Docker Desktop is running
docker ps

# Increase Docker resources (Settings → Resources)
# Restart Docker Desktop
```

**Issue: Out of memory during test**
```bash
# Reduce test load
# In JMeter: Lower thread count from 500 to 100

# Or reduce Docker memory allocation
# Then restart services
docker-compose -f docker-compose.macbook.yml restart
```

**Issue: Tests are very slow**
```bash
# Check CPU usage
top -l 1 | grep "CPU usage"

# If > 90%, reduce concurrent threads
# If < 50%, problem might be database/redis config

# Check for disk I/O (SSD vs HDD)
iostat -d 1 5
```

**Issue: Redis connection errors**
```bash
# Check Redis is running
docker exec -it redis-dev redis-cli ping

# Check connection limits
docker exec -it redis-dev redis-cli CONFIG GET maxclients

# Increase if needed
docker exec -it redis-dev redis-cli CONFIG SET maxclients 1000
```

---

## 10. Summary

### ✅ What You CAN Do on MacBook:

1. **Functional Testing** - Verify all features work correctly
2. **Small-Scale Load Testing** - 100-500 concurrent users
3. **Performance Profiling** - Identify code-level bottlenecks
4. **Cache Strategy Validation** - Test hit rates, TTL logic
5. **Development Iteration** - Quick test cycles during development

### ❌ What Requires Cloud Infrastructure:

1. **High Concurrency** - 5,000+ concurrent users
2. **Load Balancing** - Multi-instance testing
3. **Redis Cluster** - Failover and sharding
4. **Endurance Testing** - Multi-hour sustained load
5. **Production Validation** - Actual capacity planning

### 🎯 Recommended Workflow:

```
MacBook (Dev) → Cloud (Staging) → Cloud (Production)
     ↓                ↓                    ↓
  Feature         Performance         Final
  Testing         Validation         Capacity
                                      Testing
```

**Use MacBook for:** Daily development, feature verification, quick iterations
**Use Cloud for:** Pre-production validation, capacity planning, commercial reports

---

**Next Steps:**
1. Run the quick start guide above
2. Execute baseline tests with 100 users
3. Monitor resource usage
4. If results are good, gradually increase to 500 users
5. When ready for production, move to cloud testing

Good luck with your performance testing! 🚀
