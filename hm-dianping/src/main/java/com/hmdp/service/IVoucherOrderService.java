package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    /**
     *
     * 1.一人一单
     * 基于redis分布式锁
     *
     * @param voucherId
     * @return
     */
//    Result createVoucherOrder(Long voucherId);


    /**
     * 2.阻塞式队列异步处理订单/3.redis stream流消息队列实现异步秒杀
     *
     * @param voucherOrder
     */
    void createVoucherOrder(VoucherOrder voucherOrder);
}
