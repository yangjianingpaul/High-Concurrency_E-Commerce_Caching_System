package com.paulyang.ecommerce.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paulyang.ecommerce.dto.Result;
import com.paulyang.ecommerce.entity.Shop;
import com.paulyang.ecommerce.mapper.ShopMapper;
import com.paulyang.ecommerce.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.paulyang.ecommerce.utils.CacheClient;
import com.paulyang.ecommerce.utils.RedisData;
import com.paulyang.ecommerce.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.paulyang.ecommerce.utils.RedisConstants.*;

/**
 * service’s implement class
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * Solve the problem of cache penetration and breakdown
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
//        1。resolve cache penetration
//        Shop shop = queryWithPassThrough(id);
//        2。the mutex solves the cache breakdown
//        Shop shop = queryWithMutex(id);
//        3。solve cache breakdown with logical expiration
        Shop shop = queryWithLogicalExpire(id);

//        4。the utility class solves cache penetration
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById);
//        5。the utility class solves cache breakdown
//        Shop shop = cacheClient.queryWithLogicalExpire(
//                CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("the store does not exist！");
        }
        return Result.ok(shop);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * logical expiration resolves cache breakdown
     *
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
//        1。query the store cache from redis
        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        2。determine if it exists
        if (StrUtil.isBlank(shopJson)) {
            createRedisCash(id);
//            3。does not exist check the database
            Shop shop = getById(id);
            return shop;
        }

//        4。hit, you need to deserialize the json into an object first
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
//        5。determine whether it has expired
        if (expireTime.isAfter(LocalDateTime.now())) {
            //        5。1. Return the store information directly before it expires
            return shop;
        }
//        5。2. Expired cache rebuild required
//        6。cache rebuild
//        6。1 get a mutex
        createRedisCash(id);
//        6。4 The mutex is not obtained, and the expired store information is returned
        return shop;
    }

    /**
     * a redis cache is created when the logic expires
     *
     * @param id
     */
    private void createRedisCash(Long id) {
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
//        6。2 check whether the lock is obtained
        if (isLock) {
            //        6。3 successfully, start an independent thread to rebuild the cache
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
    }

    /**
     * the mutex solves the cache breakdown
     *
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
//        1。query the store cache from redis
        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        2。determine if it exists
//        3。exists returns directly
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
//        4。If it does not exist, query the database based on the ID
//        determines whether the hit is null
        if (shopJson != null) {
            return null;
        }

//        4。1。get a mutex
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
//        4。2。determine whether the acquisition is successful
//        4。3。fail hibernate and try again
            if (!isLock) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
//        4。4。succeeded the database is queried based on the id
            shop = getById(id);
//            Thread.sleep(200);

//            resolve cache penetration
/*********************************************************************************/
//        5。does not exist returns an error
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
/*********************************************************************************/

//        6。exists write to redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
        return shop;
    }

    /**
     * resolve cache penetration
     *
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
//        1。query the store cache from redis
        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        2。determine if it exists
        if (StrUtil.isNotBlank(shopJson)) {
//        3。exists returns directly
            return JSONUtil.toBean(shopJson, Shop.class);
        }
//        determines whether the hit is null
        if (shopJson != null) {
            return null;
        }
//        4。If it does not exist, query the database based on the ID
        Shop shop = getById(id);
//        5。does not exist returns an error
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
//        6。exists write to redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    /**
     * get lock
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * release lock
     *
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        Shop shop = getById(id);
//        Thread.sleep(200);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("The store id cannot be empty");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    /**
     * Shop sorting
     * 1。distance
     * 2。popularity
     * 3。score
     *
     * @param typeId
     * @param current
     * @param sortBy
     * @param x
     * @param y
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, String sortBy, Double x, Double y) {
//        do you need to query based on coordinates
        if (x == null || y == null) {
            // paging query based on type
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // return data
            return Result.ok(page.getRecords());
        }

        if (sortBy != null && !sortBy.isEmpty()) {
            return queryShopByComments(typeId, current, sortBy);
        }

//        Calculate paging parameters
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

//        query redis to sort and paging results by distance：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
//        parse out the id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });

//        query shop based on id
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD( id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
//        return
        return Result.ok(shops);
    }

    /**
     * sort by popularity or rating
     *
     * @param typeId
     * @param current
     * @param sortBy
     * @return
     */
    private Result queryShopByComments(Integer typeId, Integer current, String sortBy) {

        Page<Shop> page = new Page<>();
        if (sortBy.equals("comments")) {
            page = query()
                    .eq("type_id", typeId)
                    .orderByDesc("comments")
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        } else {
            page = query()
                    .eq("type_id", typeId)
                    .orderByDesc("score")
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        }

        // return data
        return Result.ok(page.getRecords());
    }
}
