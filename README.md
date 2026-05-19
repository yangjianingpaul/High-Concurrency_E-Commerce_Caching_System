# High-Concurrency E-Commerce Caching System

> A study project exploring Redis cache strategies and Redis-based distributed
> locking under concurrency, built on a well-known tutorial baseline and then
> hardened with a characterization-test-first engineering workflow.

🎥 **[Watch Demo on YouTube](https://youtu.be/_4fdCW4lVE8)**

[![Java](https://img.shields.io/badge/Java-8-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.5-green)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-6.x-red)](https://redis.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.x-blue)](https://www.mysql.com/)

---

## 🔎 Provenance & Portfolio Scope

**Origin (disclosed up front):** this repository started from the widely-used
**hmdp / 黑马点评 ("Dianping") tutorial** for Spring Boot + Redis. The original
business scaffolding, schema, and feature set are tutorial-derived. This is
**not** an original production system, and it is not a system the author shipped
at any employer.

**What the portfolio signal actually is — the Engineering Delta.** The value
here is not authorship of the baseline; it is the disciplined engineering work
applied *on top of* it:

- **Characterization tests** that pin real behaviour before any change
  (`CacheClientTest`, `MutexRedisLockTest`).
- **Distributed-lock failure-mode analysis** — claimed vs. characterized
  behaviour of the hand-rolled Redis lock:
  [`docs/distributed-lock-failure-modes.md`](./docs/distributed-lock-failure-modes.md).
- **Architecture Decision Records** documenting real trade-offs, e.g.
  [ADR-0001: Redisson over the hand-rolled lock](./docs/adr/0001-redis-distributed-lock-redisson-over-handrolled.md).
- **Correctness-preserving refactoring** in small, reviewable slices with the
  test suite green at each step.

Performance numbers below are **local single-machine measurements** on
tutorial-derived code, kept for transparency — they are not production capacity
claims. The author has backend engineering experience elsewhere; that belongs
in a résumé, not as this repository's provenance.

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

This project works through the classic high-concurrency caching problems —
cache avalanche, cache penetration, cache breakdown — and Redis-based
distributed locking, using a flash-sale scenario as the working example.

**Background:** the baseline is the hmdp / 黑马点评 tutorial (see *Provenance &
Portfolio Scope* above). The engineering contribution is the test-first
hardening, failure-mode analysis, and ADR-documented decisions layered on top,
not the original feature set.

**Use Case (illustrative):** a flash sale where many users contend for limited
inventory — used here to exercise cache and locking behaviour, not as a claim of
production deployment.

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

**Solution (with characterized limits):**
```java
// Custom Lua scripts ensure atomic lock acquire (SET NX EX) and
// atomic owner-checked release (get-compare-del).
```
The hand-rolled lock's *real* behaviour — atomic acquire and genuine
foreign-thread rejection, but a **fixed lease with no watchdog/renewal** and
**non-reentrancy** — is pinned by tests and analysed in
[`docs/distributed-lock-failure-modes.md`](./docs/distributed-lock-failure-modes.md).
Production locking therefore uses Redisson `RLock`
([ADR-0001](./docs/adr/0001-redis-distributed-lock-redisson-over-handrolled.md));
the hand-rolled lock is kept as a documented teaching artifact.

---

## 🏗️ System Architecture

> 📐 **Detailed Architecture Diagrams:** See [ARCHITECTURE.md](./ARCHITECTURE.md) for complete system architecture, seckill data flow, and cache strategy comparisons.

### High-Level Overview

```
┌────────────────────────────────────────────────────────────┐
│        Load generator (local JMeter, single machine)       │
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

## 📊 Local Measurements (not production capacity)

> ⚠️ These are **single-machine, local JMeter runs against tutorial-derived
> code**. They are kept for transparency and to exercise the cache/lock paths.
> They are **not** production benchmarks, sustained-capacity guarantees, or
> daily-user/cost projections — any such framing has been deliberately removed.
>
> **Environment:** one developer machine (10-core, 32GB), single Redis + MySQL,
> 3 local Spring Boot instances behind local Nginx. **Tool:** JMeter 5.6.3.
> **Seed data:** ~102k users, ~50k shops, 110 seckill vouchers.

### Cache query path (local)

| Threads | Total Requests | Observed QPS | Avg | P99 | Max | Errors |
|---------|----------------|--------------|-----|-----|-----|--------|
| 50 | 5,000 | ~1,000 | 1ms | 1ms | 45ms | 0% |
| 500 | 50,000 | ~5,000 | 1ms | 1ms | 29ms | 0% |
| 1,000 | 100,000 | ~9,900 | 2ms | 3ms | 49ms | 0% |
| 2,000 | 200,000 | ~12,000 | 71ms | 516ms | 3,093ms | 0% |

Observation: on this single machine, latency stays low to ~1,000 threads and
degrades sharply (P99 → ~516ms) at 2,000 — i.e. the local setup saturates, as
expected. No claim is made about behaviour beyond this machine.

### Flash-sale (seckill) correctness — the result that matters

| Threads | Requests | TPS | Avg | P99 | Errors | Overselling |
|---------|----------|-----|-----|-----|--------|-------------|
| 500 | 10,000 | ~1,000 | 2ms | 4ms | 0% | none |
| 1,000 | 10,000 | ~1,000 | 1ms | 4ms | 0% | none |
| 2,000 | 10,000 | ~1,000 | <1ms | 4ms | 0% | none |

The valuable signal here is **correctness, not throughput**: across 30,000
contended requests the atomic Redis Lua check produced **no overselling and no
duplicate orders**. TPS is intentionally bounded (~1,000) by the Lua-script
serialization that guarantees inventory safety. Throughput on a single dev
machine is not a portfolio claim; the verified absence of race conditions is.

> Raw run logs are kept in
> [PERFORMANCE_TEST_REPORT.md](./PERFORMANCE_TEST_REPORT.md) for transparency.
> Treat its older capacity/cost extrapolations as superseded by this section.

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

Maintained by **Paul Yang**. This repository is a learning/portfolio artifact
built on a tutorial baseline (see *Provenance & Portfolio Scope*); it is not a
record of employer work. Professional history belongs in a résumé, not here.

- 📧 yangjianing73@gmail.com
- 💼 [LinkedIn](https://www.linkedin.com/in/paul-yang-30684128a/)
- 💻 [GitHub](https://github.com/yangjianingpaul)

**Focus:** 5+ years backend engineering (Java / Spring), currently practising a
characterization-test-first, ADR-documented AI-assisted refactoring workflow —
this repo is one worked example of that.

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
