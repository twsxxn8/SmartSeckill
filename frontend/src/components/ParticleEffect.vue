<template>
  <div class="particle-container" ref="container">
    <span
      v-for="p in particles"
      :key="p.id"
      class="particle"
      :style="p.style"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  /** 粒子数量 */
  count: { type: Number, default: 30 },
  /** 粒子颜色列表 */
  colors: { type: Array, default: () => ['#e74c3c', '#f39c12', '#e67e22', '#c0392b', '#fff'] },
  /** 动画持续时间（毫秒） */
  duration: { type: Number, default: 1000 }
})

const emit = defineEmits(['finished'])
const container = ref(null)
const particles = ref([])

let idCounter = 0

function burst(x, y) {
  const newParticles = []
  for (let i = 0; i < props.count; i++) {
    const angle = (Math.PI * 2 * i) / props.count + (Math.random() - 0.5) * 0.5
    const velocity = 60 + Math.random() * 140
    const size = 4 + Math.random() * 8
    const color = props.colors[Math.floor(Math.random() * props.colors.length)]

    newParticles.push({
      id: ++idCounter,
      style: {
        left: '0px',
        top: '0px',
        width: size + 'px',
        height: size + 'px',
        background: color,
        '--vx': Math.cos(angle) * velocity + 'px',
        '--vy': Math.sin(angle) * velocity - 40 + 'px',
        animationDuration: (props.duration * 0.6 + Math.random() * props.duration * 0.4) + 'ms'
      }
    })
  }
  particles.value = newParticles

  setTimeout(() => {
    particles.value = []
    emit('finished')
  }, props.duration + 200)
}

// 从指定位置爆发粒子
function burstAt(el) {
  if (!el) return
  const rect = el.getBoundingClientRect()
  burst(rect.left + rect.width / 2, rect.top + rect.height / 2)
}

defineExpose({ burst, burstAt })

onMounted(() => {})
onUnmounted(() => { particles.value = [] })
</script>

<style scoped>
.particle-container {
  position: fixed; inset: 0; z-index: 2999; pointer-events: none; overflow: hidden;
}
.particle {
  position: absolute;
  border-radius: 2px;
  animation: particle-fly var(--anim-dur, 800ms) ease-out forwards;
}
@keyframes particle-fly {
  0% {
    opacity: 1;
    transform: translate(0, 0) scale(1);
  }
  60% {
    opacity: .8;
  }
  100% {
    opacity: 0;
    transform: translate(var(--vx), var(--vy)) scale(0);
  }
}
</style>
