<template>
  <span class="animated-number">{{ displayValue }}</span>
</template>

<script setup>
import { ref, watch, onUnmounted } from 'vue'

const props = defineProps({
  value: { type: Number, default: 0 },
  duration: { type: Number, default: 300 }
})

const displayValue = ref(props.value)

let animFrame = null

function animate(from, to, duration) {
  if (animFrame) cancelAnimationFrame(animFrame)
  const start = performance.now()

  function step(now) {
    const elapsed = now - start
    const progress = Math.min(elapsed / duration, 1)
    // easeOutCubic
    const eased = 1 - Math.pow(1 - progress, 3)
    displayValue.value = Math.round(from + (to - from) * eased)

    if (progress < 1) {
      animFrame = requestAnimationFrame(step)
    } else {
      displayValue.value = to
    }
  }

  animFrame = requestAnimationFrame(step)
}

watch(() => props.value, (newVal, oldVal) => {
  if (newVal !== oldVal) {
    animate(oldVal, newVal, props.duration)
  }
}, { immediate: false })

onUnmounted(() => {
  if (animFrame) cancelAnimationFrame(animFrame)
})
</script>

<style scoped>
.animated-number {
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum";
}
</style>
