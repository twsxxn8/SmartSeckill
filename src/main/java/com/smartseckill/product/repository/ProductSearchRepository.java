package com.smartseckill.product.repository;

import com.smartseckill.product.entity.ProductDocument;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Lazy
@Repository
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, Long> {
}
