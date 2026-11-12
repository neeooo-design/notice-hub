import { createApp } from 'vue';
import { createRouter, createWebHashHistory } from 'vue-router';
import App from './App.vue';
import Login from './pages/Login.vue';
import Dashboard from './pages/Dashboard.vue';
import Notices from './pages/Notices.vue';
import Users from './pages/Users.vue';
import Roles from './pages/Roles.vue';
import Audit from './pages/Audit.vue';
import Profile from './pages/Profile.vue';
import { auth, refreshMe, can, clearAuth } from './state.js';
import './style.css';

const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || { left: 0, top: 0 };
  },
  routes: [
    { path: '/login', component: Login, meta: { title: '登录' } },
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', component: Dashboard, meta: { title: '工作台' } },
    { path: '/notices', component: Notices, meta: { title: '通知中心' } },
    { path: '/users', component: Users, meta: { title: '用户管理', permission: 'user:read' } },
    { path: '/roles', component: Roles, meta: { title: '角色权限', permission: 'role:read' } },
    { path: '/audit', component: Audit, meta: { title: '审计日志', permission: 'audit:read' } },
    { path: '/profile', component: Profile, meta: { title: '个人设置' } },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
});
router.beforeEach(async (to) => {
  if (to.path === '/login') return auth.token ? '/dashboard' : true;
  if (!auth.token) return '/login';
  try {
    await refreshMe();
  } catch {
    clearAuth();
    return '/login';
  }
  if (to.meta.permission && !can(to.meta.permission)) return '/dashboard';
});
router.afterEach((to) => {
  document.title = `${to.meta.title} · Notice Hub`;
});
window.addEventListener('auth-expired', () => router.replace('/login'));
createApp(App).use(router).mount('#app');
