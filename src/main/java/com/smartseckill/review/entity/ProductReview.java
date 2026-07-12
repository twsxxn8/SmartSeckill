package com.smartseckill.review.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartseckill.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品评论实体（支持楼中楼回复），对应表 {@code product_review}。
 * <p>
 * 顶层评论：parentId=0, rootId=0, rating 必填<br>
 * 回复：parentId=被回复评论ID, rootId=根评论ID, rating 为空
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_review")
public class ProductReview extends BaseEntity {

    private Long spuId;
    private Long skuId;
    private Long userId;
    private String orderSn;
    /** 被回复评论的ID，0=顶层评论 */
    private Long parentId;
    /** 根评论ID（顶层评论的ID），用于快速查找整棵树 */
    private Long rootId;
    /** 评分 1-5，顶层评论必填，回复可为空 */
    private Integer rating;
    /** 评论内容 */
    private String content;
}
