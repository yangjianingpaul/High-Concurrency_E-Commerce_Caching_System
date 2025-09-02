package com.paulyang.ecommerce.controller;


import cn.hutool.core.bean.BeanUtil;
import com.paulyang.ecommerce.dto.LoginFormDTO;
import com.paulyang.ecommerce.dto.Result;
import com.paulyang.ecommerce.dto.UserDTO;
import com.paulyang.ecommerce.entity.User;
import com.paulyang.ecommerce.entity.UserInfo;
import com.paulyang.ecommerce.service.IUserInfoService;
import com.paulyang.ecommerce.service.IUserService;
import com.paulyang.ecommerce.utils.UserHolder;
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
//        return Result.fail("Feature not implemented");
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
