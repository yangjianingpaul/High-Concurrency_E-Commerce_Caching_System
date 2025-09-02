package com.paulyang.ecommerce.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.paulyang.ecommerce.dto.Result;
import com.paulyang.ecommerce.entity.Voucher;
import com.paulyang.ecommerce.mapper.VoucherMapper;
import com.paulyang.ecommerce.entity.SeckillVoucher;
import com.paulyang.ecommerce.service.ISeckillVoucherService;
import com.paulyang.ecommerce.service.IVoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

import static com.paulyang.ecommerce.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * service’s implement class
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // query coupon information
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // return results
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // save coupon
        save(voucher);
        // save flash sale information
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);

        //  save flash sale inventory to redis
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(),
                voucher.getStock().toString());
    }
}
