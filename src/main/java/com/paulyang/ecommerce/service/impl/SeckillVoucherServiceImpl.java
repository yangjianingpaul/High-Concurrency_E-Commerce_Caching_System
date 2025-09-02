package com.paulyang.ecommerce.service.impl;

import com.paulyang.ecommerce.entity.SeckillVoucher;
import com.paulyang.ecommerce.mapper.SeckillVoucherMapper;
import com.paulyang.ecommerce.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * Flash sale coupon table, one-to-one relationship with coupons, service implementation category
 * </p>
 *
 * @author yang
 * @since 2022-01-04
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

}
