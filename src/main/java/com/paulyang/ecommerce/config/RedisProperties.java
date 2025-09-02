package com.paulyang.ecommerce.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class RedisProperties {
    
    private RedisKeys redisKeys = new RedisKeys();
    private CacheTtl cacheTtl = new CacheTtl();
    
    @Data
    public static class RedisKeys {
        private String loginCode = "login:code:";
        private String loginUser = "login:token:";
        private String cacheShop = "cache:shop:";
        private String cacheShopType = "cache:shopType";
        private String lockShop = "lock:shop:";
        private String lockOrder = "lock:order:";
        private String seckillStock = "seckill:stock:";
        private String blogLiked = "blog:liked:";
        private String feed = "feed:";
        private String shopGeo = "shop:geo:";
        private String userSign = "sign:";
    }
    
    @Data
    public static class CacheTtl {
        private Long loginCode = 2L;
        private Long loginUser = 36000L;
        private Long cacheNull = 2L;
        private Long cacheShop = 30L;
        private Long lockShop = 10L;
    }
}