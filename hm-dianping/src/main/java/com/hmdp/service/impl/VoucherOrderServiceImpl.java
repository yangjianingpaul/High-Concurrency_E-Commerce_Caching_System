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
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
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
     * 3.redis stream流消息队列实现异步秒杀
     *
     * @param voucherId
     * @return
     */

//    redis消息队列创建命令：XGROUP CREATE stream.orders g1 0 MKSTREAM
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
//                获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.order
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
//                    判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
//                        如果获取失败，说明没有消息，继续下一次循环
                        continue;
                    }
//                    解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
//                创建订单
                    handleVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }

            }
        }

        private void handlePendingList() {
            while (true) {
                try {
//                获取消息队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
//                    判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
//                        如果获取失败，说明没有消息，继续下一次循环
                        break;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
//                创建订单
                    handleVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理pendingList订单异常", e);
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
//        获取锁
            boolean isLock = lock.tryLock();
            if (!isLock) {
//            获取锁失败，返回错误重试
                log.error("不允许重复下单");
                return;
            }
            try {
//            获取代理对象（事务）
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
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );

        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
//        获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 3.返回订单id
        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
//        5.扣件库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock-1")  //set stock = stock - 1
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0) //where id = ? and stock > 0
                .update();
        if (!success) {
            log.error("库存不足！");
            return;
        }
//        创建订单
        save(voucherOrder);
    }


/*********************************************************************************/
    /**
     *
     * 2.阻塞队列异步处理下单
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
////                获取队列中的订单信息
//                try {
//                    VoucherOrder voucherOrder = orderTasks.take();
////                创建订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                }
//            }
//        }
//    }
//
//    @Override
//    public Result seckillVoucher(Long voucherId) {
////        获取用户
//        Long userId = UserHolder.getUser().getId();
////        执行lua脚本
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(),
//                userId.toString()
//        );
////        判断结果是0
//        int r = result.intValue();
//        if (r != 0) {
//            //        不为0，代表没有购买资格
//            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
//        }
//
////        为0，有购买资格，把下单信息保存到阻塞队列
//        VoucherOrder voucherOrder = new VoucherOrder();
////        6.创建订单
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
////        放入阻塞队列
//        orderTasks.add(voucherOrder);
////        获取代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
////        返回订单id
//        return Result.ok(orderId);
//    }
//
//    private void handleVoucherOrder(VoucherOrder voucherOrder) {
////        1。获取用户
//        Long userId = voucherOrder.getUserId();
////        2。创建锁对象
//        RLock lock = redissonClient.getLock(LOCK_ORDER_KEY + userId);
////        3。获取锁
//        boolean isLock = lock.tryLock();
////        4。判断是否获取成功
//        if (!isLock) {
////        获取锁失败，返回错误重试
//            log.error("不允许重复下单");
//            return;
//        }
//        try {
////            获取代理对象（事务）
//            proxy.createVoucherOrder(voucherOrder);
//        } finally {
//            lock.unlock();
//        }
//    }
//
//    @Transactional
//    public void createVoucherOrder(VoucherOrder voucherOrder) {
////        5.扣件库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock-1")  //set stock = stock - 1
//                .eq("voucher_id", voucherOrder.getVoucherId())
//                .gt("stock", 0) //where id = ? and stock > 0
//                .update();
//        if (!success) {
//            log.error("库存不足！");
//            return;
//        }
////        创建订单
//        save(voucherOrder);
//    }


/*********************************************************************************/
    /**
     * 1.一人一单
     * 基于redis分布式锁
     * 优点：解决分布式情况下并发问题
     * 缺点：不可重入，不可重试，超时释放问题，主从一致性
     * <p>
     * redisson:redis分布式工具集合
     *
     * @param voucherId
     * @return
     */

//    @Resource
//    private RedissonClient redissonClient;
//
//    @Override
//    public Result seckillVoucher(Long voucherId) {
////        1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
////        2.判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始！");
//        }
////        3.判断藐视是否已经结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
////        4.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足！");
//        }
//        return createVoucherOrder(voucherId);
//    }

    /**
     * 1.解决一人一单/分布式超卖问题
     * 基于redis分布式锁
     * 创建优惠券订单
     *
     * @param voucherId
     * @return
     */
//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
////        1。互斥锁避免分布式超卖问题
///*******************************************************************************************/
//////        a)simpleRedisLock创建分布式锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//////        a)simpleRedisLock获取锁
////        boolean isLock = lock.tryLock(1200);
///******************************************************************************************/
////        b)redisson创建分布式锁对象
//        RLock lock = redissonClient.getLock(LOCK_ORDER_KEY + userId);
////        b)redissonClient获取锁
//        boolean isLock = lock.tryLock();
///******************************************************************************************/
//
//        if (!isLock) {
////            获取锁失败，返回错误重试
//            return Result.fail("不允许重复下单");
//        }
//
//        try {
////        2。判断"一人一单"
//            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//            if (count > 0) {
//                return Result.fail("用户已经购买过一次！");
//            }
////        3。扣件库存
//            boolean success = seckillVoucherService.update()
//                    .setSql("stock = stock-1")  //set stock = stock - 1
//                    .eq("voucher_id", voucherId)
//                    .gt("stock", 0) //where id = ? and stock > 0
//                    .update();
//
//            if (!success) {
//                return Result.fail("库存不足！");
//            }
//            VoucherOrder voucherOrder = new VoucherOrder();
//            long orderId = redisIdWorker.nextId("order");
////        4。创建订单
//            voucherOrder.setId(orderId);
//            voucherOrder.setUserId(userId);
//            voucherOrder.setVoucherId(voucherId);
//            save(voucherOrder);
////        5。返回订单id
//            return Result.ok(orderId);
//
//        } finally {
//            lock.unlock();
//        }
//    }
}
