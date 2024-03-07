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
@TableName("tb_voucher_order")
public class VoucherOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * primary key
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * user id who placed the order
     */
    private Long userId;

    /**
     * voucher id purchased
     */
    private Long voucherId;

    /**
     * payment method 1：balance payment；2：alipay；3：wechat
     */
    private Integer payType;

    /**
     * order status，1：unpaid；2：paid；3：written off；4：cancelled；5：refunding；6：refunded
     */
    private Integer status;

    /**
     * order time
     */
    private LocalDateTime createTime;

    /**
     * payment time
     */
    private LocalDateTime payTime;

    /**
     * write off time
     */
    private LocalDateTime useTime;

    /**
     * refund time
     */
    private LocalDateTime refundTime;

    /**
     * update time
     */
    private LocalDateTime updateTime;


}
