package com.smartseckill.product.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartseckill.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * SPU（Standard Product Unit）标准产品单元，对应表 {@code product_spu}。
 * <p>例：iPhone 15 是一个 SPU，其下有多种 SKU（128G黑色、256G白色等）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_spu")
public class ProductSpu extends BaseEntity {

    private String spuName;
    private Long categoryId;
    private Long brandId;
    private String description;
    private String mainImage;
    private String images;
    /** 状态：0=草稿, 1=已上架, 2=已下架 */
    private Integer status;

    /** 最低售价（非数据库字段，来自 SKU 聚合查询） */
    @TableField(exist = false)
    private BigDecimal minPrice;
}
