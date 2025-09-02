package com.paulyang.ecommerce.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Distributed globally unique ID generator using Redis atomic operations.
 * This component generates unique, time-sortable IDs in a distributed environment
 * without requiring database sequences or coordination between multiple instances.
 * 
 * <p>ID Structure (64-bit long):</p>
 * <pre>
 * | Unused | Timestamp (31 bits) | Sequence (32 bits) |
 * |   1    |        31          |        32         |
 * </pre>
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Time-based ordering: IDs are naturally sorted by creation time</li>
 *   <li>High performance: Uses Redis INCR for atomic sequence generation</li>
 *   <li>Collision-free: Timestamp + atomic counter guarantees uniqueness</li>
 *   <li>Daily reset: Counter resets daily to prevent overflow</li>
 * </ul>
 * 
 * @author Paul Yang
 * @since 1.0
 */
@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final int COUNT_BITS = 32;
    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Generate the next unique ID for the specified business type.
     * This method creates a globally unique, time-sortable 64-bit ID using a combination
     * of timestamp and Redis atomic increment operations.
     * 
     * <p>Algorithm:</p>
     * <ol>
     *   <li>Calculate timestamp difference from epoch (2022-01-01)</li>
     *   <li>Get atomic counter from Redis for current date and key prefix</li>
     *   <li>Combine timestamp (high 32 bits) with counter (low 32 bits)</li>
     * </ol>
     * 
     * <p>The counter resets daily (per keyPrefix) to prevent overflow and maintain
     * reasonable ID lengths. Each business type (keyPrefix) has its own counter
     * sequence to avoid conflicts between different entity types.</p>
     * 
     * <p>Performance: Can generate ~4 billion IDs per day per keyPrefix.</p>
     *
     * @param keyPrefix business type identifier (e.g., "order", "user", "voucher")
     * @return globally unique 64-bit ID that's sortable by creation time
     * @throws RuntimeException if Redis operations fail
     * @since 1.0
     */
    public long nextId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        return timeStamp << COUNT_BITS | count;
    }

//    public static void main(String[] args) {
//        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
//        long second = time.toEpochSecond(ZoneOffset.MAX.UTC);
//        System.out.println("second =" + second);
//    }
}
