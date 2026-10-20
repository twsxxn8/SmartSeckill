package com.smartseckill.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartseckill.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表实体，对应表 {@code order_info}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_info")
public class OrderInfo extends BaseEntity {

    /** 订单编号（业务唯一标识，非数据库主键） */
    private String orderSn;
    /** 一次性下单令牌（幂等防重，order_info.submit_token 过滤唯一索引兜底，秒杀订单为 NULL） */
    private String submitToken;
    private Long userId;
    /** 商品总金额（未扣优惠券） */
    private BigDecimal totalAmount;
    /** 实际应付金额 */
    private BigDecimal payAmount;
    private BigDecimal freightAmount;
    private Long couponId;
    private BigDecimal couponAmount;
    /** 订单状态：0=待支付, 1=已支付, 2=已发货, 3=已完成, 4=已取消 */
    private Integer status;
    /** 订单类型：0=普通订单, 1=秒杀订单 */
    private Integer orderType;
    /** 收货信息快照（下单时复制，不受地址后续修改影响） */
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private LocalDateTime paymentTime;
    private LocalDateTime deliveryTime;
    private LocalDateTime receiveTime;
    private LocalDateTime cancelTime;
    private String cancelReason;
    private String remark;
}
