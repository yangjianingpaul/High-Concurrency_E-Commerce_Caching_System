package com.paulyang.ecommerce;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * High-Concurrency E-Commerce System Spring Boot Application.
 * This application demonstrates advanced caching strategies, distributed systems patterns,
 * and high-concurrency flash sale (seckill) mechanisms using Spring Boot and Redis.
 * 
 * <p>Key Features:</p>
 * <ul>
 *   <li>Redis-based caching with penetration/breakdown/avalanche protection</li>
 *   <li>Distributed locking and globally unique ID generation</li>
 *   <li>High-concurrency seckill system with Redis Streams</li>
 *   <li>Geolocation-based services with Redis GEO commands</li>
 *   <li>Social features with Redis sorted sets and bitmaps</li>
 * </ul>
 * 
 * @author Paul Yang
 * @since 1.0
 */
@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.paulyang.ecommerce.mapper")
@SpringBootApplication
public class ECommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ECommerceApplication.class, args);
    }

}
