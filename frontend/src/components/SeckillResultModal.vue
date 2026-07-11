<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div class="result-overlay" v-if="visible" @click.self="$emit('close')">
        <div class="result-modal" :class="statusClass">
          <!-- 成功 -->
          <template v-if="status === 'success'">
            <div class="result-icon">🎉</div>
            <h2>恭喜！秒杀成功！</h2>
            <div class="order-info">
              <p>订单号：<strong>{{ orderSn }}</strong></p>
              <p v-if="seckillPrice">秒杀价：<span class="price">¥{{ seckillPrice }}</span></p>
            </div>
            <div class="modal-actions">
              <el-button type="danger" size="large" @click="$router.push('/orders')">查看订单</el-button>
              <el-button size="large" @click="$emit('close')">继续逛逛</el-button>
            </div>
          </template>

          <!-- 失败 -->
          <template v-else-if="status === 'failed'">
            <div class="result-icon">😞</div>
            <h2>{{ message || '很遗憾，没抢到' }}</h2>
            <p class="hint">别灰心，下次手速再快一点！</p>
            <div class="ai-suggestion" v-if="suggestion">
              <span class="ai-badge">🤖 AI 建议</span>
              <p>{{ suggestion }}</p>
            </div>
            <div class="modal-actions">
              <el-button type="danger" size="large" @click="$emit('close'); $emit('retry')">再试一次</el-button>
              <el-button size="large" @click="$emit('close')">关闭</el-button>
            </div>
          </template>

          <!-- 排队中 -->
          <template v-else-if="status === 'queuing'">
            <div class="result-icon spinning">⏳</div>
            <h2>排队处理中...</h2>
            <p class="hint">您的请求已进入队列，正在为您生成订单</p>
            <div class="modal-actions">
              <el-button size="large" @click="$emit('close')">关闭等待</el-button>
            </div>
          </template>

          <!-- 被风控拦截 -->
          <template v-else-if="status === 'blocked'">
            <div class="result-icon">🛡️</div>
            <h2>操作被拦截</h2>
            <p class="hint">{{ message || '系统检测到异常操作，请稍后再试' }}</p>
            <div class="modal-actions">
              <el-button size="large" type="warning" @click="$emit('close')">知道了</el-button>
            </div>
          </template>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  status: { type: String, default: '' },    // 'success' | 'failed' | 'queuing' | 'blocked'
  message: { type: String, default: '' },
  orderSn: { type: String, default: '' },
  seckillPrice: { type: [String, Number], default: '' },
  suggestion: { type: String, default: '' }
})

defineEmits(['close', 'retry'])

const statusClass = computed(() => `status-${props.status}`)
</script>

<style scoped>
.result-overlay {
  position: fixed; inset: 0; z-index: 3000;
  background: rgba(0,0,0,.5);
  display: flex; align-items: center; justify-content: center;
}
.result-modal {
  background: #fff; border-radius: 20px; padding: 40px;
  text-align: center; max-width: 420px; width: 90%;
  box-shadow: 0 20px 60px rgba(0,0,0,.3);
}
.result-icon { font-size: 64px; margin-bottom: 12px; }
.result-icon.spinning { animation: spin 2s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

h2 { font-size: 22px; margin-bottom: 8px; }
.hint { color: #999; margin-bottom: 16px; font-size: 14px; }
.order-info { background: #f5f7fa; border-radius: 8px; padding: 12px; margin-bottom: 20px; }
.order-info p { margin: 4px 0; font-size: 14px; }
.price { color: #e74c3c; font-weight: 700; }
.ai-suggestion {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff; border-radius: 10px; padding: 12px 16px;
  margin-bottom: 20px; text-align: left;
}
.ai-badge { font-size: 12px; opacity: .8; display: block; margin-bottom: 4px; }
.ai-suggestion p { font-size: 13px; margin: 0; }
.modal-actions { display: flex; gap: 12px; justify-content: center; }

/* Transition */
.modal-fade-enter-active, .modal-fade-leave-active { transition: opacity .3s; }
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }

/* Status themes */
.status-success .result-icon { animation: bounce .6s; }
.status-failed .result-modal { border: 2px solid #f0f0f0; }
@keyframes bounce {
  0%,100% { transform: scale(1); }
  30% { transform: scale(1.3); }
  50% { transform: scale(.9); }
  70% { transform: scale(1.1); }
}
</style>
