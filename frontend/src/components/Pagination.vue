<script setup>
import Icon from './Icon.vue';
defineProps({ page: Number, size: Number, total: Number });
defineEmits(['change']);
</script>
<template>
  <div class="pagination">
    <span>共 {{ total }} 条记录</span>
    <div>
      <button
        class="icon-btn"
        :disabled="page <= 1"
        aria-label="上一页"
        @click="$emit('change', page - 1)"
      >
        <Icon name="ChevronLeft" :size="17" /></button
      ><span>{{ page }} / {{ Math.max(1, Math.ceil(total / size)) }}</span
      ><button
        class="icon-btn"
        :disabled="page * size >= total"
        aria-label="下一页"
        @click="$emit('change', page + 1)"
      >
        <Icon name="ChevronRight" :size="17" />
      </button>
    </div>
  </div>
</template>
