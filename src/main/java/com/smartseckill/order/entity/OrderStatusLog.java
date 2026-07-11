package com.smartseckill.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态变更日志，对应表 {@code order_status_log}。
 * <p>记录每次状态流转（谁、从哪到哪、原因），便于审计和客服查询。</p>
 */
@Data
@TableName("order_status_log")
public class OrderStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String orderSn;
    private Integer fromStatus;
    private Integer toStatus;
    private String operator;
    private String remark;
    private LocalDateTime createTime;
}
