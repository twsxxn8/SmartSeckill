package com.smartseckill.review.controller;

import com.smartseckill.common.result.Result;
import com.smartseckill.review.dto.CreateReviewRequest;
import com.smartseckill.review.dto.ReviewListResponse;
import com.smartseckill.review.service.ReviewService;
import com.smartseckill.user.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 商品评论控制器（支持楼中楼回复）。
 */
@Tag(name = "商品评论")
@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "获取商品评论列表（含评分聚合 + 树形回复）")
    @GetMapping("/spu/{spuId}")
    public Result<ReviewListResponse> getReviews(
            @PathVariable Long spuId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(reviewService.getSpuReviews(spuId, page, size));
    }

    @Operation(summary = "发表评论或回复（登录即可回复，顶层评论需已购买+订单完成）")
    @PostMapping
    public Result<Long> createReview(@AuthenticationPrincipal LoginUser loginUser,
                                     @Valid @RequestBody CreateReviewRequest request) {
        reviewService.createReview(loginUser.getUserId(), request);
        return Result.success(null);
    }
}
