package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_user_info")
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * primary key，user id
     */
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    /**
     * city name
     */
    private String city;

    /**
     * personal introduction no more than 128 characters
     */
    private String introduce;

    /**
     * number of fans
     */
    private Integer fans;

    /**
     * number of people following
     */
    private Integer followee;

    /**
     * gender 0 male 1 female
     */
    private Boolean gender;

    /**
     * birthday
     */
    private LocalDate birthday;

    /**
     * integral
     */
    private Integer credits;

    /**
     * Membership level, 0~9, 0 represents unactivated membership
     */
    private Boolean level;

    /**
     * creation time
     */
    private LocalDateTime createTime;

    /**
     * update time
     */
    private LocalDateTime updateTime;


}
