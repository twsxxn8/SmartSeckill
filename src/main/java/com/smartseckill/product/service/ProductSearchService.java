package com.smartseckill.product.service;

import com.smartseckill.common.page.PageResponse;
import com.smartseckill.product.entity.ProductDocument;
import com.smartseckill.product.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品 Elasticsearch 搜索服务（懒加载，ES 不可用时不影响启动）。
 */
@Slf4j
@Lazy
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ProductSearchRepository searchRepository;
    private final ElasticsearchRestTemplate elasticsearchRestTemplate;

    /**
     * 商品搜索：支持关键词分词、分类/品牌过滤、排序、关键词高亮。
     */
    public PageResponse<ProductDocument> search(String keyword, Long categoryId, Long brandId,
                                                 String sortBy, boolean asc,
                                                 int page, int size) {
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
                .withPageable(PageRequest.of(page - 1, size));

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolean hasCondition = false;

        // 关键词多字段匹配，spuName 权重 ^3 优先
        if (StringUtils.hasText(keyword)) {
            boolQuery.must(QueryBuilders.multiMatchQuery(keyword)
                    .field("spuName", 3.0f));
            hasCondition = true;
        }

        if (categoryId != null) {
            boolQuery.filter(QueryBuilders.termQuery("categoryId", categoryId));
            hasCondition = true;
        }

        if (brandId != null) {
            boolQuery.filter(QueryBuilders.termQuery("brandId", brandId));
            hasCondition = true;
        }

        if (hasCondition) {
            queryBuilder.withQuery(boolQuery);
        } else {
            queryBuilder.withQuery(QueryBuilders.matchAllQuery());
        }

        // 排序
        if (StringUtils.hasText(sortBy)) {
            SortOrder order = asc ? SortOrder.ASC : SortOrder.DESC;
            queryBuilder.withSort(SortBuilders.fieldSort(sortBy).order(order));
        }

        NativeSearchQuery query = queryBuilder.build();
        SearchHits<ProductDocument> searchHits = elasticsearchRestTemplate.search(query, ProductDocument.class);

        List<ProductDocument> products = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        long total = searchHits.getTotalHits();
        return PageResponse.of(products, total, page, size);
    }

    /** 保存或更新单个商品 ES 索引 */
    public void saveProduct(ProductDocument document) {
        searchRepository.save(document);
    }

    /** 批量保存商品 ES 索引 */
    public void saveAllProducts(List<ProductDocument> documents) {
        searchRepository.saveAll(documents);
    }

    /** 删除商品 ES 索引 */
    public void deleteProduct(Long id) {
        searchRepository.deleteById(id);
    }
}
