package com.paulyang.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("tb_voucher")
public class Voucher implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * store id
     */
    private Long shopId;

    /**
     * voucher title
     */
    private String title;

    /**
     * subtitle
     */
    private String subTitle;

    /**
     * usage rules
     */
    private String rules;

    /**
     * payment amount
     */
    private Long payValue;

    /**
     * deduction amount
     */
    private Long actualValue;

    /**
     * coupon type
     */
    private Integer type;

    /**
     * coupon type
     */
    private Integer status;
    /**
     * stock
     */
    @TableField(exist = false)
    private Integer stock;

    /**
     * effective time
     */
    @TableField(exist = false)
    private LocalDateTime beginTime;

    /**
     * expiration time
     */
    @TableField(exist = false)
    private LocalDateTime endTime;

    /**
     * creation time
     */
    private LocalDateTime createTime;


    /**
     * update time
     */
    private LocalDateTime updateTime;


}
