package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * mobile phone number
     */
    private String phone;

    /**
     * passwords encrypted storage
     */
    private String password;

    /**
     * nickname which is a random character by default
     */
    private String nickName;

    /**
     * user avatar
     */
    private String icon = "";

    /**
     * creation time
     */
    private LocalDateTime createTime;

    /**
     * Updated
     */
    private LocalDateTime updateTime;


}
