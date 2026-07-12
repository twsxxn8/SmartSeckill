package com.smartseckill.review.dto;

import lombok.Data;

import javax.validation.constraints.*;

/**
 * 发表评论/回复请求。
 * <p>
 * 发表顶层评论：spuId/skuId/orderSn/rating/content 必填，parentId 不传<br>
 * 发表回复：spuId/skuId/content 必填，parentId 指定被回复评论ID，rating/orderSn 为空
 * </p>
 */
@Data
public class CreateReviewRequest {

    private Long spuId;

    private Long skuId;

    /** 订单号，顶层评论必填，回复可为空 */
    private String orderSn;

    /** 被回复评论的ID，顶层评论不传或传0 */
    private Long parentId;

    /** 评分 1-5，顶层评论必填，回复可为空 */
    @Min(value = 1, message = "评分最低1星")
    @Max(value = 5, message = "评分最高5星")
    private Integer rating;

    @Size(max = 1000, message = "评论内容不能超过1000字")
    private String content;
}
