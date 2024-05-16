package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * refresh validity period
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
//    @Override
//    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
//                             jakarta.servlet.http.HttpServletResponse response,
//                             Object handler) throws Exception {
//        //        1.获取session
////        获取请求头中的token
//        String token = request.getHeader("authorization");
//        if (StrUtil.isBlank(token)) {
//            return true;
//        }
//        String key = RedisConstants.LOGIN_USER_KEY + token;
////        2.获取session中的用户
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
////        3.判断用户是否存在
////        4.不存在，拦截
//        if (userMap.isEmpty()) {
//            return true;
//        }
////        5.存在，保存用户信息到ThreadLocal
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
////        6.放行
//        UserHolder.saveUser(userDTO);
//        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
//        return true;
//    }

//    @Override
//    public void afterCompletion(jakarta.servlet.http.HttpServletRequest request,
//                                jakarta.servlet.http.HttpServletResponse response,
//                                Object handler, Exception ex) throws Exception {
//        UserHolder.removeUser();
//    }
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
//        1.get session
//        get the token in the request header
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        String key = RedisConstants.LOGIN_USER_KEY + token;
//        2.get the user in the session
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
//        3.determine whether the user exists
//        4.does not exist intercept
        if (userMap.isEmpty()) {
            return true;
        }
//        5.exists save user information to thread local
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//        6.release
        UserHolder.saveUser(userDTO);
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
