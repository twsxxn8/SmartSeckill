package com.smartseckill.common.result;

import lombok.Getter;

/**
 * 全局业务状态码枚举。
 * <p>
 * 编码规则：200=成功；400-500=HTTP 语义错误；1000+ 按模块分段（用户/商品/订单/秒杀/风控等）。
 * </p>
 */
@Getter
public enum ResultCode {

    // ---------- 通用 HTTP 语义 ----------
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证或Token已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 用户相关 1001-1099
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    VERIFICATION_CODE_ERROR(1004, "验证码错误或已过期"),
    TOKEN_EXPIRED(1005, "Token已过期"),
    TOKEN_INVALID(1006, "Token无效"),

    // 商品相关 1101-1199
    PRODUCT_NOT_FOUND(1101, "商品不存在"),
    PRODUCT_OFF_SHELF(1102, "商品已下架"),
    STOCK_NOT_ENOUGH(1103, "库存不足"),

    // 订单相关 1201-1299
    ORDER_NOT_FOUND(1201, "订单不存在"),
    ORDER_STATUS_ERROR(1202, "订单状态异常"),
    ORDER_ALREADY_PAID(1203, "订单已支付"),
    ORDER_TOKEN_INVALID(1204, "下单令牌无效或已过期，请刷新页面重试"),
    ORDER_SUBMITTING(1205, "订单处理中，请稍后到订单列表查看"),

    // 秒杀相关 1301-1399
    SECKILL_NOT_STARTED(1301, "秒杀活动未开始"),
    SECKILL_ALREADY_ENDED(1302, "秒杀活动已结束"),
    SECKILL_SOLD_OUT(1303, "已抢光"),
    SECKILL_REPEAT(1304, "您已参与过该秒杀"),
    SECKILL_QUEUEING(1305, "排队中，请稍后查看订单"),
    SECKILL_RATE_LIMIT(1306, "请求过于频繁，请稍后再试"),

    // 风控相关 1401-1499
    RISK_CONTROL_BLOCKED(1401, "系统检测到异常操作，请稍后再试"),
    RISK_CONTROL_VERIFY(1402, "请完成安全验证"),

    // 通用业务 1501-1599
    CART_EMPTY(1501, "购物车为空"),
    ADDRESS_NOT_FOUND(1502, "地址不存在"),

    // 优惠券相关 1601-1699
    COUPON_NOT_FOUND(1601, "优惠券不存在"),
    COUPON_EXPIRED(1602, "优惠券已过期"),
    COUPON_ALREADY_CLAIMED(1603, "您已领取过该优惠券"),
    COUPON_SOLD_OUT(1604, "优惠券已领完"),
    COUPON_MIN_AMOUNT_NOT_MET(1605, "未满足优惠券使用门槛"),
    COUPON_ALREADY_USED(1606, "优惠券已使用"),
    COUPON_NOT_AVAILABLE(1607, "优惠券不可用"),
    COUPON_NOT_STARTED(1608, "优惠券抢购未开始"),

    // 评论相关 1701-1799
    REVIEW_ALREADY_EXISTS(1701, "您已对该商品发表过评论"),
    REVIEW_NOT_PURCHASED(1702, "只有购买过该商品的用户才能评论"),
    REVIEW_ORDER_NOT_COMPLETED(1703, "订单未完成，无法评论");

    /** 业务状态码 */
    private final int code;

    /** 默认错误提示信息 */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
