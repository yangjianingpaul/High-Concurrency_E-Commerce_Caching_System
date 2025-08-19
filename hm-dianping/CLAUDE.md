# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 2.5.7 application for a restaurant review platform called "hm-dianping" (similar to Dianping/Yelp). The application demonstrates Redis caching patterns, distributed systems concepts, and high-concurrency handling.

### Key Technologies
- **Java 8** with Spring Boot 2.5.7
- **MyBatis-Plus 3.4.3** for database access 
- **Redis 6.x** with Lettuce client for caching and distributed features
- **Redisson 3.13.6** for distributed locks
- **MySQL 5.1.47** as the primary database
- **Hutool 5.7.17** utility library

## Build and Development Commands

### Maven Commands
```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package application
mvn package

# Run application
mvn spring-boot:run

# Skip tests during packaging
mvn package -DskipTests
```

### Running the Application
The application runs on port 8081 by default (configured in application-dev.yaml).

## Architecture Overview

### Core Components

**Authentication & Security**
- JWT-based authentication using Redis for token storage
- Two-tier interceptor system:
  - `RefreshTokenInterceptor` (order 0): Refreshes user tokens
  - `LoginInterceptor` (order 1): Validates authentication for protected endpoints
- `UserHolder` utility manages thread-local user context

**Caching Strategy**
The application implements multiple Redis caching patterns:
- **Cache-aside pattern** with null value caching to prevent cache penetration
- **Logical expiration** to handle cache breakdown for hot keys
- **Mutex locks** for cache reconstruction
- **Cache utility class** (`CacheClient`) provides reusable caching operations

**Distributed Systems Features**
- **Distributed ID generation** using Redis counters (RedisIdWorker)
- **Distributed locking** with Redisson for preventing duplicate orders
- **Asynchronous processing** using Redis Streams for seckill (flash sale) orders
- **Geospatial queries** for location-based shop searches
- **BitMap operations** for user check-in statistics

### Service Layer Patterns

**Key Services:**
- `UserService`: Handles authentication, user management, and check-in features
- `ShopService`: Manages shop data with sophisticated caching strategies
- `VoucherOrderService`: Implements high-concurrency seckill system

**Seckill (Flash Sale) Architecture:**
1. Lua scripts execute atomic operations in Redis for inventory checks
2. Redis Streams queue successful orders for asynchronous processing
3. Background thread pool processes orders to avoid blocking user requests
4. Redisson distributed locks prevent duplicate orders per user

### Database Layer
- MyBatis-Plus provides CRUD operations with query wrappers
- Entity classes in `com.hmdp.entity` package
- Mapper interfaces in `com.hmdp.mapper` package
- Database schema available in `src/main/resources/db/hmdp.sql`

### Redis Integration Patterns
- **String operations**: Caching shop data, user sessions, verification codes
- **Hash operations**: Storing user session data
- **Set operations**: Managing seckill order deduplication  
- **Sorted Set operations**: Blog feeds and ranking features
- **Geo operations**: Location-based shop queries
- **Stream operations**: Asynchronous order processing
- **BitMap operations**: User daily check-in statistics

### Configuration
- Main config in `application-dev.yaml` with database and Redis connections
- Redis configuration includes connection pooling with Lettuce
- Custom RedisTemplate setup for JSON serialization
- MyBatis-Plus configured with entity package scanning

## Code Patterns and Conventions

### Error Handling
- Custom `Result` class wraps all API responses with success/failure status
- Global exception handler in `WebExceptionAdvice`
- Consistent error messages and status codes

### Constants Management
- `RedisConstants`: All Redis key prefixes and TTL values
- `SystemConstants`: Application-wide constants
- Centralized constant management for maintainability

### Utility Classes
- `CacheClient`: Reusable caching operations with penetration/breakdown protection
- `RedisIdWorker`: Distributed unique ID generation
- `UserHolder`: Thread-local user context management
- `RegexUtils`: Input validation utilities

### Lua Scripts
- `seckill.lua`: Atomic seckill operations (inventory check + order placement)
- `unlock.lua`: Safe distributed lock release
- Scripts ensure atomicity for complex Redis operations

## Development Notes

### Redis Setup Requirements
Before running the application, ensure Redis is configured with:
```bash
# Create Redis Stream consumer group for order processing
XGROUP CREATE stream.orders g1 0 MKSTREAM
```

### Testing Patterns
- Unit tests should mock Redis operations using StringRedisTemplate
- Integration tests require running Redis instance
- Use `@SpringBootTest` for full application context testing

### Common Debugging Areas
- Redis connection issues: Check host/port in application-dev.yaml
- Cache inconsistency: Verify TTL settings and cache eviction logic
- Concurrency issues: Review distributed lock implementation
- Seckill performance: Monitor Redis Stream processing and thread pool usage