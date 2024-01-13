package com.hmdp.service.impl;

import ch.qos.logback.core.util.TimeUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
//        1。校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
//        2。如果不符合，返回错误信息
//        3。符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
//        4。保存验证码到session
//        session.setAttribute("code", code);
//        4.保存到redis中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        5。发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
//        返回ok
        return Result.ok();
    }

    /**
     * 登陆功能
     *
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        1。校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
//        2。校验验证码
        Object cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        //        3。不一致，报错
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            return Result.fail("验证码错误");
        }
//        4。一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
//        5。判断用户是否存在
//        6。不存在，创建新用户并保存
        if (user == null) {
            createUserWithPhone(phone);
        }
//        7。保存用户信息到session中
//        保存用户信息到redis
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok(token);
    }

    /**
     * 签到功能
     *
     * @return
     */
    @Override
    public Result sign() {
//        获取当前登陆的用户
        Long userId = UserHolder.getUser().getId();
//        获取日期
        LocalDateTime now = LocalDateTime.now();
//        拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = "sign:" + userId + keySuffix;
//        获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
//        写入redis setbit key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth-1,true);
        return Result.ok();
    }

    /**
     * 签到统计
     *
     * @return
     */
    @Override
    public Result signCount() {
//        获取本月今天为止的所有签到记录
        Long userId = UserHolder.getUser().getId();
//        获取日期
        LocalDateTime now = LocalDateTime.now();
//        拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = "sign:" + userId + keySuffix;
//        获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
//        获取本月截至今天为止的所有的签到记录，返回的是一个十进制的数字
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().
                        get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).
                        valueAt(0));

        if (result == null || result.isEmpty()) {
//            没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
//        循环遍历
        int count = 0;
        while (true) {
//        让这个数字与1做与运算，得到数字的最后一个bit位，
//        判断这个bit位是否为0
            if ((num & 1) == 0) {
//                如果为0，说明未签到，结束
                break;
            } else {
//        如果不为0，说明已签到，计数器+1
                count++;
            }
//        把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return null;
    }
}
