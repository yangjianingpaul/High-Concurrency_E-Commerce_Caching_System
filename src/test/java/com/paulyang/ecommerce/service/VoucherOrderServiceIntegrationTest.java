package com.paulyang.ecommerce.service;

import com.paulyang.ecommerce.entity.SeckillVoucher;
import com.paulyang.ecommerce.entity.VoucherOrder;
import com.paulyang.ecommerce.dto.Result;
import com.paulyang.ecommerce.mapper.VoucherOrderMapper;
import com.paulyang.ecommerce.utils.UserHolder;
import com.paulyang.ecommerce.dto.UserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for VoucherOrderService seckill functionality
 * Tests the critical high-concurrency flash sale mechanism
 */
@SpringBootTest
@TestPropertySource(properties = "spring.profiles.active=local")
public class VoucherOrderServiceIntegrationTest {

    @Resource
    private IVoucherOrderService voucherOrderService;
    
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    
    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    private static final Long TEST_VOUCHER_ID = 1L;
    private static final int INITIAL_STOCK = 50;
    private static final int CONCURRENT_USERS = 100;
    
    @BeforeEach
    void setUp() {
        // Clear any existing test data
        cleanupTestData();
        
        // Initialize test voucher with stock
        initializeTestVoucher();
        
        // Setup Redis stock
        setupRedisStock();
    }
    
    private void cleanupTestData() {
        // Clean up previous test data
        stringRedisTemplate.delete("seckill:stock:" + TEST_VOUCHER_ID);
        stringRedisTemplate.delete("seckill:order:" + TEST_VOUCHER_ID);
        
        // Clean up database records for test voucher (simplified approach)
        try {
            var existingOrders = voucherOrderMapper.selectList(null);
            for (VoucherOrder order : existingOrders) {
                if (TEST_VOUCHER_ID.equals(order.getVoucherId())) {
                    voucherOrderMapper.deleteById(order.getId());
                }
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
    
    private void initializeTestVoucher() {
        // Create or update seckill voucher with test data
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(TEST_VOUCHER_ID);
        seckillVoucher.setStock(INITIAL_STOCK);
        seckillVoucher.setBeginTime(LocalDateTime.now().minusHours(1));
        seckillVoucher.setEndTime(LocalDateTime.now().plusHours(1));
        
        // Save or update the voucher
        seckillVoucherService.saveOrUpdate(seckillVoucher);
    }
    
    private void setupRedisStock() {
        // Initialize Redis stock counter
        stringRedisTemplate.opsForValue().set("seckill:stock:" + TEST_VOUCHER_ID, String.valueOf(INITIAL_STOCK));
    }
    
    @Test
    void testSingleUserSeckill() {
        // Test single user seckill success
        UserDTO testUser = createTestUser(1L);
        UserHolder.saveUser(testUser);
        
        try {
            Result result = voucherOrderService.seckillVoucher(TEST_VOUCHER_ID);
            assertTrue(result.getSuccess(), "Single user seckill should succeed");
            assertNotNull(result.getData(), "Order ID should be returned");
            
            // Wait for async processing
            waitForAsyncProcessing();
            
            // Verify order was created in database
            long orderCount = voucherOrderMapper.selectList(null).stream()
                .mapToLong(order -> TEST_VOUCHER_ID.equals(order.getVoucherId()) ? 1 : 0)
                .sum();
            assertTrue(orderCount >= 1, "At least one order should be created");
            
        } finally {
            UserHolder.removeUser();
        }
    }
    
    @Test
    void testDuplicateOrderPrevention() {
        // Test that same user cannot place duplicate orders
        UserDTO testUser = createTestUser(1L);
        UserHolder.saveUser(testUser);
        
        try {
            // First order should succeed
            Result firstResult = voucherOrderService.seckillVoucher(TEST_VOUCHER_ID);
            assertTrue(firstResult.getSuccess(), "First order should succeed");
            
            // Second order should fail
            Result secondResult = voucherOrderService.seckillVoucher(TEST_VOUCHER_ID);
            assertFalse(secondResult.getSuccess(), "Second order should fail");
            assertTrue(secondResult.getErrorMsg().contains("Duplicate"), 
                "Error message should indicate duplicate order");
                
        } finally {
            UserHolder.removeUser();
        }
    }
    
    @Test 
    void testConcurrentSeckillNoOverselling() throws InterruptedException {
        // The most critical test: verify no overselling under high concurrency
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        // Launch concurrent users
        for (int i = 1; i <= CONCURRENT_USERS; i++) {
            final long userId = i;
            executor.submit(() -> {
                UserDTO user = createTestUser(userId);
                UserHolder.saveUser(user);
                
                try {
                    Result result = voucherOrderService.seckillVoucher(TEST_VOUCHER_ID);
                    if (result.getSuccess()) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.err.println("User " + userId + " encountered error: " + e.getMessage());
                } finally {
                    UserHolder.removeUser();
                    latch.countDown();
                }
            });
        }
        
        // Wait for all threads to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete within 30 seconds");
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        
        // Wait for async order processing
        waitForAsyncProcessing();
        
        // Count actual orders created in database
        long actualOrderCount = voucherOrderMapper.selectList(null).stream()
            .mapToLong(order -> TEST_VOUCHER_ID.equals(order.getVoucherId()) ? 1 : 0)
            .sum();
        
        // Verify results
        System.out.printf("Concurrency Test Results:%n");
        System.out.printf("- Success responses: %d%n", successCount.get());
        System.out.printf("- Failure responses: %d%n", failureCount.get());
        System.out.printf("- Actual orders created: %d%n", actualOrderCount);
        System.out.printf("- Test duration: %d ms%n", endTime - startTime);
        System.out.printf("- Expected stock: %d%n", INITIAL_STOCK);
        
        // Critical assertion: No overselling
        assertTrue(actualOrderCount <= INITIAL_STOCK, 
            String.format("OVERSELLING DETECTED! Created %d orders but only had %d stock", 
                actualOrderCount, INITIAL_STOCK));
        
        // Verify total responses equal total users
        assertEquals(CONCURRENT_USERS, successCount.get() + failureCount.get(),
            "All users should receive a response");
        
        // Verify remaining stock
        String remainingStock = stringRedisTemplate.opsForValue().get("seckill:stock:" + TEST_VOUCHER_ID);
        if (remainingStock != null) {
            assertTrue(Integer.parseInt(remainingStock) >= 0, "Stock should not be negative");
        }
    }
    
    @Test
    void testStockDepletionHandling() throws InterruptedException {
        // Test behavior when stock is depleted
        // First deplete all stock
        stringRedisTemplate.opsForValue().set("seckill:stock:" + TEST_VOUCHER_ID, "0");
        
        UserDTO testUser = createTestUser(999L);
        UserHolder.saveUser(testUser);
        
        try {
            Result result = voucherOrderService.seckillVoucher(TEST_VOUCHER_ID);
            assertFalse(result.getSuccess(), "Order should fail when stock is 0");
            assertTrue(result.getErrorMsg().contains("Insufficient"), 
                "Error message should indicate insufficient inventory");
        } finally {
            UserHolder.removeUser();
        }
    }
    
    @Test
    void testHighLoadStressTest() throws InterruptedException {
        // Stress test with moderate concurrency to avoid overwhelming the system
        int stressTestUsers = 50;
        int stressTestStock = 25;
        
        // Setup for stress test
        stringRedisTemplate.opsForValue().set("seckill:stock:" + TEST_VOUCHER_ID, String.valueOf(stressTestStock));
        
        try {
            SeckillVoucher voucher = seckillVoucherService.getOne(
                seckillVoucherService.lambdaQuery().eq(SeckillVoucher::getVoucherId, TEST_VOUCHER_ID).getWrapper());
            if (voucher != null) {
                voucher.setStock(stressTestStock);
                seckillVoucherService.updateById(voucher);
            }
        } catch (Exception e) {
            // If voucher doesn't exist or other error, continue with test
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(stressTestUsers);
        CountDownLatch latch = new CountDownLatch(stressTestUsers);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 1; i <= stressTestUsers; i++) {
            final long userId = i;
            executor.submit(() -> {
                UserDTO user = createTestUser(userId);
                UserHolder.saveUser(user);
                
                try {
                    Result result = voucherOrderService.seckillVoucher(TEST_VOUCHER_ID);
                    if (result.getSuccess()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Expected under high stress
                } finally {
                    UserHolder.removeUser();
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(60, TimeUnit.SECONDS), "Stress test should complete within 60 seconds");
        executor.shutdown();
        
        waitForAsyncProcessing();
        
        long actualOrders = voucherOrderMapper.selectList(null).stream()
            .mapToLong(order -> TEST_VOUCHER_ID.equals(order.getVoucherId()) ? 1 : 0)
            .sum();
        
        // Under stress, we should still not oversell
        assertTrue(actualOrders <= stressTestStock,
            String.format("Stress test overselling: %d orders created with %d stock", 
                actualOrders, stressTestStock));
        
        System.out.printf("Stress test completed: %d/%d users succeeded, %d orders created%n",
            successCount.get(), stressTestUsers, actualOrders);
    }
    
    private UserDTO createTestUser(Long userId) {
        UserDTO user = new UserDTO();
        user.setId(userId);
        user.setNickName("testuser" + userId);
        user.setIcon("");
        return user;
    }
    
    private void waitForAsyncProcessing() {
        // Wait for async order processing to complete
        try {
            Thread.sleep(2000); // Give time for async processing
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}