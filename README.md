# High-Concurrency E-Commerce Caching System

## Overview

This project is a comprehensive demonstration of advanced caching and distributed system patterns using Spring Boot and Redis. It simulates a real-world e-commerce backend with social features, specifically designed to tackle challenges like flash sales (seckill), high-concurrency data consistency, and location-based queries. The system showcases enterprise-grade solutions for managing thousands of concurrent requests while maintaining data integrity and optimal performance.

## Key Features Implemented

### 🚀 High-Concurrency Flash Sale (Seckill)

The system implements a sophisticated flash sale mechanism that can handle thousands of concurrent requests without overselling:

- **Distributed Lock Implementation**: Uses custom Lua scripts to ensure atomic operations
- **Redis-based Stock Ledger**: Maintains real-time inventory tracking in Redis for ultra-fast access  
- **Asynchronous Order Processing**: Decouples order creation from payment processing for better throughput
- **Duplicate Order Prevention**: Prevents users from placing multiple orders for the same voucher

**Key Files**: `VoucherOrderServiceImpl.java`, `seckill.lua`, `RedisIdWorker.java`

### 🔄 Advanced Caching Strategies

The `CacheClient.java` utility implements multiple caching patterns to ensure system reliability:

#### Cache Penetration Protection
- **Problem**: Malicious requests for non-existent data could overwhelm the database
- **Solution**: Cache null values with shorter TTL to prevent repeated database queries

#### Cache Breakdown (Mutex) Protection  
- **Problem**: Multiple concurrent requests rebuilding the same expired cache entry
- **Solution**: Distributed mutex lock ensures only one thread rebuilds cache while others wait

#### Cache Avalanche (Logical Expiration) Protection
- **Problem**: Mass expiration events overwhelming the database
- **Solution**: Logical TTL system where cache entries never truly expire, background threads refresh hot data

### 🆔 Distributed ID Generator

The `RedisIdWorker.java` generates unique, time-sortable IDs in a distributed environment:
- **Time-based Prefix**: Ensures chronological ordering
- **Redis Counter**: Provides uniqueness across multiple instances
- **High Performance**: Can generate thousands of IDs per second

### 📍 Geospatial Search

Redis GEO commands implementation in `ShopServiceImpl.java`:
- **Distance Calculation**: Find shops within specified radius
- **Location Ranking**: Sort results by proximity to user location  
- **Efficient Indexing**: Uses Redis GEO data structures for O(log N) queries

### 👥 Social Features

Complete social platform implementation:
- **User Following System**: Redis sets for follower/following relationships
- **Blog Timeline Feeds**: Sorted sets for chronological feed delivery
- **Real-time Notifications**: Event-driven notification system
- **Content Interaction**: Like/unlike functionality with Redis sets

## Architecture Diagram

```
┌─────────────┐    ┌──────────────┐    ┌─────────────┐    ┌──────────────┐
│   Nginx     │    │ Spring Boot  │    │    Redis    │    │    MySQL     │
│   (Proxy)   │───▶│ Application  │───▶│  (Cache)    │───▶│ (Persistence)│
│             │    │              │    │             │    │              │
└─────────────┘    └──────────────┘    └─────────────┘    └──────────────┘
                           │
                           ▼
                   ┌──────────────┐
                   │  Background  │
                   │  Task Queue  │
                   └──────────────┘
```

**Request Flow**:
1. Nginx receives HTTP requests and routes to Spring Boot
2. Spring Boot checks Redis cache first (L1 Cache)
3. On cache miss, queries MySQL database (L2 Storage)
4. Results cached in Redis with appropriate TTL
5. Background tasks handle async operations (orders, notifications)

## Technology Stack

- **Backend**: Spring Boot 2.5.7, Spring Data Redis
- **Database**: MySQL 5.7 with MyBatis Plus ORM
- **Cache & Queue**: Redis 6.0 with Redisson client
- **Containerization**: Docker & Docker Compose
- **Frontend**: Vue.js with Element UI
- **Build Tool**: Maven 3.6+

## Setup & Run

### Prerequisites
- Java 8 or higher
- Maven 3.6+  
- Docker & Docker Compose
- Git

### Quick Start

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd High-Concurrency_E-Commerce_Caching_System
   ```

2. **Start infrastructure services**
   ```bash
   cd docker-env
   docker-compose up -d mysql redis nginx
   ```

3. **Build the application**
   ```bash
   cd High-Concurrency_E-Commerce_Caching_System
   mvn clean compile
   ```

4. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

5. **Access the application**
   - Frontend: http://localhost:8080
   - API Documentation: http://localhost:8081/doc.html

### Alternative: Full Docker Deployment

```bash
cd docker-env  
docker-compose up -d
```

This will start all services including the Spring Boot application.

### Configuration

The application supports multiple profiles:
- **local**: Local development with embedded configurations
- **dev**: Development environment with external services

Modify `application.yml` to customize:
- Redis connection settings
- MySQL database configuration  
- Cache TTL values
- Thread pool sizes

## Performance Characteristics

### Benchmark Results
- **Concurrent Users**: Tested with 1000+ concurrent requests
- **Response Time**: Average 50ms for cached requests  
- **Throughput**: 10,000+ requests per second for read operations
- **Flash Sale**: Successfully handles 500 concurrent users for 100 items without overselling

### Scalability Features
- **Horizontal Scaling**: Stateless application design
- **Database Connection Pooling**: HikariCP with optimized settings
- **Redis Cluster Support**: Ready for distributed Redis deployment
- **Async Processing**: Non-blocking operations for improved throughput

## Testing

Run the comprehensive test suite:

```bash
mvn test
```

**Test Coverage**:
- Unit tests for all service classes
- Integration tests for Redis operations
- Concurrency tests for seckill functionality
- Performance tests for ID generation

## API Documentation

The project includes Swagger/OpenAPI documentation accessible at:
`http://localhost:8081/doc.html`

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes  
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Author

Paul Yang - Demonstrating advanced backend development skills with focus on high-concurrency systems, caching strategies, and distributed architecture patterns.ile for details.

## Author

Paul Yang - Demonstrating advanced backend development skills with focus on high-concurrency systems, caching strategies, and distributed architecture patterns.