package com.smartseckill.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartseckill.review.dto.ReviewListResponse;
import com.smartseckill.review.entity.ProductReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 商品评论 Mapper。
 */
@Mapper
public interface ProductReviewMapper extends BaseMapper<ProductReview> {

    /** 获取某 SPU 的平均评分 */
    @Select("SELECT ISNULL(AVG(CAST(rating AS FLOAT)), 0) FROM product_review WHERE spu_id = #{spuId} AND deleted = 0")
    Double selectAvgRatingBySpuId(@Param("spuId") Long spuId);

    /** 获取某 SPU 的评分分布（每个星级的评论数） */
    @Select("SELECT rating, COUNT(1) AS cnt FROM product_review WHERE spu_id = #{spuId} AND deleted = 0 GROUP BY rating")
    List<Map<String, Object>> selectRatingDistribution(@Param("spuId") Long spuId);

    /** 检查用户是否已对特定订单中的特定 SKU 发表过评论 */
    @Select("SELECT COUNT(1) FROM product_review WHERE user_id = #{userId} AND order_sn = #{orderSn} AND sku_id = #{skuId} AND deleted = 0")
    int countByUserAndOrder(@Param("userId") Long userId, @Param("orderSn") String orderSn, @Param("skuId") Long skuId);

    /** 分页查询评论（联表用户信息） */
    @Select("SELECT r.id, r.sku_id, r.user_id, r.rating, r.content, r.create_time, " +
            "u.nickname, u.avatar " +
            "FROM product_review r " +
            "LEFT JOIN sys_user u ON r.user_id = u.id " +
            "WHERE r.spu_id = #{spuId} AND r.deleted = 0 " +
            "ORDER BY r.create_time DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY")
    List<ReviewListResponse.ReviewItem> selectReviewPage(@Param("spuId") Long spuId,
                                                          @Param("offset") int offset,
                                                          @Param("size") int size);
}
