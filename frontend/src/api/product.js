import request from './request'

export function getProductList(params) {
  // 默认只展示已上架商品（前台），管理员可传 status 覆盖
  return request.get('/product/list', { params })
}

export function getProductDetail(spuId) {
  return request.get(`/product/detail/${spuId}`)
}

export function getProductDetailFull(spuId) {
  return request.get(`/product/detail/${spuId}/full`)
}

export function getSkuList(spuId) {
  return request.get(`/product/${spuId}/skus`)
}

export function getStock(skuId) {
  return request.get(`/product/stock/${skuId}`)
}

export function getCategories(parentId) {
  return request.get('/product/categories', { params: parentId != null ? { parentId } : {} })
}

export function getBrands(categoryId) {
  return request.get('/product/brands', { params: categoryId != null ? { categoryId } : {} })
}

// Admin
export function saveSpu(data) {
  return request.post('/product/admin/spu', data)
}

export function updateSpu(id, data) {
  return request.put(`/product/admin/spu/${id}`, data)
}

export function publishProduct(spuId) {
  return request.post(`/product/admin/publish/${spuId}`)
}

export function unpublishProduct(spuId) {
  return request.post(`/product/admin/unpublish/${spuId}`)
}

export function setStock(params) {
  return request.post('/product/admin/stock', null, { params })
}
