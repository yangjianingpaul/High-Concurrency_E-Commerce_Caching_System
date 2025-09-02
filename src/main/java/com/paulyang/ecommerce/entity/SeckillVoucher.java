package com.paulyang.ecommerce.entity;

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
@TableName("tb_seckill_voucher")
public class SeckillVoucher implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * the id of the associated coupon
     */
    @TableId(value = "voucher_id", type = IdType.INPUT)
    private Long voucherId;

    /**
     * stock
     */
    private Integer stock;

    /**
     * creation time
     */
    private LocalDateTime createTime;

    /**
     * effective time
     */
    private LocalDateTime beginTime;

    /**
     * expiration time
     */
    private LocalDateTime endTime;

    /**
     * update time
     */
    private LocalDateTime updateTime;


}
