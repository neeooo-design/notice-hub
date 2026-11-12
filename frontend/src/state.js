import { reactive } from 'vue';

export const auth = reactive({
  token: sessionStorage.getItem('noticehub-token') || '',
  user: null,
});
export const ui = reactive({ toasts: [], confirmation: null });
export const roleNames = { EMPLOYEE: '员工', EDITOR: '编辑', ADMIN: '管理员' };
export const categories = ['公司动态', '行政通知', '技术分享', '活动资讯'];
export const actionNames = {
  LOGIN: '账号登录',
  LOGOUT: '退出登录',
  USER_CREATE: '创建用户',
  USER_UPDATE: '编辑用户',
  USER_ENABLE: '启用用户',
  USER_DISABLE: '停用用户',
  ROLE_ASSIGN: '分配角色',
  NOTICE_CREATE: '发布通知',
  NOTICE_UPDATE: '编辑通知',
  NOTICE_DELETE: '删除通知',
  PROFILE_UPDATE: '更新资料',
  PASSWORD_CHANGE: '修改密码',
};
export function can(permission) {
  return auth.user?.permissions?.includes(permission) || false;
}
export function setAuth(data) {
  auth.token = data.token;
  auth.user = data.user;
  sessionStorage.setItem('noticehub-token', data.token);
}
export function clearAuth() {
  auth.token = '';
  auth.user = null;
  sessionStorage.removeItem('noticehub-token');
}
export async function api(path, options = {}) {
  let response;
  try {
    response = await fetch('/api' + path, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(auth.token ? { 'noticehub-token': 'Bearer ' + auth.token } : {}),
        ...options.headers,
      },
      signal: options.signal || AbortSignal.timeout(15000),
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new Error('连接服务失败，请检查服务是否启动后重试');
  }
  let body;
  try {
    body = await response.json();
  } catch {
    throw new Error('服务器返回了无法读取的响应');
  }
  if (!response.ok) {
    if (response.status === 401 && path !== '/auth/login') {
      clearAuth();
      window.dispatchEvent(new Event('auth-expired'));
    }
    if (response.status === 403 && path !== '/auth/login')
      window.dispatchEvent(new Event('permissions-changed'));
    const error = new Error(body.message || '操作未完成');
    error.status = response.status;
    throw error;
  }
  return body.data;
}
export async function refreshMe() {
  auth.user = await api('/auth/me');
  return auth.user;
}
export function toast(message, type = 'success') {
  const id = Date.now() + Math.random();
  ui.toasts.push({ id, message, type });
  setTimeout(() => (ui.toasts = ui.toasts.filter((t) => t.id !== id)), 4500);
}
export function confirmAction(title, message, danger = false) {
  return new Promise((resolve) => {
    ui.confirmation = { title, message, danger, resolve };
  });
}
export function resolveConfirm(result) {
  const c = ui.confirmation;
  ui.confirmation = null;
  c?.resolve(result);
}
export function dateTime(value, short = false) {
  if (!value) return '—';
  const d = new Date(value);
  return new Intl.DateTimeFormat(
    'zh-CN',
    short
      ? { month: '2-digit', day: '2-digit' }
      : {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          hour12: false,
        },
  ).format(d);
}
export function errorToast(error) {
  toast(error.message, 'error');
}
