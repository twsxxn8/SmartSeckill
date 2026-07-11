package com.smartseckill.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "购物车项")
public class CartItemDTO {

    @NotNull(message = "SKU ID不能为空")
    @Schema(description = "SKU ID")
    private Long skuId;

    @NotNull(message = "SPU ID不能为空")
    @Schema(description = "SPU ID")
    private Long spuId;

    @Schema(description = "商品名称")
    private String skuName;

    @Schema(description = "商品图片")
    private String skuImage;

    @Schema(description = "规格信息")
    private String specInfo;

    @Schema(description = "单价")
    private java.math.BigDecimal price;

    @Min(value = 1, message = "数量最小为1")
    @Schema(description = "数量")
    private Integer quantity;
}
