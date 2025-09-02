package com.paulyang.ecommerce.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for RedisIdWorker distributed ID generation
 * Validates uniqueness, performance, and thread-safety
 */
@SpringBootTest
@TestPropertySource(properties = "spring.profiles.active=local")
public class RedisIdWorkerTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    private RedisIdWorker redisIdWorker;
    
    @BeforeEach
    void setUp() {
        redisIdWorker = new RedisIdWorker(stringRedisTemplate);
    }
    
    @Test
    void testSingleThreadIdGeneration() {
        // Generate 1000 IDs in single thread and verify uniqueness
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            long id = redisIdWorker.nextId("test");
            assertTrue(ids.add(id), "Duplicate ID generated: " + id);
            assertTrue(id > 0, "ID should be positive");
        }
        assertEquals(1000, ids.size(), "All generated IDs should be unique");
    }
    
    @Test
    void testConcurrentIdGeneration() throws InterruptedException {
        // Test with 300 threads generating 100 IDs each
        int threadCount = 300;
        int idsPerThread = 100;
        int totalIds = threadCount * idsPerThread;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<Long> allIds = new HashSet<>(totalIds);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Set<Long> threadIds = new HashSet<>();
                    for (int j = 0; j < idsPerThread; j++) {
                        long id = redisIdWorker.nextId("concurrent-test");
                        threadIds.add(id);
                    }
                    
                    synchronized (allIds) {
                        allIds.addAll(threadIds);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Test should complete within 30 seconds");
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Verify all IDs are unique
        assertEquals(totalIds, allIds.size(), 
            String.format("Expected %d unique IDs but got %d", totalIds, allIds.size()));
        
        // Performance verification - should generate at least 1000 IDs per second
        double idsPerSecond = (double) totalIds / (duration / 1000.0);
        assertTrue(idsPerSecond > 1000, 
            String.format("Performance too low: %.2f IDs/second", idsPerSecond));
        
        System.out.printf("Generated %d unique IDs in %d ms (%.2f IDs/second)%n", 
            totalIds, duration, idsPerSecond);
    }
    
    @Test
    void testIdSequenceProperties() {
        // Test that IDs generated in sequence have expected properties
        long id1 = redisIdWorker.nextId("sequence-test");
        
        // Wait 1 second to ensure different timestamp
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long id2 = redisIdWorker.nextId("sequence-test");
        
        // ID2 should be greater than ID1 (time-based ordering)
        assertTrue(id2 > id1, "Later generated ID should be greater than earlier one");
    }
    
    @Test
    void testDifferentPrefixesIndependence() {
        // Test that different prefixes maintain independent counters
        Set<Long> orderIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        
        for (int i = 0; i < 100; i++) {
            orderIds.add(redisIdWorker.nextId("order"));
            userIds.add(redisIdWorker.nextId("user"));
        }
        
        assertEquals(100, orderIds.size(), "Order IDs should all be unique");
        assertEquals(100, userIds.size(), "User IDs should all be unique");
        
        // Verify no overlap between different prefix IDs
        Set<Long> intersection = new HashSet<>(orderIds);
        intersection.retainAll(userIds);
        assertTrue(intersection.isEmpty(), "Different prefixes should not generate overlapping IDs");
    }
    
    @Test
    void testHighVolumeStressTest() throws InterruptedException {
        // Stress test with very high load
        int threadCount = 100;
        int idsPerThread = 500;
        int totalIds = threadCount * idsPerThread;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<Long> allIds = new HashSet<>(totalIds);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < idsPerThread; j++) {
                        long id = redisIdWorker.nextId("stress-test");
                        synchronized (allIds) {
                            allIds.add(id);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(60, TimeUnit.SECONDS), "Stress test should complete within 60 seconds");
        executor.shutdown();
        
        assertEquals(totalIds, allIds.size(), 
            String.format("Stress test failed: Expected %d unique IDs but got %d", totalIds, allIds.size()));
    }
}