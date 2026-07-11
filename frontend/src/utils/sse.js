/**
 * SSE (Server-Sent Events) 连接管理工具。
 *
 * 封装 EventSource，提供自动重连、事件回调注册、连接状态管理。
 * 浏览器 EventSource 不支持自定义 Header，因此通过 URL 查询参数传递 token。
 */

const SSE_CONFIG = {
  /** 基础重连延迟（毫秒） */
  baseRetryDelay: 1000,
  /** 最大重连延迟（毫秒） */
  maxRetryDelay: 30000,
  /** 重连延迟倍数（指数退避） */
  retryBackoff: 2,
  /** 心跳超时（毫秒），超过此时间未收到任何事件则重连 */
  heartbeatTimeout: 45000
}

/**
 * 创建 SSE 连接。
 *
 * @param {string} url - SSE 端点 URL（不含 token 参数）
 * @param {string} token - JWT accessToken
 * @param {Object} handlers - 事件处理函数映射 { eventName: (data) => void }
 * @returns {{ close: () => void, readyState: () => number }}
 */
export function createSSEConnection(url, token, handlers = {}) {
  let eventSource = null
  let retryCount = 0
  let heartbeatTimer = null
  let closed = false
  let reconnectTimer = null

  const fullUrl = url + (url.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(token)

  function clearTimers() {
    if (heartbeatTimer) { clearTimeout(heartbeatTimer); heartbeatTimer = null }
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
  }

  function resetHeartbeat() {
    if (heartbeatTimer) clearTimeout(heartbeatTimer)
    heartbeatTimer = setTimeout(() => {
      console.warn('[SSE] Heartbeat timeout, reconnecting...')
      reconnect()
    }, SSE_CONFIG.heartbeatTimeout)
  }

  function reconnect() {
    if (closed) return
    clearTimers()
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }

    const delay = Math.min(
      SSE_CONFIG.baseRetryDelay * Math.pow(SSE_CONFIG.retryBackoff, retryCount),
      SSE_CONFIG.maxRetryDelay
    )
    retryCount++

    console.log(`[SSE] Reconnecting in ${delay}ms (attempt ${retryCount})...`)
    reconnectTimer = setTimeout(connect, delay)
  }

  function connect() {
    if (closed) return
    clearTimers()

    try {
      eventSource = new EventSource(fullUrl)
    } catch (e) {
      console.error('[SSE] Failed to create EventSource:', e)
      reconnect()
      return
    }

    eventSource.onopen = () => {
      retryCount = 0
      resetHeartbeat()
    }

    // 注册自定义事件处理器
    Object.entries(handlers).forEach(([eventName, handler]) => {
      eventSource.addEventListener(eventName, (e) => {
        resetHeartbeat()
        try {
          const data = JSON.parse(e.data)
          handler(data)
        } catch (err) {
          console.warn(`[SSE] Failed to parse ${eventName} event:`, err)
        }
      })
    })

    eventSource.onerror = () => {
      // EventSource 在连接失败时会自动触发 onerror，readyState 会变为 CLOSED
      if (eventSource && eventSource.readyState === EventSource.CLOSED) {
        if (!closed) reconnect()
      }
    }
  }

  // 立即连接
  connect()

  return {
    /** 关闭连接（主动关闭，不重连） */
    close() {
      closed = true
      clearTimers()
      if (eventSource) {
        eventSource.close()
        eventSource = null
      }
    },
    /** 获取当前连接状态：0=CONNECTING, 1=OPEN, 2=CLOSED */
    readyState() {
      return eventSource ? eventSource.readyState : EventSource.CLOSED
    }
  }
}
