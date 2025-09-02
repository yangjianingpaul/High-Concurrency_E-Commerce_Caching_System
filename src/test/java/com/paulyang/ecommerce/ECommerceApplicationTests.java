package com.paulyang.ecommerce;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import com.paulyang.ecommerce.dto.UserDTO;
import com.paulyang.ecommerce.entity.Shop;
import com.paulyang.ecommerce.entity.User;
import com.paulyang.ecommerce.service.impl.ShopServiceImpl;
import com.paulyang.ecommerce.service.impl.UserServiceImpl;
import com.paulyang.ecommerce.utils.CacheClient;
import com.paulyang.ecommerce.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisCommand;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.paulyang.ecommerce.utils.RedisConstants.*;

/**
 * Integration tests for the High-Concurrency E-Commerce System.
 * This test suite validates critical system components including ID generation,
 * caching mechanisms, geolocation services, and Redis operations.
 * 
 * <p>Test Categories:</p>
 * <ul>
 *   <li>ID Worker Performance: Multi-threaded ID generation testing</li>
 *   <li>Cache Operations: Logical expiration and shop data caching</li>
 *   <li>Geospatial Data: Location-based shop indexing in Redis</li>
 *   <li>HyperLogLog: Unique visitor counting validation</li>
 *   <li>User Token Generation: Batch token creation for load testing</li>
 * </ul>
 * 
 * @author Paul Yang
 * @since 1.0
 */
@SpringBootTest
class ECommerceApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserServiceImpl userService;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id =" + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        long end = System.currentTimeMillis();
        System.out.println("time =" + (end - begin));
    }

    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

    /**
     * store shop location information
     */
    @Test
    void loadShopData() {
        List<Shop> list = shopService.list();
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            for (Shop shop : value) {
//                stringRedisTemplate.opsForGeo().add(
//                        key,
//                        new Point(shop.getX(), shop.getY()),
//                        shop.getId().toString());

                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if (j == 999) {
//                send to redis
                stringRedisTemplate.opsForHyperLogLog().add("h12", values);
            }
        }
        Long count = stringRedisTemplate.opsForHyperLogLog().size("h12");
        System.out.println("count = " + count);
    }

    @Test
    void testGetAll() {
        for (int i = 1; i < 1005; i++) {
            User user = userService.getById(i);
            if (user == null) {
                continue;
            }

            String token = UUID.randomUUID().toString(true);

            File file = new File("/Users/yangjianing/Desktop/token.txt");
            FileOutputStream outputStream = null;

            try {
                outputStream = new FileOutputStream(file, true);
                byte[] bytes = token.getBytes();
                outputStream.write(bytes);
                outputStream.write("\r\n".getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
            String tokenKey = LOGIN_USER_KEY + token;
            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
            stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        }
    }
}
