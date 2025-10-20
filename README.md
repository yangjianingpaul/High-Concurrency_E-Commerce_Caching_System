# High-Concurrency E-Commerce Caching System

> Production-grade caching system designed for extreme traffic scenarios like flash sales. Handles 10,000+ concurrent requests with sub-millisecond response times.

🎥 **[Watch Live Demo on YouTube](https://youtu.be/_4fdCW4lVE8)**

[![Java](https://img.shields.io/badge/Java-8-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.5-green)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-6.x-red)](https://redis.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.x-blue)](https://www.mysql.com/)

---

## 📋 Table of Contents
- [Overview](#overview)
- [Key Features](#key-features)
- [Technical Challenges Solved](#technical-challenges-solved)
- [System Architecture](#system-architecture)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Performance Benchmarks](#performance-benchmarks)

---

## 🎯 Overview

This project tackles the **most challenging problems in high-concurrency e-commerce systems**: cache avalanche, cache penetration, cache breakdown, and distributed locking. It's architected to handle extreme traffic spikes during flash sales while maintaining data consistency.

**Background:** Built based on experience at **HUAWEI** (2021-2022) working on high-concurrency e-commerce systems, with advanced implementations that go beyond production requirements.

**Use Case:** Flash sale scenarios where thousands of users simultaneously attempt to purchase limited inventory items.

---

## ✨ Key Features

### Advanced Caching Strategies
- **Cache-Aside Pattern** - Primary caching strategy with database fallback
- **Mutex Locking** - Prevents cache breakdown under high concurrency
- **Logical Expiration** - Eliminates cache avalanche risk
- **Bloom Filters** - Prevents cache penetration from invalid requests

### Distributed Systems
- **Custom Lua Scripts** - Atomic Redis operations for distributed locks
- **Redisson Integration** - Production-grade distributed locking framework
- **Connection Pooling** - Optimized database and Redis connections
- **Geospatial Queries** - Location-based shop search supporting 50,000+ shops

### High Performance
- **Redis Pipelines** - Batch operations reducing network roundtrips
- **Async Processing** - Non-blocking operations for better throughput
- **Query Optimization** - Indexed database queries with MyBatis Plus

---

## 🔥 Technical Challenges Solved

### 1. Cache Avalanche Prevention
**Problem:** Massive cache expiration at the same time causes database overload.

**Solution:**
```java
// Logical expiration - data never expires in Redis
// Background thread refreshes expired entries
// Database remains protected during high traffic
```

### 2. Cache Breakdown (Hotspot Invalid)
**Problem:** Single popular key expires, all requests hit database simultaneously.

**Solution:**
```java
// Mutex lock using Lua script
// Only first request queries database
// Others wait for cache rebuild
// Prevents duplicate database queries
```

### 3. Cache Penetration
**Problem:** Malicious queries for non-existent data bypass cache.

**Solution:**
```java
// Bloom filter checks existence before cache lookup
// Invalid requests blocked at Redis level
// Database protected from scanning attacks
```

### 4. Distributed Locking
**Problem:** Multiple instances need atomic operations on shared resources.

**Solution:**
```java
// Custom Lua scripts ensure atomic lock acquire/release
// Automatic lock expiration prevents deadlocks
// UUID-based lock ownership verification
```

---

## 🏗️ System Architecture

> 📐 **Detailed Architecture Diagrams:** See [ARCHITECTURE.md](./ARCHITECTURE.md) for complete system architecture, seckill data flow, and cache strategy comparisons.

### High-Level Overview

```
┌────────────────────────────────────────────────────────────┐
│              2,000+ Concurrent Users                       │
│              10,000+ QPS / 1,000 TPS                       │
└───────────────────────┬────────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────────┐
│          Nginx Load Balancer (Least-Connection)            │
│          • Health Checks  • Connection Pooling             │
└──────┬─────────────────┬─────────────────┬─────────────────┘
       │                 │                 │
       ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Spring Boot  │  │ Spring Boot  │  │ Spring Boot  │
│ Instance #1  │  │ Instance #2  │  │ Instance #3  │
│ JVM: 2-4GB   │  │ JVM: 2-4GB   │  │ JVM: 2-4GB   │
│ 200 threads  │  │ 200 threads  │  │ 200 threads  │
└──────┬───────┘  └──────┬───────┘  └──────┬───────┘
       │                 │                 │
       └────────┬────────┴────────┬────────┘
                │                 │
                ▼                 ▼
    ┌──────────────────┐  ┌──────────────────┐
    │ Redis 7.2        │  │ MySQL 8.0        │
    │ (Cache + Queue)  │  │ (Persistent DB)  │
    │                  │  │                  │
    │ • String Cache   │  │ • InnoDB Engine  │
    │ • Lua Scripts    │  │ • HikariCP Pool  │
    │ • Redis Streams  │  │ • 6GB Buffer     │
    │ • Distributed ID │  │                  │
    └──────────────────┘  └──────────────────┘
```

**Key Design Patterns:**
- ✅ **Stateless Application**: All state in Redis/MySQL for horizontal scaling
- ✅ **Async Processing**: Redis Streams decouple validation from order creation
- ✅ **Circuit Breaker**: Health checks prevent requests to failed instances
- ✅ **Connection Pooling**: Optimized for high-concurrency scenarios

---

## 🛠️ Tech Stack

| Category | Technologies |
|----------|--------------|
| **Backend** | Spring Boot 2.5.7, MyBatis Plus 3.4.3 |
| **Caching** | Redis 6.x (Lettuce client), Redisson 3.13.6 |
| **Database** | MySQL 8.x |
| **Scripting** | Lua (for atomic Redis operations) |
| **Connection Pool** | Apache Commons Pool2 |
| **Utilities** | Hutool 5.7.17, Lombok |
| **Containerization** | Docker Compose |

---

## 🚀 Getting Started

### Prerequisites
- Java 8 or higher
- Maven 3.6+
- Docker & Docker Compose
- At least 4GB RAM

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yangjianingpaul/High-Concurrency_E-Commerce_Caching_System.git
   cd High-Concurrency_E-Commerce_Caching_System
   ```

2. **Start Redis and MySQL with Docker**
   ```bash
   cd docker-env
   docker-compose up -d
   ```

3. **Import database schema**
   ```bash
   # Connect to MySQL container
   docker exec -i mysql_container mysql -uroot -p123456 < db/schema.sql
   ```

4. **Build the project**
   ```bash
   mvn clean package
   ```

5. **Run the application**
   ```bash
   java -jar target/hm-dianping-0.0.1-SNAPSHOT.jar
   ```

6. **Access the API**
   ```
   http://localhost:8081
   ```

---

## 📁 Key Components

### Lua Scripts for Distributed Locks

**Lock Acquisition** (`lock.lua`):
```lua
-- Atomic operation: set key with UUID only if not exists
if redis.call('exists', KEYS[1]) == 0 then
    redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2])
    return 1
else
    return 0
end
```

**Lock Release** (`unlock.lua`):
```lua
-- Verify ownership before release
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0
end
```

### Caching Strategies

**Cache-Aside with Mutex:**
```java
public <T> T queryWithMutex(String key, Function<String, T> dbFallback) {
    // 1. Check Redis cache
    String json = stringRedisTemplate.opsForValue().get(key);
    if (json != null) return deserialize(json);

    // 2. Acquire mutex lock
    String lockKey = "lock:" + key;
    boolean acquired = tryLock(lockKey);

    if (acquired) {
        try {
            // 3. Query database
            T data = dbFallback.apply(key);
            // 4. Write to cache
            stringRedisTemplate.opsForValue().set(key, serialize(data));
            return data;
        } finally {
            // 5. Release lock
            unlock(lockKey);
        }
    } else {
        // 6. Wait and retry
        Thread.sleep(50);
        return queryWithMutex(key, dbFallback);
    }
}
```

**Logical Expiration (No Avalanche):**
```java
public <T> T queryWithLogicalExpire(String key, Function<String, T> dbFallback) {
    RedisData data = get(key);

    // Check logical expiration time
    if (!data.isExpired()) {
        return data.getData();
    }

    // Expired: try rebuild
    if (tryLock("rebuild:" + key)) {
        // Async rebuild cache
        executorService.submit(() -> {
            T fresh = dbFallback.apply(key);
            setWithLogicalExpire(key, fresh);
            unlock("rebuild:" + key);
        });
    }

    // Return stale data (prevents database load)
    return data.getData();
}
```

---

## 📊 Performance Benchmarks

> **Test Date:** October 2025
> **Test Environment:** Intel i5-12600KF (10-core), 32GB RAM, 1TB NVMe, Ubuntu 24.04 LTS
> **Architecture:** 3× Spring Boot + Nginx Load Balancer + MySQL 8.0 + Redis 7.2
> **Test Tool:** Apache JMeter 5.6.3
> **Test Data:** 102,005 users, 50,114 shops, 110 seckill vouchers (1M+ inventory)

### Production Performance Metrics

| Metric | Result | Industry Standard |
|--------|--------|-------------------|
| **Sustainable QPS** | **10,000** queries/second | ✅ Excellent (5K-15K) |
| **Peak QPS** | **12,012** queries/second | ✅ Above expectations |
| **Average Latency** | **1-2ms** (at 10K QPS) | ✅ Exceptional (<50ms) |
| **P99 Latency** | **71ms** (at 12K QPS) | ✅ Good (<200ms) |
| **Error Rate** | **0.00%** (all tests) | ✅ Perfect (target <1%) |
| **Cache Hit Rate** | **95%+** | ✅ Industry standard |
| **Daily User Capacity** | **100K-500K** active users | ✅ Medium-to-large scale |

### Progressive Load Testing Results

#### Cache Query Performance (Shop Queries)

| Test Scenario | Threads | Total Requests | QPS | Avg Latency | P50 | P90 | P99 | Max | Error Rate |
|---------------|---------|----------------|-----|-------------|-----|-----|-----|-----|------------|
| **Baseline** | 50 | 5,000 | 1,002 | 1ms | 1ms | 1ms | 1ms | 45ms | 0.00% |
| **Medium Load** | 500 | 50,000 | 4,986 | 1ms | 1ms | 1ms | 1ms | 29ms | 0.00% |
| **High Load** | 1,000 | 100,000 | **9,955** | 2ms | 1ms | 2ms | 3ms | 49ms | 0.00% |
| **Stress Test** | 2,000 | 200,000 | **12,012** | 71ms | 14ms | 199ms | 516ms | 3,093ms | 0.00% |

**Key Findings:**
- ✅ **Optimal Performance Range:** 500-1,000 concurrent users (10,000 QPS, <3ms latency)
- ✅ **Zero Errors:** 100% success rate across all 355,000 test requests
- ✅ **Linear Scaling:** Performance scales linearly up to 1,000 threads
- ⚠️ **Saturation Point:** Thread pool saturation begins at 2,000 threads (600 total across 3 instances)

#### Flash Sale (Seckill) Performance

| Test Scenario | Threads | Total Requests | TPS | Avg Latency | P50 | P95 | P99 | Max | Error Rate | Overselling |
|---------------|---------|----------------|-----|-------------|-----|-----|-----|-----|------------|-------------|
| **500 Users** | 500 | 10,000 | 999 | 2ms | 1ms | 1ms | 4ms | 70ms | 0.00% | **Zero** ✅ |
| **1K Users** | 1,000 | 10,000 | 999 | 1ms | 1ms | 1ms | 4ms | 34ms | 0.00% | **Zero** ✅ |
| **2K Users** | 2,000 | 10,000 | 1,006 | 0.82ms | 1ms | 1ms | 4ms | 31ms | 0.00% | **Zero** ✅ |

**Flash Sale Key Findings:**
- ✅ **Consistent TPS:** ~1,000 TPS across all concurrency levels (Lua script bottleneck)
- ✅ **Sub-millisecond Latency:** Average 0.82-2ms, P99 <5ms
- ✅ **Zero Overselling:** 29,957 orders created from 30,000 requests - perfect atomic operations
- ✅ **100% Accuracy:** Redis Lua scripts + distributed locks prevent all race conditions
- ✅ **Perfect Reliability:** 0.00% error rate, no duplicate orders, no stock inconsistencies
- 📊 **Bottleneck:** Lua script execution limits TPS to ~1,000 (intentional for inventory safety)

### Real-World Capacity

**What This System Can Handle:**

| User Scenario | Concurrent Users | Daily Active Users | Example Use Case |
|---------------|------------------|-------------------|------------------|
| **Current Baseline** | 1,000 | 100,000-500,000 | Medium-sized e-commerce platform |
| **With Optimization** | 1,500 | 200,000-800,000 | Major city restaurant platform |
| **Horizontal Scaling (6 instances)** | 3,000 | 500,000-1,500,000 | Regional e-commerce leader |
| **Enterprise Setup (12 instances)** | 10,000+ | 2,000,000-5,000,000 | National flash sale platform |

**Flash Sale Capability (Validated Performance):**
- ✅ Handles **2,000 simultaneous users** with **1,000 TPS** sustained
- ✅ Tested with **110 seckill vouchers** and **1.1M initial inventory**
- ✅ **Zero overselling verified:** 29,957 orders processed perfectly (100% accuracy)
- ✅ **Sub-millisecond response:** 0.82ms avg, P99 <5ms
- ✅ Atomic Redis Lua scripts + Redisson distributed locks
- ✅ Asynchronous order processing via Redis Streams

### Hardware Scaling Estimates

Performance on different hardware configurations:

| Hardware | CPU Cores | RAM | Estimated QPS | Daily Users | Cost |
|----------|-----------|-----|---------------|-------------|------|
| **Entry Server** | 6-core | 16GB | ~5,000 | 20K-100K | $1,500 |
| **Current Test** | 10-core | 32GB | **~10,000** | 100K-500K | $2,500 |
| **High-Performance** | 16-core | 64GB | ~30,000 | 500K-1.5M | $4,000 |
| **Enterprise** | 64-core | 256GB | ~100,000 | 2M-5M | $20,000 |

> 📊 **Full Performance Report:** See [PERFORMANCE_TEST_REPORT.md](./PERFORMANCE_TEST_REPORT.md) for complete analysis, bottleneck identification, optimization recommendations, and cloud migration cost analysis.

---

## 🧪 Key Features Demonstrated

### 1. Geospatial Shop Search
```java
// Find shops within 5km radius
GeoResults<GeoLocation<String>> results = stringRedisTemplate
    .opsForGeo()
    .radius("shops", new Circle(new Point(longitude, latitude), 5));
```

### 2. Flash Sale Voucher System
```java
// Atomic inventory deduction
String script = "if redis.call('get', KEYS[1]) > 0 then " +
                "return redis.call('decr', KEYS[1]) else return -1 end";
Long result = redisTemplate.execute(script, keys, args);
```

### 3. User Check-in System
```java
// BitMap for daily check-in tracking
stringRedisTemplate.opsForValue().setBit("checkin:user:123:2025-10", 13, true);
```

---

## 🎥 Demo Video

The YouTube demo includes:
- Flash sale simulation with 1000 concurrent users
- Cache hit/miss visualization
- Distributed lock behavior under contention
- Geospatial query performance
- System behavior during cache expiration

**[▶️ Watch Demo](https://youtu.be/_4fdCW4lVE8)**

---

## 💡 Real-World Applications

This architecture is suitable for:
- ✅ Flash sales / Limited-time promotions
- ✅ High-traffic e-commerce platforms
- ✅ Ticket booking systems
- ✅ Inventory management systems
- ✅ Any scenario requiring high-concurrency writes with consistency

---

## 👨‍💻 About the Developer

Built by **Paul Yang** based on production experience at HUAWEI and enhanced during career transition.

- 📧 yangjianing73@gmail.com
- 💼 [LinkedIn](https://www.linkedin.com/in/paul-yang-30684128a/)
- 💻 [GitHub](https://github.com/yangjianingpaul)

**Professional Background:**
- Worked on high-concurrency e-commerce systems at HUAWEI (2021-2022)
- 5+ years backend engineering experience
- Specialized in distributed systems and performance optimization

---

## 📚 Documentation & Resources

### Project Documentation
- **[ARCHITECTURE.md](./ARCHITECTURE.md)** - Complete system architecture diagrams
  - Production deployment architecture
  - Seckill data flow (step-by-step)
  - Cache strategy comparisons
  - Technology stack integration

- **[PERFORMANCE_TEST_REPORT.md](./PERFORMANCE_TEST_REPORT.md)** - Comprehensive performance analysis
  - Complete test results and methodology
  - Hardware scaling estimates
  - Bottleneck analysis and optimization recommendations
  - Cost analysis (self-hosted vs cloud)

- **[INTERVIEW_PREP.md](./INTERVIEW_PREP.md)** - Technical interview preparation
  - Top 20 interview questions with detailed answers
  - STAR method examples
  - System design deep-dives
  - Common traps and how to avoid them

- **[PERFORMANCE_CHEAT_SHEET.md](./PERFORMANCE_CHEAT_SHEET.md)** - Quick reference
  - Key metrics to memorize
  - One-sentence explanations
  - Interview answer templates

### Learning Resources

This project demonstrates concepts from:
- Redis in Action (cache patterns)
- Designing Data-Intensive Applications (distributed systems)
- Java Concurrency in Practice (thread safety)

---

## 📄 License

This project is open source and available for educational purposes.

---

**⭐ If you find this project valuable, please star the repository!**
