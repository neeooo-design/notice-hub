<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Icon from './components/Icon.vue';
import Modal from './components/Modal.vue';
import {
  auth,
  ui,
  can,
  roleNames,
  api,
  clearAuth,
  refreshMe,
  errorToast,
  resolveConfirm,
} from './state.js';
const route = useRoute(),
  router = useRouter(),
  sidebarOpen = ref(false);
const navigation = computed(() => [
  { to: '/dashboard', label: '工作台', icon: 'LayoutDashboard' },
  { to: '/notices', label: '通知中心', icon: 'Bell' },
  ...(can('user:read') ? [{ to: '/users', label: '用户管理', icon: 'Users' }] : []),
  ...(can('role:read') ? [{ to: '/roles', label: '角色权限', icon: 'ShieldCheck' }] : []),
  ...(can('audit:read') ? [{ to: '/audit', label: '审计日志', icon: 'ScrollText' }] : []),
]);
const topRole = computed(
  () =>
    roleNames[
      auth.user?.roles?.includes('ADMIN')
        ? 'ADMIN'
        : auth.user?.roles?.includes('EDITOR')
          ? 'EDITOR'
          : 'EMPLOYEE'
    ],
);
async function logout() {
  try {
    await api('/auth/logout', { method: 'POST' });
    clearAuth();
    router.replace('/login');
  } catch (e) {
    errorToast(e);
  }
}
async function sync() {
  if (auth.token) {
    try {
      await refreshMe();
      if (route.meta.permission && !can(route.meta.permission)) router.replace('/dashboard');
    } catch {}
  }
}
let timer;
onMounted(() => {
  timer = setInterval(sync, 30000);
  window.addEventListener('permissions-changed', sync);
  document.addEventListener('visibilitychange', sync);
});
onUnmounted(() => {
  clearInterval(timer);
  window.removeEventListener('permissions-changed', sync);
  document.removeEventListener('visibilitychange', sync);
});
</script>
<template>
  <RouterView v-if="route.path === '/login'" />
  <div v-else class="app-shell">
    <div v-if="sidebarOpen" class="sidebar-scrim" @click="sidebarOpen = false"></div>
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <RouterLink to="/dashboard" class="brand"
        ><span class="brand-mark">N<span></span></span>
        <div>Notice Hub<small>让团队的信息流动起来</small></div></RouterLink
      >
      <div class="workspace-label">团队工作空间 <span>WORKSPACE</span></div>
      <nav aria-label="主导航">
        <RouterLink
          v-for="item in navigation"
          :key="item.to"
          :to="item.to"
          class="nav-link"
          @click="sidebarOpen = false"
          ><Icon :name="item.icon" :size="19" /><span>{{ item.label }}</span
          ><span v-if="route.path === item.to" class="nav-dot"></span
        ></RouterLink>
      </nav>
      <div class="sidebar-bottom">
        <div class="workspace-note">
          <span class="status-dot"></span> 内部协作空间 <Icon name="LockKeyhole" :size="13" />
        </div>
        <RouterLink to="/profile" class="nav-link" @click="sidebarOpen = false"
          ><Icon name="Settings2" :size="19" />个人设置</RouterLink
        ><button class="nav-link logout" @click="logout">
          <Icon name="LogOut" :size="19" />退出登录
        </button>
      </div>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <div class="breadcrumbs">
          <button class="icon-btn mobile-menu" aria-label="打开导航" @click="sidebarOpen = true">
            <Icon name="Menu" /></button
          ><span>工作空间</span><Icon name="ChevronRight" :size="14" /><strong>{{
            route.meta.title
          }}</strong>
        </div>
        <div class="topbar-right">
          <span class="workspace-pill"><span class="status-dot"></span> 团队版</span
          ><span class="topbar-divider"></span
          ><RouterLink to="/profile" class="account-chip"
            ><span class="avatar">{{ auth.user?.displayName?.slice(-1) }}</span>
            <div>
              <strong>{{ auth.user?.displayName }}</strong
              ><small>{{ topRole }}</small>
            </div>
            <Icon name="ChevronDown" :size="14"
          /></RouterLink>
        </div>
      </header>
      <main class="page-content"><RouterView :key="route.path" /></main>
      <footer class="page-footer"><span>Notice Hub</span><span>连接信息，连接团队。</span></footer>
    </div>
  </div>
  <div class="toast-stack" aria-live="polite">
    <div v-for="item in ui.toasts" :key="item.id" class="toast" :class="item.type">
      <Icon :name="item.type === 'error' ? 'CircleAlert' : 'CircleCheck'" :size="19" /><span>{{
        item.message
      }}</span>
    </div>
  </div>
  <Modal v-if="ui.confirmation" :title="ui.confirmation.title" @close="resolveConfirm(false)"
    ><p class="confirm-message">{{ ui.confirmation.message }}</p>
    <div class="form-actions">
      <button class="btn" @click="resolveConfirm(false)">取消</button
      ><button
        class="btn"
        :class="ui.confirmation.danger ? 'btn-danger' : 'btn-primary'"
        @click="resolveConfirm(true)"
      >
        确认
      </button>
    </div></Modal
  >
</template>
