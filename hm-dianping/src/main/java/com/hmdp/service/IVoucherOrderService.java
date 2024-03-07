package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * service class
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    /**
     *
     * 1.one person one order
     * based on redis distributed lock
     *
     * @param voucherId
     * @return
     */
//    Result createVoucherOrder(Long voucherId);


    /**
     * 2.blocking queue processes orders asynchronously/3.Redis stream message queue implements asynchronous flash killing
     *
     * @param voucherOrder
     */
    void createVoucherOrder(VoucherOrder voucherOrder);
}
