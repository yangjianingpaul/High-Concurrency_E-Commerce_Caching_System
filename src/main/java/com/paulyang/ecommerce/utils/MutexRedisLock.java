package com.paulyang.ecommerce.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based distributed mutex lock implementation
 * Provides thread-safe locking mechanism using Redis SET commands
 * with automatic expiration and thread identification
 */
public class MutexRedisLock implements ILock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public MutexRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //    read lua script ahead of time
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * Attempt to acquire the distributed lock with automatic expiration.
     * This method uses Redis SET IF NOT EXISTS operation with expiration time
     * to implement a distributed mutex lock with thread ownership identification.
     * 
     * <p>Lock Mechanism:</p>
     * <ul>
     *   <li>Unique Thread ID: Each lock attempt is tagged with a unique thread identifier</li>
     *   <li>Atomic Operation: Uses Redis SETNX + EXPIRE in single operation</li>
     *   <li>Auto-Expiration: Prevents deadlocks if thread crashes before unlock</li>
     *   <li>Ownership Verification: Only the thread that acquired lock can release it</li>
     * </ul>
     * 
     * <p>Thread ID Format: {UUID}-{ThreadId} to ensure global uniqueness across
     * multiple JVM instances and prevent accidental lock release by different threads.</p>
     * 
     * <p><strong>Important:</strong> Always use try-finally pattern to ensure
     * {@link #unlock()} is called even if business logic throws exceptions:</p>
     * <pre>{@code
     * MutexRedisLock lock = new MutexRedisLock("resource", template);
     * if (lock.tryLock(10)) {
     *     try {
     *         // Critical section
     *     } finally {
     *         lock.unlock();
     *     }
     * }
     * }</pre>
     *
     * @param timeoutSec lock expiration timeout in seconds (prevents deadlock)
     * @return true if lock was successfully acquired, false if already locked by another thread
     * @throws RuntimeException if Redis operations fail
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
//        a)Lua script solves the problem of accidental deletion of locks
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());

//        b)Add thread identification to solve the problem of accidentally deleting locks
//        get thread id
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        get the lock id
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if (threadId.equals(id)) {
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}
