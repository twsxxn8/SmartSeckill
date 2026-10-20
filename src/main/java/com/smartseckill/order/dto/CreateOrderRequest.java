package com.smartseckill.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建订单请求 DTO。
 * <p>Controller 层用 {@code @Valid} 校验，校验失败由 GlobalExceptionHandler 统一处理。</p>
 */
@Data
@Schema(description = "创建订单请求")
public class CreateOrderRequest {

    @NotNull(message = "地址ID不能为空")
    @Schema(description = "收货地址ID")
    private Long addressId;

    @NotBlank(message = "下单令牌不能为空")
    @Schema(description = "一次性下单令牌（POST /order/token 签发，防重复提交）")
    private String submitToken;

    @NotEmpty(message = "订单项不能为空")
    @Schema(description = "订单项列表")
    private List<OrderItemRequest> items;

    @Schema(description = "优惠券ID")
    private Long couponId;

    @Schema(description = "备注")
    private String remark;

    /** 单个订单项（一个 SKU + 数量） */
    @Data
    public static class OrderItemRequest {
        @NotNull(message = "SKU ID不能为空")
        private Long skuId;

        @NotNull(message = "SPU ID不能为空")
        private Long spuId;

        @NotNull(message = "数量不能为空")
        private Integer quantity;
    }
}
