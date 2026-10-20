package com.smartseckill.product.controller;

import com.smartseckill.common.page.PageResponse;
import com.smartseckill.common.result.Result;
import com.smartseckill.product.dto.ProductDetailDTO;
import com.smartseckill.product.entity.*;
import com.smartseckill.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品管理控制器。
 * <p>
 * 分为两类接口：
 * <ul>
 *   <li>前台接口（/list、/detail 等）— 公开访问，供商城浏览</li>
 *   <li>后台接口（/admin/*）— 需 ADMIN 角色，供运营管理</li>
 * </ul>
 * 商品模型：SPU（标准产品）→ SKU（具体规格）→ Stock（库存）
 * </p>
 */
@Tag(name = "商品管理")
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ==================== 前台接口（SecurityConfig 中部分已设为公开） ====================

    @Operation(summary = "商品列表（分页）")
    @GetMapping("/list")
    public Result<PageResponse<ProductSpu>> getProductList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(productService.getSpuList(page, size, categoryId, brandId, keyword, status));
    }

    @Operation(summary = "商品详情")
    @GetMapping("/detail/{spuId}")
    public Result<ProductSpu> getProductDetail(@PathVariable Long spuId) {
        return Result.success(productService.getSpuDetail(spuId));
    }

    @Operation(summary = "获取SKU列表")
    @GetMapping("/{spuId}/skus")
    public Result<List<ProductSku>> getSkuList(@PathVariable Long spuId) {
        return Result.success(productService.getSkuListBySpuId(spuId));
    }

    @Operation(summary = "商品详情（完整信息含品牌/分类名称及SKU规格）")
    @GetMapping("/detail/{spuId}/full")
    public Result<ProductDetailDTO> getProductDetailFull(@PathVariable Long spuId) {
        return Result.success(productService.getProductDetailFull(spuId));
    }

    @Operation(summary = "获取库存")
    @GetMapping("/stock/{skuId}")
    public Result<Integer> getStock(@PathVariable Long skuId) {
        return Result.success(productService.getAvailableStock(skuId));
    }

    @Operation(summary = "获取分类列表（可指定父级ID）")
    @GetMapping("/categories")
    public Result<List<ProductCategory>> getCategoryList(@RequestParam(required = false) Long parentId) {
        return Result.success(productService.getCategoryList(parentId));
    }

    @Operation(summary = "获取品牌列表（可按分类筛选）")
    @GetMapping("/brands")
    public Result<List<ProductBrand>> getBrandList(@RequestParam(required = false) Long categoryId) {
        return Result.success(productService.getBrandList(categoryId));
    }

    // ==================== 后台管理接口（@PreAuthorize 要求 ADMIN 角色） ====================

    @Operation(summary = "新增分类")
    @PostMapping("/admin/category")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> saveCategory(@RequestBody ProductCategory category) {
        productService.saveCategory(category);
        return Result.success();
    }

    @Operation(summary = "更新分类")
    @PutMapping("/admin/category/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateCategory(@PathVariable Long id, @RequestBody ProductCategory category) {
        category.setId(id);
        productService.updateCategory(category);
        return Result.success();
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/admin/category/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        productService.deleteCategory(id);
        return Result.success();
    }

    @Operation(summary = "新增品牌")
    @PostMapping("/admin/brand")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> saveBrand(@RequestBody ProductBrand brand) {
        productService.saveBrand(brand);
        return Result.success();
    }

    @Operation(summary = "更新品牌")
    @PutMapping("/admin/brand/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateBrand(@PathVariable Long id, @RequestBody ProductBrand brand) {
        brand.setId(id);
        productService.updateBrand(brand);
        return Result.success();
    }

    @Operation(summary = "新增SPU")
    @PostMapping("/admin/spu")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ProductSpu> saveSpu(@RequestBody ProductSpu spu) {
        productService.saveSpu(spu);
        return Result.success(spu);
    }

    @Operation(summary = "更新SPU")
    @PutMapping("/admin/spu/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateSpu(@PathVariable Long id, @RequestBody ProductSpu spu) {
        spu.setId(id);
        productService.updateSpu(spu);
        return Result.success();
    }

    @Operation(summary = "新增SKU")
    @PostMapping("/admin/sku")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ProductSku> saveSku(@RequestBody ProductSku sku) {
        productService.saveSku(sku);
        return Result.success(sku);
    }

    @Operation(summary = "更新SKU")
    @PutMapping("/admin/sku/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateSku(@PathVariable Long id, @RequestBody ProductSku sku) {
        sku.setId(id);
        productService.updateSku(sku);
        return Result.success();
    }

    @Operation(summary = "删除SKU")
    @DeleteMapping("/admin/sku/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteSku(@PathVariable Long id) {
        productService.deleteSku(id);
        return Result.success();
    }

    @Operation(summary = "商品上架")
    @PostMapping("/admin/publish/{spuId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> publishProduct(@PathVariable Long spuId) {
        productService.publishProduct(spuId);
        return Result.success();
    }

    @Operation(summary = "商品下架")
    @PostMapping("/admin/unpublish/{spuId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> unpublishProduct(@PathVariable Long spuId) {
        productService.unpublishProduct(spuId);
        return Result.success();
    }

    @Operation(summary = "设置库存")
    @PostMapping("/admin/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> setStock(@RequestParam Long skuId,
                                 @RequestParam Integer stock,
                                 @RequestParam(defaultValue = "0") Integer stockType,
                                 @RequestParam(required = false) Long activityId) {
        productService.setStock(skuId, stock, stockType, activityId);
        return Result.success();
    }
}
