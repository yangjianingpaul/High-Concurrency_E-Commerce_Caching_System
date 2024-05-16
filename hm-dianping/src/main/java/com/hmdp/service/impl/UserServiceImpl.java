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
 * service’s implement class
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
//        1.verify the mobile phone number
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("The phone number is in the wrong format！");
        }
//        2.if not an error message is returned
//        3.yes, a verification code will be generated
        String code = RandomUtil.randomNumbers(6);
//        4.save the verification code to session
//        session.setAttribute("code", code);
//        4.save to redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        5。send a verification code
        log.debug("the sms verification code was successfully sent，captcha：{}", code);
//        return ok
        return Result.ok();
    }

    /**
     * login function
     *
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        1。verify the mobile phone number
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("the phone number is in the wrong format");
        }
//        2。verify the verification code
        Object cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        //        3。inconsistencies, error reports
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            return Result.fail("the verification code is incorrect");
        }
//        4。Consistently, query users based on their mobile phone number
        User user = query().eq("phone", phone).one();
//        5。determine whether a user exists
//        6。does not exist create a new user and save
        if (user == null) {
            createUserWithPhone(phone);
        }
//        7。save the user information to the session
//        save the user information to redis
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
     * check in function
     *
     * @return
     */
    @Override
    public Result sign() {
//        get the currently logged in user
        Long userId = UserHolder.getUser().getId();
//        get the date
        LocalDateTime now = LocalDateTime.now();
//        concatenate the key
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = "sign:" + userId + keySuffix;
//        get today is the first day of the month
        int dayOfMonth = now.getDayOfMonth();
//        write in redis setbit key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    /**
     * check in statistics
     *
     * @return
     */
    @Override
    public Result signCount() {
//        Get all the check-in records for the month so far today
        Long userId = UserHolder.getUser().getId();
//        get the date
        LocalDateTime now = LocalDateTime.now();
//        concatenate the key
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = "sign:" + userId + keySuffix;
//        get today is the first day of the month
        int dayOfMonth = now.getDayOfMonth();
//        Get all the check-in records for the month up to today, and return a decimal number
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().
                        get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).
                        valueAt(0));

        if (result == null || result.isEmpty()) {
//            there were no check in results
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
//        loop traversal
        int count = 0;
        while (true) {
//        Let this number be compared with 1 to get the last bit of the number,
//        determine whether the bit is 0
            if ((num & 1) == 0) {
//                If it is 0, it means that you have not checked in and it is over
                break;
            } else {
//        If it is not 0, it means that it is checked in, and the counter is +1
                count++;
            }
//        Shift the number to the right by one bit, discard the last bit, and move on to the next bit
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
