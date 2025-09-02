package com.paulyang.ecommerce.service;

import com.paulyang.ecommerce.dto.Result;
import com.paulyang.ecommerce.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * service class
 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
