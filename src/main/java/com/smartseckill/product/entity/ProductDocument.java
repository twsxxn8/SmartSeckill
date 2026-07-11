package com.smartseckill.product.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;

/**
 * Elasticsearch 商品搜索文档。
 * <p>与 MySQL 的 ProductSpu 分离，专用于全文检索，字段带 ES 分词和分析器配置。</p>
 */
@Data
@Document(indexName = "product")
public class ProductDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String spuName;

    @Field(type = FieldType.Long)
    private Long spuId;

    @Field(type = FieldType.Long)
    private Long categoryId;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    @Field(type = FieldType.Long)
    private Long brandId;

    @Field(type = FieldType.Keyword)
    private String brandName;

    @Field(type = FieldType.Keyword)
    private String mainImage;

    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal minPrice;

    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal maxPrice;

    @Field(type = FieldType.Integer)
    private Integer sales;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private String createTime;
}
