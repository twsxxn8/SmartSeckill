package com.smartseckill.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartseckill.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * SKU（Stock Keeping Unit）库存单元，对应表 {@code product_sku}。
 * <p>SPU 下的具体售卖规格，含价格、图片、规格 JSON 等。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_sku")
public class ProductSku extends BaseEntity {

    private Long spuId;
    private String skuName;
    private String skuCode;
    private BigDecimal price;
    private BigDecimal marketPrice;
    private String image;
    private String specInfo; // JSON: {"颜色":"红色","内存":"128G"}
    private Integer status;
}
