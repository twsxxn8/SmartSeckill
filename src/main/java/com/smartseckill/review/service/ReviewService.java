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
 * 商品评论服务。
 * <p>
 * 核心规则：
 * <ul>
 *   <li>只有已购买该商品且订单状态为"已完成"的用户才能评论</li>
 *   <li>同一用户对同一订单的同一 SKU 只能评论一次</li>
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
     * 发表评论。
     */
    @Transactional
    public void createReview(Long userId, CreateReviewRequest request) {
        // 1. 校验订单归属
        OrderInfo order = orderInfoMapper.selectOne(
                new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getOrderSn, request.getOrderSn())
                        .eq(OrderInfo::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ResultCode.REVIEW_NOT_PURCHASED);
        }

        // 2. 校验订单状态（已完成或已收货才能评论）
        if (order.getStatus() < 2) {
            throw new BusinessException(ResultCode.REVIEW_ORDER_NOT_COMPLETED);
        }

        // 3. 校验订单中是否包含该 SKU
        Long itemCount = orderItemMapper.selectCount(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderSn, request.getOrderSn())
                        .eq(OrderItem::getSkuId, request.getSkuId()));
        if (itemCount == 0) {
            throw new BusinessException(ResultCode.REVIEW_NOT_PURCHASED);
        }

        // 4. 检查是否已评论过（同一用户 + 同一订单 + 同一 SKU）
        int existingCount = reviewMapper.countByUserAndOrder(
                userId, request.getOrderSn(), request.getSkuId());
        if (existingCount > 0) {
            throw new BusinessException(ResultCode.REVIEW_ALREADY_EXISTS);
        }

        // 5. 插入评论
        ProductReview review = new ProductReview();
        review.setSpuId(request.getSpuId());
        review.setSkuId(request.getSkuId());
        review.setUserId(userId);
        review.setOrderSn(request.getOrderSn());
        review.setRating(request.getRating());
        review.setContent(request.getContent());
        reviewMapper.insert(review);

        log.info("用户 {} 对 SPU {} SKU {} 发表 {}星评论", userId, request.getSpuId(), request.getSkuId(), request.getRating());
    }

    /**
     * 获取某 SPU 的评论列表（含评分聚合）。
     */
    public ReviewListResponse getSpuReviews(Long spuId, int page, int size) {
        // 1. 总数
        Long total = reviewMapper.selectCount(
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getSpuId, spuId)
                        .eq(ProductReview::getDeleted, 0));

        // 2. 分页列表
        int offset = (page - 1) * size;
        List<ReviewListResponse.ReviewItem> records = reviewMapper.selectReviewPage(spuId, offset, size);

        // 3. 平均评分
        Double avgRating = reviewMapper.selectAvgRatingBySpuId(spuId);
        if (avgRating == null) avgRating = 0.0;

        // 4. 评分分布
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
        resp.setAvgRating(Math.round(avgRating * 10.0) / 10.0); // 保留1位小数
        resp.setRatingDistribution(ratingDist);
        resp.setRecords(records != null ? records : Collections.emptyList());

        return resp;
    }
}
