package com.smartseckill.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartseckill.product.entity.ProductStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 商品库存 Mapper。
 * <p>
 * 库存操作使用自定义 SQL 而非 MyBatis-Plus 默认方法，原因：
 * <ul>
 *   <li>需要在 WHERE 中做 {@code stock >= quantity} 原子判断，防止超卖</li>
 *   <li>lockStock 机制：下单锁定 → 支付扣减 → 取消释放</li>
 *   <li>version 字段实现乐观锁</li>
 * </ul>
 * </p>
 */
@Mapper
public interface ProductStockMapper extends BaseMapper<ProductStock> {

    /**
     * 支付成功后扣减真实库存。
     * 同时减少 stock 和 lock_stock（下单时已 lock）。
     *
     * @return 影响行数，0 表示库存不足
     */
    @Update("UPDATE product_stock SET stock = stock - #{quantity}, lock_stock = lock_stock - #{quantity}, " +
            "version = version + 1, update_time = GETDATE() " +
            "WHERE sku_id = #{skuId} AND stock_type = 0 AND stock >= #{quantity} AND deleted = 0")
    int deductStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /**
     * 下单时锁定库存（增加 lock_stock，不减少 stock）。
     * 条件：(stock - lock_stock) >= quantity 保证可用库存充足。
     *
     * @return 影响行数，0 表示可用库存不足
     */
    @Update("UPDATE product_stock SET lock_stock = lock_stock + #{quantity}, version = version + 1, " +
            "update_time = GETDATE() " +
            "WHERE sku_id = #{skuId} AND stock_type = 0 AND (stock - lock_stock) >= #{quantity} AND deleted = 0")
    int lockStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /**
     * 取消订单或超时未支付时释放锁定库存。
     *
     * @return 影响行数，0 表示 lock_stock 不足（异常情况）
     */
    @Update("UPDATE product_stock SET lock_stock = lock_stock - #{quantity}, version = version + 1, " +
            "update_time = GETDATE() " +
            "WHERE sku_id = #{skuId} AND stock_type = 0 AND lock_stock >= #{quantity} AND deleted = 0")
    int unlockStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /** 查询可用库存：总库存 - 已锁定库存 */
    @Select("SELECT stock - lock_stock FROM product_stock WHERE sku_id = #{skuId} AND stock_type = 0 AND deleted = 0")
    Integer getAvailableStock(@Param("skuId") Long skuId);

    /**
     * 秒杀下单扣减真实库存（只减 stock，不动 lock_stock）。
     * <p>
     * 秒杀链路与普通链路不同：Redis Lua 已完成预扣，这里只做最终落库扣减，
     * 不存在"先锁定后扣减"的 lock 阶段。若误用 {@link #deductStock} 会把
     * lock_stock 扣成负数，导致普通通道可用库存（stock - lock_stock）虚增超卖。
     * </p>
     *
     * @return 影响行数，0 表示库存不足（调用方必须回滚事务）
     */
    @Update("UPDATE product_stock SET stock = stock - #{quantity}, " +
            "version = version + 1, update_time = GETDATE() " +
            "WHERE sku_id = #{skuId} AND stock_type = 0 AND stock >= #{quantity} AND deleted = 0")
    int deductSeckillStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /**
     * 秒杀订单取消/超时关单时回补真实库存（只加 stock，不动 lock_stock）。
     */
    @Update("UPDATE product_stock SET stock = stock + #{quantity}, " +
            "version = version + 1, update_time = GETDATE() " +
            "WHERE sku_id = #{skuId} AND stock_type = 0 AND deleted = 0")
    int restoreSeckillStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);
}
