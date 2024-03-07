package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

/**
 * front controller
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * Send the phone verification code
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // TODO send an sms verification code and save it
        return userService.sendCode(phone, session);
//        return Result.fail("功能未完成");
    }

    /**
     * Sign-in function
     *
     * @param loginForm Login parameters, including mobile phone number, verification code, or mobile phone number and password
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {
        // TODO implement the login function
        return userService.login(loginForm, session);
    }

    /**
     * Logout function
     *
     * @return null
     */
    @PostMapping("/logout")
    public Result logout() {
        // TODO Implement the logout function
        return Result.fail("the function is not complete");
    }

    @GetMapping("/me")
    public Result me() {
        // TODO get the currently logged in user and return
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        // find out more
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // There are no details, it should be the first time to check the details
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // return
        return Result.ok(info);
    }

    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    @PostMapping("sign")
    public Result sign() {
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount() {
        return userService.signCount();
    }
}
