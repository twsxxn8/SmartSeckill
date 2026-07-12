import request from './request'

/**
 * 获取某 SPU 的评论列表（分页 + 平均评分 + 分布）
 */
export function getSpuReviews(spuId, page = 1, size = 10) {
  return request.get(`/review/spu/${spuId}`, { params: { page, size } })
}

/**
 * 发表评论（需登录）
 */
export function createReview(data) {
  return request.post('/review', data)
}
