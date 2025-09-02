package com.paulyang.ecommerce.utils;

import cn.hutool.core.util.StrUtil;

public class RegexUtils {
    /**
     * whether it is an invalid phone format
     * @param phone the mobile phone number to be verified
     * @return true:accord-with，false：not true
     */
    public static boolean isPhoneInvalid(String phone){
        return mismatch(phone, RegexPatterns.PHONE_REGEX);
    }
    /**
     * whether it is an invalid mailbox format
     * @param email the mailbox to be verified
     * @return true:accord-with，false：not true
     */
    public static boolean isEmailInvalid(String email){
        return mismatch(email, RegexPatterns.EMAIL_REGEX);
    }

    /**
     * whether it is an invalid captcha format
     * @param code the verification code to be verified
     * @return true:accord-with，false：not true
     */
    public static boolean isCodeInvalid(String code){
        return mismatch(code, RegexPatterns.VERIFY_CODE_REGEX);
    }

    // Check whether it does not conform to the regular format
    private static boolean mismatch(String str, String regex){
        if (StrUtil.isBlank(str)) {
            return true;
        }
        return !str.matches(regex);
    }
}
