package com.smartseckill.product.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商品详情完整 DTO（含品牌/分类名称 + 解析后的 SKU 规格信息）。
 * <p>
 * 与原始 {@link com.smartseckill.product.entity.ProductSpu} 相比：
 * <ul>
 *   <li>brandName / categoryName 替代了 brandId / categoryId</li>
 *   <li>SKU 列表中的 specInfo 是解析后的 Map，不再是 JSON 字符串</li>
 * </ul>
 * </p>
 */
@Data
public class ProductDetailDTO {

    // ==================== SPU 基本信息 ====================
    private Long id;
    private String spuName;
    private String description;
    private String mainImage;
    private String images;
    private Integer status;

    // ==================== 联表填充 ====================
    private String brandName;
    private String categoryName;

    // ==================== SKU 列表（含解析后的规格） ====================
    private List<SkuDetail> skus;

    @Data
    public static class SkuDetail {
        private Long id;
        private String skuName;
        private String skuCode;
        private BigDecimal price;
        private BigDecimal marketPrice;
        private String image;
        /** 解析后的规格键值对，如 {"颜色":"黑色","存储":"256GB"} */
        private Map<String, String> specInfo;
        private Integer status;
    }
}
