package com.smartseckill.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartseckill.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "coupon", autoResultMap = true)
public class Coupon extends BaseEntity {
    private String couponName;
    /** 1=满减, 2=折扣 */
    private Integer couponType;
    /** 满减金额 */
    private BigDecimal amount;
    /** 折扣比例 (0.85 = 85折) */
    private BigDecimal discount;
    /** 使用门槛金额 */
    private BigDecimal minAmount;
    private Integer totalCount;
    private Integer usedCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
}
