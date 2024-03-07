package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * front controller
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * query store information based on id
     *
     * @param id store id
     * @return store details data
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    /**
     * add store information
     *
     * @param shop store data
     * @return store id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // write to database
        shopService.save(shop);
        // return store id
        return Result.ok(shop.getId());
    }

    /**
     * update store information
     *
     * @param shop store data
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        // write to database
        return shopService.update(shop);
    }

    /**
     * Query store information by page according to store type
     *
     * @param typeId  shop type
     * @param current page number
     * @return store list
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
        return shopService.queryShopByType(typeId, current, sortBy, x, y);
    }

    /**
     * Query store information by page based on store name keywords
     *
     * @param name    store name keywords
     * @param current page number
     * @return store list
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // paging query based on type
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // return data
        return Result.ok(page.getRecords());
    }
}
