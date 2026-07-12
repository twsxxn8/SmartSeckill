package com.smartseckill.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartseckill.common.exception.BusinessException;
import com.smartseckill.common.result.ResultCode;
import com.smartseckill.order.entity.OrderInfo;
import com.smartseckill.order.entity.OrderItem;
import com.smartseckill.order.mapper.OrderInfoMapper;
import com.smartseckill.order.mapper.OrderItemMapper;
import com.smartseckill.review.dto.CreateReviewRequest;
import com.smartseckill.review.dto.ReviewListResponse;
import com.smartseckill.review.entity.ProductReview;
import com.smartseckill.review.mapper.ProductReviewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 商品评论服务（支持楼中楼回复）。
 * <p>
 * 核心规则：
 * <ul>
 *   <li>顶层评论：只有已购买且订单"已完成"的用户才能发表，需要评分</li>
 *   <li>回复：任何登录用户都可以回复，不需要评分和订单</li>
 *   <li>同一用户对同一订单的同一 SKU 只能发表一次顶层评论</li>
 *   <li>评论列表对所有人公开可见</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ProductReviewMapper reviewMapper;
    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;

    /**
     * 发表评论或回复。
     * <ul>
     *   <li>parentId == null or 0 → 顶层评论，需校验订单归属 + 评分</li>
     *   <li>parentId > 0 → 回复，需校验父评论存在，不需要订单和评分</li>
     * </ul>
     */
    @Transactional
    public void createReview(Long userId, CreateReviewRequest request) {
        Long parentId = request.getParentId() != null ? request.getParentId() : 0L;

        if (parentId == 0) {
            // ===== 顶层评论 =====
            if (request.getOrderSn() == null || request.getOrderSn().isBlank()) {
                throw new BusinessException(400, "顶层评论需要提供订单号");
            }
            if (request.getRating() == null || request.getRating() < 1) {
                throw new BusinessException(400, "顶层评论需要评分");
            }

            // 校验订单归属
            OrderInfo order = orderInfoMapper.selectOne(
                    new LambdaQueryWrapper<OrderInfo>()
                            .eq(OrderInfo::getOrderSn, request.getOrderSn())
                            .eq(OrderInfo::getUserId, userId));
            if (order == null) {
                throw new BusinessException(ResultCode.REVIEW_NOT_PURCHASED);
            }
            if (order.getStatus() < 2) {
                throw new BusinessException(ResultCode.REVIEW_ORDER_NOT_COMPLETED);
            }

            // 校验订单中是否包含该 SKU
            Long itemCount = orderItemMapper.selectCount(
                    new LambdaQueryWrapper<OrderItem>()
                            .eq(OrderItem::getOrderSn, request.getOrderSn())
                            .eq(OrderItem::getSkuId, request.getSkuId()));
            if (itemCount == 0) {
                throw new BusinessException(ResultCode.REVIEW_NOT_PURCHASED);
            }

            // 检查是否已发表过顶层评论
            int existing = reviewMapper.countByUserAndOrder(
                    userId, request.getOrderSn(), request.getSkuId());
            if (existing > 0) {
                throw new BusinessException(ResultCode.REVIEW_ALREADY_EXISTS);
            }

            // 插入顶层评论
            ProductReview review = new ProductReview();
            review.setSpuId(request.getSpuId());
            review.setSkuId(request.getSkuId());
            review.setUserId(userId);
            review.setOrderSn(request.getOrderSn());
            review.setParentId(0L);
            review.setRootId(0L);
            review.setRating(request.getRating());
            review.setContent(request.getContent());
            reviewMapper.insert(review);

            log.info("用户 {} 对 SPU {} SKU {} 发表顶层评论 {}星 ID={}", userId, request.getSpuId(), request.getSkuId(), request.getRating(), review.getId());

        } else {
            // ===== 回复 =====
            // 校验父评论存在且是顶层评论
            ProductReview parent = reviewMapper.selectById(parentId);
            if (parent == null || parent.getDeleted() == 1) {
                throw new BusinessException(ResultCode.NOT_FOUND);
            }

            // 确定 root_id：父评论是顶层评论时用父评论ID，否则沿用父评论的root_id
            Long rootId = parent.getRootId() == 0 ? parent.getId() : parent.getRootId();

            // 插入回复
            ProductReview reply = new ProductReview();
            reply.setSpuId(request.getSpuId());
            reply.setSkuId(request.getSkuId() != null ? request.getSkuId() : parent.getSkuId());
            reply.setUserId(userId);
            reply.setParentId(parentId);
            reply.setRootId(rootId);
            reply.setRating(null);
            reply.setContent(request.getContent());
            reviewMapper.insert(reply);

            log.info("用户 {} 回复评论 ID={}，回复ID={}", userId, parentId, reply.getId());
        }
    }

    /**
     * 获取某 SPU 的评论列表（含树形回复）。
     */
    public ReviewListResponse getSpuReviews(Long spuId, int page, int size) {
        // 1. 顶层评论总数
        Long total = reviewMapper.countRootReviews(spuId);

        // 2. 分页查询顶层评论
        int offset = (page - 1) * size;
        List<ReviewListResponse.ReviewItem> records = reviewMapper.selectRootReviewPage(spuId, offset, size);

        // 3. 为每条顶层评论加载回复
        if (records != null) {
            for (ReviewListResponse.ReviewItem item : records) {
                List<ReviewListResponse.ReplyItem> replies = reviewMapper.selectRepliesByRootId(item.getId());
                item.setReplies(replies != null ? replies : Collections.emptyList());
                item.setReplyCount(item.getReplies().size());
            }
        }

        // 4. 平均评分（仅顶层评论）
        Double avgRating = reviewMapper.selectAvgRatingBySpuId(spuId);
        if (avgRating == null) avgRating = 0.0;

        // 5. 评分分布（仅顶层评论）
        List<Map<String, Object>> dist = reviewMapper.selectRatingDistribution(spuId);
        Map<Integer, Long> ratingDist = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) ratingDist.put(i, 0L);
        for (Map<String, Object> row : dist) {
            Integer r = ((Number) row.get("rating")).intValue();
            Long c = ((Number) row.get("cnt")).longValue();
            ratingDist.put(r, c);
        }

        ReviewListResponse resp = new ReviewListResponse();
        resp.setTotal(total);
        resp.setPage(page);
        resp.setSize(size);
        resp.setAvgRating(Math.round(avgRating * 10.0) / 10.0);
        resp.setRatingDistribution(ratingDist);
        resp.setRecords(records != null ? records : Collections.emptyList());

        return resp;
    }
}
