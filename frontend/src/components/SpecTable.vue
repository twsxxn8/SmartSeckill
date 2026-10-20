<template>
  <div class="spec-table" v-if="specs && Object.keys(specs).length">
    <div class="spec-row" v-for="(value, key) in specs" :key="key">
      <span class="spec-key">{{ key }}</span>
      <span class="spec-value">{{ value }}</span>
    </div>
  </div>
  <span class="no-spec" v-else>暂无规格信息</span>
</template>

<script setup>
defineProps({
  specs: { type: Object, default: () => ({}) }
})
</script>

<style scoped>
.spec-table { display: flex; flex-direction: column; }
.spec-row { display: flex; padding: 8px 0; border-bottom: 1px solid #f0f0f0; }
.spec-row:last-child { border-bottom: none; }
.spec-key { width: 100px; color: #999; font-size: 13px; flex-shrink: 0; }
.spec-value { color: #333; font-size: 14px; }
.no-spec { color: #999; font-size: 13px; }
</style>
