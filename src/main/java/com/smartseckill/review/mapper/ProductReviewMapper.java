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
 * 商品评论 Mapper（支持楼中楼回复）。
 */
@Mapper
public interface ProductReviewMapper extends BaseMapper<ProductReview> {

    /** 获取某 SPU 的平均评分（仅统计顶层评论的评分） */
    @Select("SELECT ISNULL(AVG(CAST(rating AS FLOAT)), 0) FROM product_review WHERE spu_id = #{spuId} AND root_id = 0 AND deleted = 0")
    Double selectAvgRatingBySpuId(@Param("spuId") Long spuId);

    /** 获取某 SPU 的评分分布（仅统计顶层评论） */
    @Select("SELECT rating, COUNT(1) AS cnt FROM product_review WHERE spu_id = #{spuId} AND root_id = 0 AND deleted = 0 GROUP BY rating")
    List<Map<String, Object>> selectRatingDistribution(@Param("spuId") Long spuId);

    /** 检查用户是否已对特定订单中的特定 SKU 发表过评论 */
    @Select("SELECT COUNT(1) FROM product_review WHERE user_id = #{userId} AND order_sn = #{orderSn} AND sku_id = #{skuId} AND root_id = 0 AND deleted = 0")
    int countByUserAndOrder(@Param("userId") Long userId, @Param("orderSn") String orderSn, @Param("skuId") Long skuId);

    /** 分页查询顶层评论（仅 root_id=0 的评论，含用户信息） */
    @Select("SELECT r.id, r.sku_id, r.user_id, r.rating, r.content, r.create_time, " +
            "u.nickname, u.avatar " +
            "FROM product_review r " +
            "LEFT JOIN sys_user u ON r.user_id = u.id " +
            "WHERE r.spu_id = #{spuId} AND r.root_id = 0 AND r.deleted = 0 " +
            "ORDER BY r.create_time DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY")
    List<ReviewListResponse.ReviewItem> selectRootReviewPage(@Param("spuId") Long spuId,
                                                              @Param("offset") int offset,
                                                              @Param("size") int size);

    /** 查询某条根评论的所有子回复（含用户信息，按时间正序） */
    @Select("SELECT r.id, r.parent_id, r.root_id, r.user_id, r.content, r.create_time, " +
            "u.nickname, u.avatar " +
            "FROM product_review r " +
            "LEFT JOIN sys_user u ON r.user_id = u.id " +
            "WHERE r.root_id = #{rootId} AND r.id != #{rootId} AND r.deleted = 0 " +
            "ORDER BY r.create_time ASC")
    List<ReviewListResponse.ReplyItem> selectRepliesByRootId(@Param("rootId") Long rootId);

    /** 获取某 SPU 的顶层评论总数 */
    @Select("SELECT COUNT(1) FROM product_review WHERE spu_id = #{spuId} AND root_id = 0 AND deleted = 0")
    Long countRootReviews(@Param("spuId") Long spuId);
}
