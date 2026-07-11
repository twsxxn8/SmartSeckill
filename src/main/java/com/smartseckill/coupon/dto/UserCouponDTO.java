package com.smartseckill.coupon.dto;

import com.smartseckill.coupon.entity.Coupon;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户优惠券视图对象，在 {@link Coupon} 基础上附加用户持有状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserCouponDTO extends Coupon {
    /** 用户持有状态：0=未使用, 1=已使用, 2=已过期 */
    private Integer userStatus;
}
