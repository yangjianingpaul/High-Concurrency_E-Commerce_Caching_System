package com.paulyang.ecommerce.service;

import com.paulyang.ecommerce.dto.Result;
import com.paulyang.ecommerce.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * service class
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, String sortBy, Double x, Double y);
}
