package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.hmdp.utils.RedisConstants.LOCK_ORDER_KEY;

/**
 * service’s implement class
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

/*********************************************************************************/
    /**
     * 3.redis Stream message queue implements asynchronous flash killing
     *
     * @param voucherId
     * @return
     */

//    redis message queue creation command：XGROUP CREATE stream.orders g1 0 MKSTREAM
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private IVoucherOrderService proxy;
    @Resource
    private RedissonClient redissonClient;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {
                try {
//                get order information from the message queue XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.order
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
//                    Determine whether the message acquisition is successful
                    if (list == null || list.isEmpty()) {
//                        If the acquisition fails, it means there is no message, and the next cycle continues.
                        continue;
                    }
//                    parse the order information in the message
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
//                create order
                    handleVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("Handling order exceptions", e);
                    handlePendingList();
                }

            }
        }

        private void handlePendingList() {
            while (true) {
                try {
//                get order information from the message queue
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
//                    Determine whether the message acquisition is successful
                    if (list == null || list.isEmpty()) {
//                        If the acquisition fails, it means there is no message, and the next cycle continues.
                        break;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
//                create order
                    handleVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("handle pendingList order exception", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }

            }
        }

        private void handleVoucherOrder(VoucherOrder voucherOrder) {
            Long userId = voucherOrder.getUserId();
            RLock lock = redissonClient.getLock(LOCK_ORDER_KEY + userId);
//        get the lock
            boolean isLock = lock.tryLock();
            if (!isLock) {
//            Failed to acquire the lock, and an error is returned to try again
                log.error("duplicate orders are not allowed");
                return;
            }
            try {
//            get the proxy object（transaction）
                proxy.createVoucherOrder(voucherOrder);
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        // 1.execute the lua script
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );

        int r = result.intValue();
        // 2.check whether the result is 0
        if (r != 0) {
            // 2.1.If it is not 0, it means that you are not eligible to purchase
            return Result.fail(r == 1 ? "Insufficient inventory" : "Duplicate orders cannot be placed");
        }
//        get the proxy object
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 3.return the order id
        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
//        5.deduction of inventory
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock-1")  //set stock = stock - 1
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0) //where id = ? and stock > 0
                .update();
        if (!success) {
            log.error("Insufficient inventory！");
            return;
        }
//        create an order
        save(voucherOrder);
    }


/*********************************************************************************/
    /**
     *
     * 2.The blocking queue processes the order asynchronously
     *
     * @param voucherId
     * @return
     */

//    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
//    @Resource
//    private RedissonClient redissonClient;
//    private IVoucherOrderService proxy;
//
//    static {
//        SECKILL_SCRIPT = new DefaultRedisScript<>();
//        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
//        SECKILL_SCRIPT.setResultType(Long.class);
//    }
//
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
//    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
//
//    @PostConstruct
//    private void init() {
//        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
//    }
//
//    private class VoucherOrderHandler implements Runnable {
//        @Override
//        public void run() {
//            while (true) {
////                get order information in the queue
//                try {
//                    VoucherOrder voucherOrder = orderTasks.take();
////                create order
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("handling order exceptions", e);
//                }
//            }
//        }
//    }
//
//    @Override
//    public Result seckillVoucher(Long voucherId) {
////        get user
//        Long userId = UserHolder.getUser().getId();
////        execute lua script
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(),
//                userId.toString()
//        );
////        the judgment result is 0
//        int r = result.intValue();
//        if (r != 0) {
//            //        If it is not 0, it means there is no purchase qualification.
//            return Result.fail(r == 1 ? "inventory shortage" : "repeat orders cannot be placed");
//        }
//
////        When it is 0, you are eligible to purchase and save the order information to the blocking queue.
//        VoucherOrder voucherOrder = new VoucherOrder();
////        6.create order
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
////        put in blocking queue
//        orderTasks.add(voucherOrder);
////        get proxy object
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
////        return order id
//        return Result.ok(orderId);
//    }
//
//    private void handleVoucherOrder(VoucherOrder voucherOrder) {
////        1。get user
//        Long userId = voucherOrder.getUserId();
////        2。create lock object
//        RLock lock = redissonClient.getLock(LOCK_ORDER_KEY + userId);
////        3。get lock
//        boolean isLock = lock.tryLock();
////        4。determine whether the acquisition is successful
//        if (!isLock) {
////        failed to acquire lock return error and try again
//            log.error("duplicate orders are not allowed");
//            return;
//        }
//        try {
////            get proxy object transaction
//            proxy.createVoucherOrder(voucherOrder);
//        } finally {
//            lock.unlock();
//        }
//    }
//
//    @Transactional
//    public void createVoucherOrder(VoucherOrder voucherOrder) {
////        5.deduction inventory
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock-1")  //set stock = stock - 1
//                .eq("voucher_id", voucherOrder.getVoucherId())
//                .gt("stock", 0) //where id = ? and stock > 0
//                .update();
//        if (!success) {
//            log.error("inventory shortage！");
//            return;
//        }
////        create order
//        save(voucherOrder);
//    }


/*********************************************************************************/
    /**
     * 1.one order per person/redission
     * based on redis distributed locks
     * Advantages: Solve the problem of concurrency in a distributed situation
     * Disadvantages: no reentrant, no retry, timeout release problem, master-slave consistency
     * <p>
     * redisson:redis distributed tool collection
     *
     * @param voucherId
     * @return
     */

//    @Resource
//    private RedissonClient redissonClient;
//
//    @Override
//    public Result seckillVoucher(Long voucherId) {
////        1.check coupons
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
////        2.determine whether the flash sale has started
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("flash sale has not started yet！");
//        }
////        3.determine whether the flash sale has ended
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("flash sale has ended");
//        }
////        4.determine whether inventory is sufficient
//        if (voucher.getStock() < 1) {
//            return Result.fail("inventory shortage！");
//        }
//
//        return createVoucherOrder(voucherId);
//    }
//
//    /**
//     * 1. Solve the problem of one person, one order/distributed overselling
//     * based on redis distributed locks
//     * create a coupon order
//     *
//     * @param voucherId
//     * @return
//     */
//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
////        1。mutex locks avoid distributed oversold problems
///*******************************************************************************************/
//////        a)simpleRedisLock creates a distributed lock object
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//////        a)simpleRedisLock acquires the lock
////        boolean isLock = lock.tryLock(1200);
///******************************************************************************************/
////        b)redisson creates distributed lock object
//        RLock lock = redissonClient.getLock(LOCK_ORDER_KEY + userId);
////        b)redisson client acquires lock
//        boolean isLock = lock.tryLock();
///******************************************************************************************/
//
//        if (!isLock) {
////            failed to acquire lock return error and try again
//            return Result.fail("duplicate orders are not allowed");
//        }
//
//        try {
////        2。determine one person one order
//            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//            if (count > 0) {
//                return Result.fail("the user has purchased once！");
//            }
////        3。reduce inventory/Solve the oversold problem
//            boolean success = seckillVoucherService.update()
//                    .setSql("stock = stock-1")  //set stock = stock - 1
//                    .eq("voucher_id", voucherId)
//                    .gt("stock", 0) //where id = ? and stock > 0
//                    .update();
//
//            if (!success) {
//                return Result.fail("inventory shortage！");
//            }
//
//            VoucherOrder voucherOrder = new VoucherOrder();
//            long orderId = redisIdWorker.nextId("order");
////        4。create order
//            voucherOrder.setId(orderId);
//            voucherOrder.setUserId(userId);
//            voucherOrder.setVoucherId(voucherId);
//            save(voucherOrder);
////        5。return order id
//            return Result.ok(orderId);
//
//        } finally {
//            lock.unlock();
//        }
//    }
}
