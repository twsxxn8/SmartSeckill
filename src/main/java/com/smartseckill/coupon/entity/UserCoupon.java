package com.smartseckill.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户优惠券关联实体，对应表 {@code user_coupon}。
 * <p>不继承 BaseEntity，因为表结构不含逻辑删除字段 deleted。</p>
 */
@Data
@TableName("user_coupon")
public class UserCoupon {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long couponId;
    /** 0=未使用, 1=已使用, 2=已过期 */
    private Integer status;
    private LocalDateTime usedTime;
    private Long orderId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
