package com.paulyang.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paulyang.ecommerce.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * mapper
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}
