package com.paulyang.ecommerce.utils;

import com.paulyang.ecommerce.entity.Shop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for CacheClient utility class
 * Tests cache penetration, breakdown, and avalanche protection mechanisms
 */
@SpringBootTest
@TestPropertySource(properties = "spring.profiles.active=local")
public class CacheClientTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    private CacheClient cacheClient;
    
    private static final String TEST_KEY_PREFIX = "test:cache:";
    
    @BeforeEach
    void setUp() {
        cacheClient = new CacheClient(stringRedisTemplate);
        
        // Clean up any existing test data
        cleanupTestData();
    }
    
    private void cleanupTestData() {
        var keys = stringRedisTemplate.keys(TEST_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
    
    @Test
    void testCachePenetrationProtection() {
        // Test cache penetration protection for non-existent data
        String testKey = TEST_KEY_PREFIX + "nonexistent";
        
        AtomicInteger dbCallCount = new AtomicInteger(0);
        Function<Long, Shop> dbFallback = id -> {
            dbCallCount.incrementAndGet();
            return null; // Simulate non-existent data
        };
        
        // First call should go to DB and cache null result
        Shop result1 = cacheClient.queryWithPassThrough(
            testKey, 1L, Shop.class, dbFallback);
        assertNull(result1, "Should return null for non-existent data");
        assertEquals(1, dbCallCount.get(), "DB should be called once");
        
        // Second call should hit cache and not call DB
        Shop result2 = cacheClient.queryWithPassThrough(
            testKey, 1L, Shop.class, dbFallback);
        assertNull(result2, "Should still return null");
        assertEquals(1, dbCallCount.get(), "DB should not be called again");
        
        System.out.println("Cache penetration protection: PASSED");
    }
    
    @Test
    void testCacheLogicalExpiration() throws InterruptedException {
        // Test logical expiration mechanism
        String testKey = TEST_KEY_PREFIX + "logical_exp_test";
        
        AtomicInteger dbCallCount = new AtomicInteger(0);
        Function<Long, Shop> dbFallback = id -> {
            dbCallCount.incrementAndGet();
            return createTestShop(id);
        };
        
        // Pre-populate cache with data that will "logically" expire quickly
        cacheClient.setWithLogicalExpire(testKey + "1", createTestShop(1L), 1L, TimeUnit.SECONDS);
        
        // Wait for logical expiration
        Thread.sleep(2000);
        
        int concurrentThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch latch = new CountDownLatch(concurrentThreads);
        
        // Multiple threads access "expired" data
        for (int i = 0; i < concurrentThreads; i++) {
            executor.submit(() -> {
                try {
                    Shop result = cacheClient.queryWithLogicalExpire(
                        testKey, 1L, Shop.class, dbFallback, 30L, TimeUnit.SECONDS);
                    assertNotNull(result, "Should always return data (old or new)");
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();
        
        System.out.println("Cache logical expiration test: PASSED");
    }
    
    @Test
    void testCacheHitPerformance() {
        // Test cache performance for hit scenarios
        String testKey = TEST_KEY_PREFIX + "performance_test";
        Shop testShop = createTestShop(1L);
        
        // Pre-populate cache
        cacheClient.set(testKey + "1", testShop, 30L, TimeUnit.SECONDS);
        
        int iterations = 100;
        long startTime = System.currentTimeMillis();
        
        AtomicInteger dbCallCount = new AtomicInteger(0);
        Function<Long, Shop> dbFallback = id -> {
            dbCallCount.incrementAndGet();
            return createTestShop(id);
        };
        
        for (int i = 0; i < iterations; i++) {
            Shop result = cacheClient.queryWithPassThrough(
                testKey, 1L, Shop.class, dbFallback);
            assertNotNull(result, "Cache hit should return data");
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        double avgResponseTime = (double) duration / iterations;
        
        assertEquals(0, dbCallCount.get(), "No DB calls should be made for cache hits");
        assertTrue(avgResponseTime < 5.0, 
            String.format("Cache hit should be fast: %.2f ms avg", avgResponseTime));
        
        System.out.printf("Cache performance test: %d hits in %d ms (%.2f ms avg)%n", 
            iterations, duration, avgResponseTime);
    }
    
    @Test
    void testConcurrentCacheAccess() throws InterruptedException {
        // Test concurrent access to the same cache key
        String testKey = TEST_KEY_PREFIX + "concurrent_test";
        Shop testShop = createTestShop(1L);
        
        cacheClient.set(testKey + "1", testShop, 30L, TimeUnit.SECONDS);
        
        int concurrentThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch latch = new CountDownLatch(concurrentThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        
        Function<Long, Shop> dbFallback = id -> createTestShop(id);
        
        for (int i = 0; i < concurrentThreads; i++) {
            executor.submit(() -> {
                try {
                    Shop result = cacheClient.queryWithPassThrough(
                        testKey, 1L, Shop.class, dbFallback);
                    if (result != null && result.getId().equals(1L)) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();
        
        assertEquals(concurrentThreads, successCount.get(), 
            "All threads should successfully retrieve cached data");
        
        System.out.println("Concurrent cache access test: PASSED");
    }
    
    @Test
    void testCacheKeyExpiration() throws InterruptedException {
        // Test that cache entries properly expire
        String testKey = TEST_KEY_PREFIX + "expiration_test";
        Shop testShop = createTestShop(1L);
        
        // Set with very short TTL
        cacheClient.set(testKey + "1", testShop, 1L, TimeUnit.SECONDS);
        
        // Should be present immediately
        assertNotNull(stringRedisTemplate.opsForValue().get(testKey + "1"), "Cache should contain data");
        
        // Wait for expiration
        Thread.sleep(1500);
        
        // Should be expired
        assertNull(stringRedisTemplate.opsForValue().get(testKey + "1"), "Cache should be expired");
        
        System.out.println("Cache expiration test: PASSED");
    }
    
    @Test
    void testBasicCacheOperations() {
        // Test basic set and get operations
        String testKey = TEST_KEY_PREFIX + "basic_test";
        Shop testShop = createTestShop(42L);
        
        // Set data
        cacheClient.set(testKey + "42", testShop, 30L, TimeUnit.SECONDS);
        
        // Verify it's cached
        String cachedData = stringRedisTemplate.opsForValue().get(testKey + "42");
        assertNotNull(cachedData, "Data should be cached");
        
        // Test retrieval through cache client
        Function<Long, Shop> dbFallback = id -> {
            fail("DB should not be called when data is cached");
            return null;
        };
        
        Shop retrieved = cacheClient.queryWithPassThrough(testKey, 42L, Shop.class, dbFallback);
        assertNotNull(retrieved, "Should retrieve cached data");
        assertEquals(42L, retrieved.getId(), "Retrieved data should match");
        assertEquals("Test Shop 42", retrieved.getName(), "Shop name should match");
        
        System.out.println("Basic cache operations test: PASSED");
    }
    
    private Shop createTestShop(Long id) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setName("Test Shop " + id);
        shop.setTypeId(1L);
        shop.setImages("test.jpg");
        shop.setArea("Test Area");
        shop.setAddress("Test Address");
        shop.setX(120.0);
        shop.setY(30.0);
        shop.setAvgPrice(100L);
        shop.setSold(50);
        shop.setComments(10);
        shop.setScore(5);
        shop.setOpenHours("9:00-22:00");
        shop.setCreateTime(LocalDateTime.now());
        shop.setUpdateTime(LocalDateTime.now());
        return shop;
    }
}