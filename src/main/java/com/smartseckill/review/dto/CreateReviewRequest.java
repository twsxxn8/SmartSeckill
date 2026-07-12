package com.smartseckill.review.dto;

import lombok.Data;

import javax.validation.constraints.*;

/**
 * 发表评论请求。
 */
@Data
public class CreateReviewRequest {

    @NotNull(message = "商品ID不能为空")
    private Long spuId;

    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    @NotBlank(message = "订单号不能为空")
    private String orderSn;

    @Min(value = 1, message = "评分最低1星")
    @Max(value = 5, message = "评分最高5星")
    private Integer rating;

    @Size(max = 1000, message = "评论内容不能超过1000字")
    private String content;
}
