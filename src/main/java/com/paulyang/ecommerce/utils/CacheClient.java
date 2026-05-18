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
        String json = stringRedisTemplate.opsForValue().get(key);
        // Cache hit - return the stored value
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // Negative Cache Entry hit - the record is known to be absent
        if (isNegativeCacheHit(json)) {
            return null;
        }
        // Cache miss - fall back to the database
        R r = dbFallback.apply(id);
        if (r == null) {
            writeNegativeCacheEntry(key);
            return null;
        }
        // Cache the resolved value
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return r;
    }

    /**
     * Tests whether the cached payload is a Negative Cache Entry: a non-null but
     * blank value recorded to mark that the backing record is known to be absent.
     *
     * @param cached the raw value read from Redis (may be {@code null} on a cache miss)
     * @return {@code true} if {@code cached} is a Negative Cache Entry
     */
    private boolean isNegativeCacheHit(String cached) {
        return cached != null && StrUtil.isBlank(cached);
    }

    /**
     * Writes a Negative Cache Entry under {@code key} so repeat lookups for the
     * absent record are answered from Redis instead of the database
     * (Cache Penetration defence).
     *
     * @param key the Redis key whose backing record is absent
     */
    private void writeNegativeCacheEntry(String key) {
        stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
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
        // Logically expired - exactly one reader rebuilds in the background
        String rebuildLockKey = LOCK_SHOP_KEY + id;
        if (acquireRebuildLock(rebuildLockKey)) {
            // Rebuild Lock held - reconstruct asynchronously, then release it
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    releaseRebuildLock(rebuildLockKey);
                }
            });
        }
        // Stale-on-expiry read - serve the existing entry while it refreshes
        return r;
    }

    /**
     * Attempts to take the Rebuild Lock: a single-holder marker so that exactly
     * one reader reconstructs a logically expired key while others are served
     * the existing entry (Cache Breakdown defence).
     *
     * @param lockKey the Redis key holding the Rebuild Lock
     * @return {@code true} if this caller now holds the Rebuild Lock
     */
    private boolean acquireRebuildLock(String lockKey) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * Releases the Rebuild Lock so the next logically expired read may rebuild.
     *
     * @param lockKey the Redis key holding the Rebuild Lock
     */
    private void releaseRebuildLock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }
}
