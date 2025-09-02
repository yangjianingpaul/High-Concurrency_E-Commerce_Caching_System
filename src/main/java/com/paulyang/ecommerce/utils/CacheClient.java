package com.paulyang.ecommerce.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.paulyang.ecommerce.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.events.Event;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.paulyang.ecommerce.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * Query with cache penetration protection using cache-aside pattern.
     * This method implements a robust caching strategy that prevents cache penetration
     * attacks by caching null values when data doesn't exist in the database.
     * 
     * <p>Cache Strategy:</p>
     * <ul>
     *   <li>Cache Hit: Return data directly from Redis</li>
     *   <li>Cache Miss: Query database and cache result</li>
     *   <li>Null Protection: Cache empty string for non-existent data</li>
     * </ul>
     * 
     * <p>This prevents malicious requests for non-existent data from overwhelming
     * the database by ensuring that even "null" results are cached temporarily.</p>
     *
     * @param <R> the return type of the cached object
     * @param <ID> the type of the identifier used for caching
     * @param keyPrefix the Redis key prefix for cache storage
     * @param id the unique identifier for the cached object
     * @param type the Class type for JSON deserialization
     * @param dbFallback function to execute when cache misses, typically a database query
     * @return the cached object of type R, or null if not found
     * @throws RuntimeException if JSON deserialization fails
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback) {
        String key = keyPrefix + id;
        // 1. Query cache from Redis
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. Check if data exists in cache
        if (StrUtil.isNotBlank(json)) {
            // 3. Cache hit - return data directly
            return JSONUtil.toBean(json, type);
        }
        // Check if this is a cached null value (empty string)
        if (json != null) {
            return null;
        }
        // 4. Cache miss - query database
        R r = dbFallback.apply(id);
        // 5. Handle null result from database
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6. Cache the result in Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * Query with logical expiration to prevent cache breakdown in high-concurrency scenarios.
     * This method implements a sophisticated caching strategy using logical expiration timestamps
     * instead of Redis TTL, ensuring that hot data is always available while being refreshed
     * asynchronously in the background.
     * 
     * <p>Logical Expiration Strategy:</p>
     * <ul>
     *   <li>Cache Never Expires: Data always remains in Redis</li>
     *   <li>Logical Timestamp: Embedded expiration time in cached data</li>
     *   <li>Background Refresh: Expired data is refreshed by background threads</li>
     *   <li>Mutex Protection: Only one thread rebuilds cache per key</li>
     * </ul>
     * 
     * <p>This approach prevents cache breakdown where multiple threads simultaneously
     * try to rebuild the same cache key, which could overwhelm the database during
     * high-concurrency scenarios.</p>
     * 
     * <p><strong>Important:</strong> This method requires that the cached data was originally
     * stored using {@link #setWithLogicalExpire(String, Object, Long, TimeUnit)}.</p>
     *
     * @param <R> the return type of the cached object
     * @param <ID> the type of the identifier used for caching
     * @param keyPrefix the Redis key prefix for cache storage
     * @param id the unique identifier for the cached object
     * @param type the Class type for JSON deserialization
     * @param dbFallback function to execute for cache rebuild, typically a database query
     * @param time the logical expiration time duration
     * @param unit the time unit for the expiration duration
     * @return the cached object of type R, or null if cache doesn't exist
     * @throws RuntimeException if JSON deserialization fails or database query fails
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. Query cache from Redis
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. Check if cache exists
        if (StrUtil.isBlank(json)) {
            return null;
        }

        // Cache hit - deserialize JSON to object
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // Check if logically expired
        if (expireTime.isAfter(LocalDateTime.now())) {
            // Not expired - return cached data directly
            return r;
        }
        // Expired - needs cache reconstruction
        // Try to acquire mutex lock for cache rebuild
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // Check if lock acquisition was successful
        if (isLock) {
            // Lock acquired - start background thread for cache reconstruction
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        // Return expired data (better than no data)
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
