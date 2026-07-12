package com.smartseckill.review.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 商品评论列表响应（含评分聚合 + 树形回复）。
 */
@Data
public class ReviewListResponse {

    private long total;
    private long page;
    private long size;
    private double avgRating;
    /** 评分分布：key=星级(1-5), value=数量 */
    private Map<Integer, Long> ratingDistribution;
    /** 顶层评论列表（每项含嵌套回复） */
    private List<ReviewItem> records;

    @Data
    public static class ReviewItem {
        private Long id;
        private Long skuId;
        private Long userId;
        private Integer rating;
        private String content;
        private LocalDateTime createTime;
        private String nickname;
        private String avatar;
        /** 回复数 */
        private int replyCount;
        /** 子回复列表 */
        private List<ReplyItem> replies;
    }

    @Data
    public static class ReplyItem {
        private Long id;
        private Long parentId;
        private Long rootId;
        private Long userId;
        private String content;
        private LocalDateTime createTime;
        private String nickname;
        private String avatar;
        /** 被回复者的昵称 */
        private String replyToNickname;
    }
}
