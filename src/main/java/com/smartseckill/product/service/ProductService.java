package com.smartseckill.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartseckill.common.exception.BusinessException;
import com.smartseckill.common.page.PageResponse;
import com.smartseckill.common.result.ResultCode;
import com.smartseckill.product.entity.*;
import com.smartseckill.product.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品业务服务。
 * <p>
 * 管理 SPU/SKU/分类/品牌/库存的 CRUD 和上下架。
 * 查询使用 MyBatis-Plus LambdaQueryWrapper 构建动态条件；
 * 库存扣减/锁定等原子操作在 {@link ProductStockMapper} 中通过 SQL 实现。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductStockMapper stockMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductBrandMapper brandMapper;

    // ==================== 分类管理 ====================

    public List<ProductCategory> getCategoryList(Long parentId) {
        return categoryMapper.selectList(
                new LambdaQueryWrapper<ProductCategory>()
                        .eq(ProductCategory::getStatus, 1)
                        .eq(ProductCategory::getDeleted, 0)
                        .eq(parentId != null, ProductCategory::getParentId, parentId)
                        .orderByAsc(ProductCategory::getSortOrder)
        );
    }

    @Transactional
    public void saveCategory(ProductCategory category) {
        categoryMapper.insert(category);
    }

    @Transactional
    public void updateCategory(ProductCategory category) {
        categoryMapper.updateById(category);
    }

    public void deleteCategory(Long id) {
        categoryMapper.deleteById(id);
    }

    // ==================== 品牌管理 ====================

    /**
     * 获取品牌列表。
     * @param categoryId 可选，若指定则只返回该分类（含子分类）下有商品的品牌
     */
    public List<ProductBrand> getBrandList(Long categoryId) {
        if (categoryId != null) {
            // 收集该分类及其所有子分类 ID
            List<Long> categoryIds = collectCategoryIds(categoryId);
            return brandMapper.selectBrandsByCategoryIds(categoryIds);
        }
        return brandMapper.selectList(
                new LambdaQueryWrapper<ProductBrand>()
                        .eq(ProductBrand::getStatus, 1)
                        .eq(ProductBrand::getDeleted, 0)
                        .orderByAsc(ProductBrand::getSortOrder)
        );
    }

    /** 递归收集分类及其子分类 ID */
    private List<Long> collectCategoryIds(Long parentId) {
        List<Long> ids = new ArrayList<>();
        ids.add(parentId);
        List<ProductCategory> children = categoryMapper.selectList(
                new LambdaQueryWrapper<ProductCategory>()
                        .eq(ProductCategory::getParentId, parentId)
                        .eq(ProductCategory::getStatus, 1)
                        .eq(ProductCategory::getDeleted, 0)
        );
        for (ProductCategory child : children) {
            ids.addAll(collectCategoryIds(child.getId()));
        }
        return ids;
    }

    @Transactional
    public void saveBrand(ProductBrand brand) {
        brandMapper.insert(brand);
    }

    @Transactional
    public void updateBrand(ProductBrand brand) {
        brandMapper.updateById(brand);
    }

    public void deleteBrand(Long id) {
        brandMapper.deleteById(id);
    }

    // ==================== SPU/SKU 管理 ====================

    /**
     * 分页查询 SPU 列表。
     * 条件构造技巧：{@code .eq(categoryId != null, ...)} 参数为 null 时不拼该条件。
     * 查询后批量填充 minPrice（每个 SPU 下最低 SKU 售价）。
     */
    public PageResponse<ProductSpu> getSpuList(int page, int size, Long categoryId, Long brandId, String keyword, Integer status) {
        // 收集该分类及其所有子分类 ID，实现级联查询
        List<Long> catIds = categoryId != null ? collectCategoryIds(categoryId) : null;

        LambdaQueryWrapper<ProductSpu> wrapper = new LambdaQueryWrapper<ProductSpu>()
                .eq(ProductSpu::getDeleted, 0)
                .eq(status != null, ProductSpu::getStatus, status)  // 不传则不过滤，管理员可查看全部
                // 仅展示有有效 SKU 的商品，避免无规格/无价格的空壳 SPU
                .inSql(ProductSpu::getId,
                        "SELECT DISTINCT spu_id FROM product_sku WHERE deleted = 0 AND status = 1")
                .in(catIds != null && !catIds.isEmpty(), ProductSpu::getCategoryId, catIds)
                .eq(brandId != null, ProductSpu::getBrandId, brandId)
                .like(StringUtils.hasText(keyword), ProductSpu::getSpuName, keyword)
                .orderByDesc(ProductSpu::getCreateTime);

        Page<ProductSpu> result = spuMapper.selectPage(new Page<>(page, size), wrapper);

        // 批量填充最低售价
        List<ProductSpu> records = result.getRecords();
        if (!records.isEmpty()) {
            List<Long> spuIds = records.stream().map(ProductSpu::getId).collect(Collectors.toList());
            List<Map<String, Object>> priceRows = skuMapper.selectMinPriceBySpuIds(spuIds);
            Map<Long, BigDecimal> priceMap = new HashMap<>();
            for (Map<String, Object> row : priceRows) {
                Long spuId = ((Number) row.get("spu_id")).longValue();
                BigDecimal minPrice = (BigDecimal) row.get("min_price");
                priceMap.put(spuId, minPrice);
            }
            records.forEach(r -> r.setMinPrice(priceMap.get(r.getId())));
        }

        return PageResponse.of(result);
    }

    /** 获取 SPU 详情，不存在则抛 BusinessException */
    public ProductSpu getSpuDetail(Long spuId) {
        ProductSpu spu = spuMapper.selectById(spuId);
        if (spu == null || spu.getDeleted() == 1) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        return spu;
    }

    /** 获取某 SPU 下所有 SKU（规格） */
    public List<ProductSku> getSkuListBySpuId(Long spuId) {
        return skuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>()
                        .eq(ProductSku::getSpuId, spuId)
                        .eq(ProductSku::getDeleted, 0)
        );
    }

    @Transactional
    public void saveSpu(ProductSpu spu) {
        spuMapper.insert(spu);
    }

    @Transactional
    public void updateSpu(ProductSpu spu) {
        spuMapper.updateById(spu);
    }

    @Transactional
    public void saveSku(ProductSku sku) {
        skuMapper.insert(sku);
    }

    @Transactional
    public void updateSku(ProductSku sku) {
        skuMapper.updateById(sku);
    }

    @Transactional
    public void deleteSku(Long id) {
        skuMapper.deleteById(id);
    }

    /** 商品上架：status 设为 1 */
    @Transactional
    public void publishProduct(Long spuId) {
        ProductSpu spu = getSpuDetail(spuId);
        spu.setStatus(1);
        spuMapper.updateById(spu);
    }

    /** 商品下架：status 设为 2 */
    @Transactional
    public void unpublishProduct(Long spuId) {
        ProductSpu spu = getSpuDetail(spuId);
        spu.setStatus(2);
        spuMapper.updateById(spu);
    }

    // ==================== 库存管理 ====================

    public ProductStock getStock(Long skuId, Integer stockType) {
        return stockMapper.selectOne(
                new LambdaQueryWrapper<ProductStock>()
                        .eq(ProductStock::getSkuId, skuId)
                        .eq(ProductStock::getStockType, stockType)
                        .eq(ProductStock::getDeleted, 0)
        );
    }

    /** 可用库存 = stock - lock_stock，由 Mapper 自定义 SQL 计算 */
    public Integer getAvailableStock(Long skuId) {
        Integer available = stockMapper.getAvailableStock(skuId);
        return available != null ? available : 0;
    }

    /** 初始化 SKU 库存记录 — 存在则更新，不存在则插入 */
    @Transactional
    public void setStock(Long skuId, Integer stock, Integer stockType, Long activityId) {
        LambdaQueryWrapper<ProductStock> wrapper = new LambdaQueryWrapper<ProductStock>()
                .eq(ProductStock::getSkuId, skuId)
                .eq(ProductStock::getStockType, stockType)
                .eq(ProductStock::getDeleted, 0)
                .last("AND (activity_id = " + (activityId != null ? activityId : 0)
                        + (stockType != null && stockType == 1 ? " OR stock_type = 1" : "") + ")");
        ProductStock existing = stockMapper.selectOne(wrapper);
        if (existing != null) {
            existing.setStock(stock);
            existing.setLockStock(0);
            stockMapper.updateById(existing);
        } else {
            ProductStock stockEntity = new ProductStock();
            stockEntity.setSkuId(skuId);
            stockEntity.setStock(stock);
            stockEntity.setLockStock(0);
            stockEntity.setStockType(stockType != null ? stockType : 0);
            stockEntity.setActivityId(activityId != null ? activityId : 0L);
            stockMapper.insert(stockEntity);
        }
    }
}
