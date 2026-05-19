package com.paulyang.ecommerce.service;

import com.paulyang.ecommerce.dto.Result;
import com.paulyang.ecommerce.dto.UserDTO;
import com.paulyang.ecommerce.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.test.context.TestPropertySource;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization test for slice 2A-1A: the missing Redis Stream
 * consumer-group bootstrap boundary.
 *
 * <p>This pins the observed boundary fact only: the production seckill path
 * writes to {@code stream.orders} via seckill.lua, while no production or test
 * code bootstraps consumer group {@code g1}. It does NOT assert downstream
 * persistence or any pipeline-correctness claim.</p>
 */
@SpringBootTest
@TestPropertySource(properties = "spring.profiles.active=local")
public class SeckillStreamConsumerGroupCharacterizationTest {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String STREAM_KEY = "stream.orders";
    private static final String CONSUMER_GROUP = "g1";

    // Unique per run so the test never collides with shared seckill keys
    // (e.g. VoucherOrderServiceIntegrationTest's voucher id 1).
    private Long testVoucherId;
    private Long testUserId;

    @BeforeEach
    void setUp() {
        testVoucherId = 990_000_000L + Math.floorMod(System.nanoTime(), 1_000_000L);
        testUserId = testVoucherId; // any positive id unique to this run
        cleanupTestOwnedKeys();
        // seckill.lua does tonumber(get(stockKey)) <= 0, so the stock key must exist.
        stringRedisTemplate.opsForValue().set(stockKey(), "10");
    }

    @AfterEach
    void tearDown() {
        cleanupTestOwnedKeys();
        UserHolder.removeUser();
    }

    private void cleanupTestOwnedKeys() {
        stringRedisTemplate.delete(STREAM_KEY);
        stringRedisTemplate.delete(stockKey());
        stringRedisTemplate.delete(orderSetKey());
    }

    private String stockKey() {
        return "seckill:stock:" + testVoucherId;
    }

    private String orderSetKey() {
        return "seckill:order:" + testVoucherId;
    }

    private Result invokeSeckillAsTestUser() {
        UserDTO user = new UserDTO();
        user.setId(testUserId);
        user.setNickName("charuser" + testUserId);
        user.setIcon("");
        UserHolder.saveUser(user);
        return voucherOrderService.seckillVoucher(testVoucherId);
    }

    @Test
    void seckillVoucher_eligibleUser_returnsSuccessAndOrderId() {
        Result result = invokeSeckillAsTestUser();

        assertTrue(result.getSuccess(), "Eligible single-user seckill should return success");
        assertNotNull(result.getData(), "Order id should be returned on success");
    }

    @Test
    void seckillVoucher_eligibleUser_enqueuesEntryOnStreamOrders() {
        Result result = invokeSeckillAsTestUser();
        assertTrue(result.getSuccess(), "Precondition: seckill must succeed to enqueue");

        String expectedOrderId = String.valueOf(result.getData());

        // seckill.lua's XADD is synchronous within the request thread, so the
        // entry is observable immediately; no async wait is involved here.
        List<MapRecord<String, Object, Object>> records =
                stringRedisTemplate.opsForStream().range(STREAM_KEY, Range.unbounded());

        assertNotNull(records, "stream.orders should exist after a successful seckill");
        boolean matched = records.stream().anyMatch(r -> {
            Map<Object, Object> v = r.getValue();
            return expectedOrderId.equals(String.valueOf(v.get("id")))
                    && String.valueOf(testUserId).equals(String.valueOf(v.get("userId")))
                    && String.valueOf(testVoucherId).equals(String.valueOf(v.get("voucherId")));
        });
        assertTrue(matched,
                "stream.orders should contain an entry matching the returned order id, user and voucher");
    }

    @Test
    void productionCodePath_doesNotBootstrapConsumerGroupG1() {
        Result result = invokeSeckillAsTestUser();
        assertTrue(result.getSuccess(), "Precondition: seckill must succeed so stream.orders exists");

        // Stream exists (XADD created it); characterize that the production
        // code path nonetheless leaves consumer group g1 uncreated.
        StreamInfo.XInfoGroups groups = stringRedisTemplate.opsForStream().groups(STREAM_KEY);
        boolean g1Present = groups.stream()
                .anyMatch(g -> CONSUMER_GROUP.equals(g.groupName()));
        assertFalse(g1Present,
                "Production seckill path must not bootstrap consumer group g1 (it exists only as a comment)");
    }
}
