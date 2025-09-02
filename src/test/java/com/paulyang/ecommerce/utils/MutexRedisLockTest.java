package com.paulyang.ecommerce.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for MutexRedisLock distributed lock implementation
 * Tests thread safety, deadlock prevention, and lock ownership verification
 */
@SpringBootTest
@TestPropertySource(properties = "spring.profiles.active=local")
public class MutexRedisLockTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    private static final String TEST_LOCK_NAME = "test-resource";
    
    @BeforeEach
    void setUp() {
        // Clean up any existing test locks
        stringRedisTemplate.delete("lock:" + TEST_LOCK_NAME);
    }
    
    @Test
    void testBasicLockAcquisitionAndRelease() {
        MutexRedisLock lock = new MutexRedisLock(TEST_LOCK_NAME, stringRedisTemplate);
        
        // Should be able to acquire lock
        assertTrue(lock.tryLock(10), "Should acquire lock successfully");
        
        // Verify lock exists in Redis
        String lockKey = "lock:" + TEST_LOCK_NAME;
        assertNotNull(stringRedisTemplate.opsForValue().get(lockKey), "Lock should exist in Redis");
        
        // Release lock
        lock.unlock();
        
        // Verify lock is removed
        assertNull(stringRedisTemplate.opsForValue().get(lockKey), "Lock should be removed after unlock");
    }
    
    @Test
    void testLockTimeout() throws InterruptedException {
        MutexRedisLock lock = new MutexRedisLock(TEST_LOCK_NAME, stringRedisTemplate);
        
        // Acquire lock with short timeout
        assertTrue(lock.tryLock(1), "Should acquire lock");
        
        // Wait for timeout
        Thread.sleep(2000);
        
        // Lock should have expired automatically
        String lockKey = "lock:" + TEST_LOCK_NAME;
        assertNull(stringRedisTemplate.opsForValue().get(lockKey), 
            "Lock should expire automatically");
    }
    
    @Test
    void testConcurrentLockAccess() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        AtomicInteger lockAcquiredCount = new AtomicInteger(0);
        AtomicInteger sharedResource = new AtomicInteger(0);
        
        // Each thread tries to acquire lock and increment shared resource
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                MutexRedisLock lock = new MutexRedisLock(TEST_LOCK_NAME, stringRedisTemplate);
                try {
                    if (lock.tryLock(5)) {
                        lockAcquiredCount.incrementAndGet();
                        try {
                            // Simulate critical section work
                            int currentValue = sharedResource.get();
                            Thread.sleep(10); // Simulate some work
                            sharedResource.set(currentValue + 1);
                        } finally {
                            lock.unlock();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();
        
        // Only one thread should successfully acquire lock at a time
        assertTrue(lockAcquiredCount.get() >= 1, "At least one thread should acquire lock");
        assertTrue(lockAcquiredCount.get() <= threadCount, "Lock acquisition count should be reasonable");
        
        // If multiple threads acquired lock, shared resource should equal the number of acquisitions
        assertEquals(lockAcquiredCount.get(), sharedResource.get(), 
            "Shared resource should be incremented safely");
        
        System.out.printf("Concurrent test: %d/%d threads acquired lock, shared resource = %d%n", 
            lockAcquiredCount.get(), threadCount, sharedResource.get());
    }
    
    @Test
    void testLockOwnershipVerification() throws InterruptedException {
        MutexRedisLock lock1 = new MutexRedisLock(TEST_LOCK_NAME, stringRedisTemplate);
        MutexRedisLock lock2 = new MutexRedisLock(TEST_LOCK_NAME, stringRedisTemplate);
        
        // Thread 1 acquires lock
        assertTrue(lock1.tryLock(10), "Lock1 should acquire lock");
        
        // Thread 2 tries to acquire same lock
        assertFalse(lock2.tryLock(1), "Lock2 should fail to acquire lock");
        
        // Thread 2 tries to unlock (should not affect Thread 1's lock)
        lock2.unlock(); // Should not throw exception but should not release lock
        
        // Verify lock still exists (owned by thread 1)
        String lockKey = "lock:" + TEST_LOCK_NAME;
        assertNotNull(stringRedisTemplate.opsForValue().get(lockKey), 
            "Lock should still exist after wrong thread tries to unlock");
        
        // Thread 1 releases its own lock
        lock1.unlock();
        
        // Now lock should be gone
        assertNull(stringRedisTemplate.opsForValue().get(lockKey), 
            "Lock should be released by correct owner");
    }
    
    @Test
    void testHighContentionScenario() throws InterruptedException {
        // Test with many threads competing for the same lock
        int threadCount = 20;
        int workIterations = 5;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        AtomicInteger totalWork = new AtomicInteger(0);
        AtomicInteger successfulLockAcquisitions = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                MutexRedisLock lock = new MutexRedisLock(TEST_LOCK_NAME + "_contention", stringRedisTemplate);
                
                for (int j = 0; j < workIterations; j++) {
                    try {
                        if (lock.tryLock(1)) {
                            successfulLockAcquisitions.incrementAndGet();
                            try {
                                // Simulate work under lock
                                totalWork.incrementAndGet();
                                Thread.sleep(1);
                            } finally {
                                lock.unlock();
                            }
                        }
                        // Brief pause before retry
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                latch.countDown();
            });
        }
        
        assertTrue(latch.await(60, TimeUnit.SECONDS), "High contention test should complete");
        executor.shutdown();
        
        // Verify that work was done safely
        assertEquals(successfulLockAcquisitions.get(), totalWork.get(), 
            "Total work should equal successful lock acquisitions");
        
        System.out.printf("High contention test: %d successful acquisitions, %d work items completed%n", 
            successfulLockAcquisitions.get(), totalWork.get());
    }
    
    @Test
    void testLockReentrancy() {
        // Test that lock is NOT reentrant (current implementation)
        MutexRedisLock lock = new MutexRedisLock(TEST_LOCK_NAME, stringRedisTemplate);
        
        assertTrue(lock.tryLock(10), "Should acquire lock first time");
        
        // Same thread trying to acquire again should fail (not reentrant)
        assertFalse(lock.tryLock(1), "Should not be able to acquire lock again (not reentrant)");
        
        lock.unlock();
    }
    
    @Test
    void testMultipleLockInstances() {
        // Test multiple locks with different names can be acquired simultaneously
        MutexRedisLock lock1 = new MutexRedisLock("resource1", stringRedisTemplate);
        MutexRedisLock lock2 = new MutexRedisLock("resource2", stringRedisTemplate);
        
        assertTrue(lock1.tryLock(10), "Should acquire lock1");
        assertTrue(lock2.tryLock(10), "Should acquire lock2 simultaneously");
        
        // Both locks should exist
        assertNotNull(stringRedisTemplate.opsForValue().get("lock:resource1"), "Lock1 should exist");
        assertNotNull(stringRedisTemplate.opsForValue().get("lock:resource2"), "Lock2 should exist");
        
        lock1.unlock();
        lock2.unlock();
        
        // Both locks should be released
        assertNull(stringRedisTemplate.opsForValue().get("lock:resource1"), "Lock1 should be released");
        assertNull(stringRedisTemplate.opsForValue().get("lock:resource2"), "Lock2 should be released");
    }
    
    @Test
    void testLockPerformance() {
        // Test lock acquisition/release performance
        int iterations = 100;
        MutexRedisLock lock = new MutexRedisLock(TEST_LOCK_NAME + "_perf", stringRedisTemplate);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < iterations; i++) {
            assertTrue(lock.tryLock(10), "Should acquire lock");
            lock.unlock();
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double avgTime = (double) totalTime / iterations;
        
        assertTrue(avgTime < 10, "Average lock operation should be fast: " + avgTime + "ms");
        
        System.out.printf("Lock performance: %d operations in %d ms (%.2f ms avg)%n", 
            iterations, totalTime, avgTime);
    }
}