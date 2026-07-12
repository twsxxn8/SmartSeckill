package com.smartseckill.review.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 商品评论列表响应（含评分聚合）。
 */
@Data
public class ReviewListResponse {

    private long total;
    private long page;
    private long size;
    private double avgRating;
    /** 评分分布：key=星级(1-5), value=数量 */
    private Map<Integer, Long> ratingDistribution;
    private List<ReviewItem> records;

    @Data
    public static class ReviewItem {
        private Long id;
        private Long skuId;
        private Long userId;
        private Integer rating;
        private String content;
        private LocalDateTime createTime;
        /** 评论者昵称（联表 sys_user） */
        private String nickname;
        /** 评论者头像（联表 sys_user） */
        private String avatar;
    }
}
