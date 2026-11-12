<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue';
import Icon from './Icon.vue';
const props = defineProps({ title: String, wide: Boolean, busy: Boolean });
const emit = defineEmits(['close']);
const panel = ref(null);
let previousFocus;
function close() {
  if (!props.busy) emit('close');
}
function keyboard(e) {
  if (e.key === 'Escape') close();
  if (e.key === 'Tab') {
    const items = panel.value?.querySelectorAll(
      'button:not([disabled]),input:not([disabled]),textarea,select,[tabindex="0"]',
    );
    if (!items?.length) return;
    const first = items[0],
      last = items[items.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  }
}
onMounted(async () => {
  previousFocus = document.activeElement;
  document.body.style.overflow = 'hidden';
  window.addEventListener('keydown', keyboard);
  await nextTick();
  panel.value?.querySelector('input,textarea,select,button')?.focus();
});
onUnmounted(() => {
  document.body.style.overflow = '';
  window.removeEventListener('keydown', keyboard);
  previousFocus?.focus();
});
</script>
<template>
  <Teleport to="body"
    ><div class="nh-dialog-layer" @click.self="close">
      <section
        ref="panel"
        class="modal"
        :class="{ wide }"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
      >
        <header class="modal-header">
          <h2>{{ title }}</h2>
          <button
            type="button"
            class="icon-btn"
            aria-label="关闭弹窗"
            :disabled="busy"
            @click="close"
          >
            <Icon name="X" />
          </button>
        </header>
        <div class="modal-body"><slot /></div>
      </section></div
  ></Teleport>
</template>
