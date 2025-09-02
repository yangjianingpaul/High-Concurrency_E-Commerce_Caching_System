package com.paulyang.ecommerce.utils;

import com.paulyang.ecommerce.config.RedisProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class RedisConstants {
    
    @Autowired
    private RedisProperties redisProperties;
    
    public static String LOGIN_CODE_KEY;
    public static Long LOGIN_CODE_TTL;
    public static String LOGIN_USER_KEY;
    public static Long LOGIN_USER_TTL;
    
    public static Long CACHE_NULL_TTL;
    public static Long CACHE_SHOP_TTL;
    public static String CACHE_SHOP_KEY;
    public static String CACHE_SHOP_TYPE;
    
    public static String LOCK_SHOP_KEY;
    public static String LOCK_ORDER_KEY;
    public static Long LOCK_SHOP_TTL;
    
    public static String SECKILL_STOCK_KEY;
    public static String BLOG_LIKED_KEY;
    public static String FEED_KEY;
    public static String SHOP_GEO_KEY;
    public static String USER_SIGN_KEY;
    
    @PostConstruct
    public void init() {
        LOGIN_CODE_KEY = redisProperties.getRedisKeys().getLoginCode();
        LOGIN_CODE_TTL = redisProperties.getCacheTtl().getLoginCode();
        LOGIN_USER_KEY = redisProperties.getRedisKeys().getLoginUser();
        LOGIN_USER_TTL = redisProperties.getCacheTtl().getLoginUser();
        
        CACHE_NULL_TTL = redisProperties.getCacheTtl().getCacheNull();
        CACHE_SHOP_TTL = redisProperties.getCacheTtl().getCacheShop();
        CACHE_SHOP_KEY = redisProperties.getRedisKeys().getCacheShop();
        CACHE_SHOP_TYPE = redisProperties.getRedisKeys().getCacheShopType();
        
        LOCK_SHOP_KEY = redisProperties.getRedisKeys().getLockShop();
        LOCK_ORDER_KEY = redisProperties.getRedisKeys().getLockOrder();
        LOCK_SHOP_TTL = redisProperties.getCacheTtl().getLockShop();
        
        SECKILL_STOCK_KEY = redisProperties.getRedisKeys().getSeckillStock();
        BLOG_LIKED_KEY = redisProperties.getRedisKeys().getBlogLiked();
        FEED_KEY = redisProperties.getRedisKeys().getFeed();
        SHOP_GEO_KEY = redisProperties.getRedisKeys().getShopGeo();
        USER_SIGN_KEY = redisProperties.getRedisKeys().getUserSign();
    }
}
