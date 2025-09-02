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
@TableName("tb_shop")
public class Shop implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * store name
     */
    private String name;

    /**
     * id of store type
     */
    private Long typeId;

    /**
     * store pictures multiple pictures separated by
     */
    private String images;

    /**
     * business districts such as lujiazui
     */
    private String area;

    /**
     * address
     */
    private String address;

    /**
     * longitude
     */
    private Double x;

    /**
     * dimensions
     */
    private Double y;

    /**
     * average price rounded to an integer
     */
    private Long avgPrice;

    /**
     * sales volume
     */
    private Integer sold;

    /**
     * number of comments
     */
    private Integer comments;

    /**
     * Rating, 1~5 points, multiply by 10 to save, avoid decimals
     */
    private Integer score;

    /**
     * business hours for example 10 00 22 00
     */
    private String openHours;

    /**
     * creation time
     */
    private LocalDateTime createTime;

    /**
     * update time
     */
    private LocalDateTime updateTime;


    @TableField(exist = false)
    private Double distance;
}
