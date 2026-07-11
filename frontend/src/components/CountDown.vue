<template>
  <div class="countdown" v-if="display">
    <span class="label">{{ prefix }}</span>
    <span class="time-block">{{ hours }}</span>:<span class="time-block">{{ mins }}</span>:<span class="time-block">{{ secs }}</span>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import dayjs from '@/utils/dayjs'

const props = defineProps({
  targetTime: { type: [String, Number], required: true },
  prefix: { type: String, default: '距结束' }
})

const emit = defineEmits(['finished'])
const now = ref(Date.now())
let timer = null

const diff = computed(() => {
  const t = dayjs(props.targetTime).valueOf() - now.value
  return Math.max(0, t)
})
const display = computed(() => diff.value > 0)
const hours = computed(() => String(Math.floor(diff.value / 3600000)).padStart(2, '0'))
const mins = computed(() => String(Math.floor((diff.value % 3600000) / 60000)).padStart(2, '0'))
const secs = computed(() => String(Math.floor((diff.value % 60000) / 1000)).padStart(2, '0'))

onMounted(() => { timer = setInterval(() => { now.value = Date.now(); if (diff.value <= 0) { emit('finished'); clearInterval(timer) } }, 1000) })
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.countdown { display: inline-flex; align-items: center; gap: 4px; }
.label { color: #666; font-size: 13px; }
.time-block {
  background: #2c3e50;
  color: #fff;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 700;
  font-family: monospace;
}
</style>
