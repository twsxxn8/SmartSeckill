package com.smartseckill.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartseckill.common.util.JwtUtil;
import com.smartseckill.common.util.RedisUtil;
import com.smartseckill.product.controller.ProductController;
import com.smartseckill.product.entity.ProductBrand;
import com.smartseckill.product.entity.ProductCategory;
import com.smartseckill.product.entity.ProductSku;
import com.smartseckill.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class, ObjectMapper.class})
@TestPropertySource(properties = {
        "jwt.secret=SmartSeckill2024SecretKeyForJwtTokenGenerationMustBeLongEnough",
        "jwt.expiration=7200000",
        "jwt.refresh-expiration=604800000"
})
class SecurityConfigPublicPathTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    /** JWT 过滤器依赖（Token 黑名单校验），@WebMvcTest 切片不会扫描该 @Component，需显式 Mock */
    @MockBean
    private RedisUtil redisUtil;

    @Test
    void productCategories_shouldBePublic() throws Exception {
        when(productService.getCategoryList(null)).thenReturn(List.of(new ProductCategory()));

        mockMvc.perform(get("/product/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void productBrands_shouldBePublic() throws Exception {
        when(productService.getBrandList(null)).thenReturn(List.of(new ProductBrand()));

        mockMvc.perform(get("/product/brands"))
                .andExpect(status().isOk());
    }

    @Test
    void productSkus_shouldBePublic() throws Exception {
        when(productService.getSkuListBySpuId(1L)).thenReturn(List.of(new ProductSku()));

        mockMvc.perform(get("/product/1/skus"))
                .andExpect(status().isOk());
    }

    @Test
    void productStock_shouldBePublic() throws Exception {
        when(productService.getAvailableStock(100L)).thenReturn(10);

        mockMvc.perform(get("/product/stock/100"))
                .andExpect(status().isOk());
    }
}
