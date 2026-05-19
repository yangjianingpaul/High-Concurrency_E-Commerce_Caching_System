package com.paulyang.ecommerce.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    
    /**
     * CHARACTERIZATION (not a guarantee).
     *
     * <p>This test previously asserted that a "wrong thread" could not release
     * another's lock. That assertion was false: {@code lock1} and {@code lock2}
     * were created and used on the <em>same</em> JUnit thread. The owner token is
     * {@code ID_PREFIX + Thread.currentThread().getId()}, where {@code ID_PREFIX}
     * is a single static UUID per JVM/classload — so two different
     * {@link MutexRedisLock} instances on the same thread share the
     * <strong>same owner token</strong>. The {@code unlock.lua} owner check
     * therefore passes for {@code lock2}, and {@code lock2.unlock()} releases
     * {@code lock1}'s lock.</p>
     *
     * <p>This is <strong>not</strong> wrong-owner protection. A genuine
     * foreign-owner test must run the second unlock on a <em>different thread</em>
     * (Issue 02). This test now pins the real same-thread/same-token behavior.</p>
     */
    @Test
    void testSameThreadDifferentInstanceSharesOwnerToken() {
        MutexRedisLock lock1 = new MutexRedisLock(TEST_LOCK_NAME, stringRedisTemplate);
        MutexRedisLock lock2 = new MutexRedisLock(TEST_LOCK_NAME, stringRedisTemplate);
        String lockKey = "lock:" + TEST_LOCK_NAME;

        // lock1 acquires; lock2 cannot acquire while the key is present.
        assertTrue(lock1.tryLock(10), "lock1 should acquire the lock");
        assertFalse(lock2.tryLock(1),
            "lock2 cannot acquire while the key is present");

        // Same-thread token: lock2's owner token equals lock1's, so the
        // unlock.lua owner check passes and lock2.unlock() DELETES lock1's key.
        lock2.unlock();
        assertNull(stringRedisTemplate.opsForValue().get(lockKey),
            "characterized: same-thread different instance shares the owner "
            + "token, so lock2.unlock() releases lock1's lock (key now gone)");

        // lock1.unlock() is now a no-op: the key is already absent.
        lock1.unlock();
        assertNull(stringRedisTemplate.opsForValue().get(lockKey),
            "key remains absent after the redundant lock1.unlock()");
    }

    /**
     * CHARACTERIZATION — true foreign-owner protection.
     *
     * <p>Complements {@link #testSameThreadDifferentInstanceSharesOwnerToken()}:
     * the owner token is {@code ID_PREFIX + Thread.currentThread().getId()}, so a
     * genuinely <em>different thread</em> has a different token. The
     * {@code unlock.lua} owner check then fails for the foreign thread and the
     * lock is preserved. This is the protection the original same-thread test
     * could never actually exercise.</p>
     */
    @Test
    void testDifferentThreadCannotReleaseForeignOwnersLock() throws Exception {
        String lockKey = "lock:" + TEST_LOCK_NAME;
        ExecutorService owner = Executors.newSingleThreadExecutor();
        try {
            // Acquire on the owner thread (token = owner thread id).
            Future<Boolean> acquired = owner.submit(() ->
                new MutexRedisLock(TEST_LOCK_NAME, stringRedisTemplate).tryLock(10));
            assertTrue(acquired.get(), "owner thread should acquire the lock");
            assertNotNull(stringRedisTemplate.opsForValue().get(lockKey),
                "lock key present after owner acquires");

            // A different thread (the JUnit thread) attempts to unlock.
            new MutexRedisLock(TEST_LOCK_NAME, stringRedisTemplate).unlock();
            assertNotNull(stringRedisTemplate.opsForValue().get(lockKey),
                "characterized: foreign-thread unlock does NOT release the lock "
                + "(owner-token mismatch in unlock.lua)");

            // The owning thread can still release it afterwards.
            owner.submit(() ->
                new MutexRedisLock(TEST_LOCK_NAME, stringRedisTemplate).unlock())
                .get();
            assertNull(stringRedisTemplate.opsForValue().get(lockKey),
                "owner thread releases its own lock successfully");
        } finally {
            owner.shutdownNow();
        }
    }

    /**
     * CHARACTERIZATION — fixed-lease expiry while the holder is still active.
     *
     * <p>{@code MutexRedisLock} sets a fixed TTL at acquire time and has no
     * watchdog / lease-renewal. A critical section that outlives the TTL leaves
     * the holder believing it still owns a lock that has already expired, so a
     * second caller can acquire concurrently. This is a limitation of
     * fixed-lease locks, not a defect in {@code SET NX EX} itself.</p>
     */
    @Test
    void testLeaseExpiresWhileHolderStillActive() throws InterruptedException {
        MutexRedisLock holder = new MutexRedisLock(TEST_LOCK_NAME, stringRedisTemplate);

        assertTrue(holder.tryLock(1), "holder acquires with a 1s lease");

        // Holder is still "inside the critical section" past the lease.
        Thread.sleep(1500);

        // Lease has expired with no renewal: a second caller can now acquire,
        // while the original holder still assumes it holds the lock.
        MutexRedisLock contender = new MutexRedisLock(TEST_LOCK_NAME, stringRedisTemplate);
        assertTrue(contender.tryLock(5),
            "characterized: fixed lease expired under an active holder, so a "
            + "second caller acquires concurrently (no watchdog/renewal)");

        contender.unlock();
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
    @Disabled("Quarantined brittle timing assertion for characterization baseline")
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