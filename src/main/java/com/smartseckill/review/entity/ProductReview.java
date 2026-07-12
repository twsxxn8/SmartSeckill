package com.smartseckill.review.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartseckill.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品评论实体，对应表 {@code product_review}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_review")
public class ProductReview extends BaseEntity {

    private Long spuId;
    private Long skuId;
    private Long userId;
    private String orderSn;
    /** 评分 1-5 */
    private Integer rating;
    /** 评论内容，最多 1000 字 */
    private String content;
}
