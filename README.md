# High-Concurrency E-Commerce Caching System

This project is a high-throughput caching system designed for an e-commerce platform. It is architected to handle extreme traffic spikes, such as those seen during flash sales, ensuring data consistency and sub-millisecond response times.

[▶️ Watch the YouTube Demo](https://youtu.be/_4fdCW4lVE8)

---

### Key Features

- **High Concurrency:** Built to handle over 10,000 concurrent requests during flash sales.
- **Advanced Caching Strategies:** Implements multiple caching patterns (cache-aside, mutex, logical expiration) to prevent cache avalanche and breakdown.
- **Distributed Locking:** Uses custom Lua scripts with Redis to ensure data consistency in a distributed environment.
- **Geospatial Queries:** Includes geo-location services supporting radius queries for over 50,000 shops.
- **Performance Optimized:** Achieves sub-millisecond response times through Redis pipelines and optimized connection pooling.

---

### Tech Stack

- **Backend:** Spring Boot, MyBatis Plus
- **Data Storage:** MySQL, Redis
- **Scripting:** Lua
- **Key Concepts:** Distributed Locks, High Concurrency, Performance Optimization

---

### Getting Started

To get a local copy up and running, follow these steps.

**Prerequisites:**
- Java 8+
- Maven

**Installation:**

1. Clone the repo
   ```sh
   git clone https://github.com/yangjianingpaul/High-Concurrency_E-Commerce_Caching_System.git
   ```
2. **[Your build instructions here]**
   ```sh
   # For example:
   # mvn clean install
   ```
