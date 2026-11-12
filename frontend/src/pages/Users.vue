<script setup>
import { ref, onMounted } from 'vue';
import Icon from '../components/Icon.vue';
import Modal from '../components/Modal.vue';
import Pagination from '../components/Pagination.vue';
import { auth, api, can, roleNames, dateTime, toast, errorToast, confirmAction } from '../state.js';
const list = ref({ items: [], total: 0 }),
  page = ref(1),
  q = ref(''),
  loading = ref(true),
  error = ref(''),
  modal = ref(''),
  selected = ref(null),
  form = ref({}),
  busy = ref(false),
  formError = ref('');
async function load(reset = false) {
  if (reset) page.value = 1;
  loading.value = true;
  error.value = '';
  try {
    list.value = await api(
      '/users?' + new URLSearchParams({ q: q.value, page: page.value, size: 10 }),
    );
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
function open(type, u = null) {
  selected.value = u;
  modal.value = type;
  form.value =
    type === 'create'
      ? { username: '', displayName: '', password: '', roles: ['EMPLOYEE'] }
      : type === 'roles'
        ? { roles: [...u.roles] }
        : { displayName: u.displayName };
  formError.value = '';
}
async function save() {
  if (busy.value) return;
  busy.value = true;
  formError.value = '';
  try {
    await api(
      modal.value === 'create'
        ? '/users'
        : `/users/${selected.value.id}${modal.value === 'roles' ? '/roles' : ''}`,
      { method: modal.value === 'create' ? 'POST' : 'PUT', body: form.value },
    );
    modal.value = '';
    toast('用户信息已保存');
    await load();
  } catch (e) {
    formError.value = e.message;
  } finally {
    busy.value = false;
  }
}
async function toggle(u) {
  if (
    !(await confirmAction(
      u.enabled ? '停用这个账号？' : '启用这个账号？',
      u.enabled
        ? `${u.displayName} 将立即退出登录，账号启用后才能再次访问。`
        : `${u.displayName} 将恢复登录和使用权限。`,
      u.enabled,
    ))
  )
    return;
  try {
    await api(`/users/${u.id}/enabled`, { method: 'PUT', body: { enabled: !u.enabled } });
    toast(u.enabled ? '账号已停用' : '账号已启用');
    await load();
  } catch (e) {
    errorToast(e);
  }
}
onMounted(load);
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">PEOPLE MAKE THE WORKSPACE</div>
      <h1>
        用户管理<span class="heading-count">{{ list.total }}</span>
      </h1>
      <p>管理团队成员，给每个人合适的访问权限。</p>
    </div>
    <button v-if="can('user:create')" class="btn btn-primary" @click="open('create')">
      <Icon name="UserPlus" :size="18" />添加用户
    </button>
  </div>
  <section class="panel">
    <div class="table-toolbar">
      <form class="search-field" @submit.prevent="load(true)">
        <Icon name="Search" :size="18" /><input
          v-model="q"
          placeholder="搜索用户名或姓名…"
          aria-label="搜索用户"
          maxlength="40"
        /><button type="submit" class="btn btn-small">搜索</button>
      </form>
      <span class="muted small-text">账号由管理员统一维护</span>
    </div>
    <div v-if="error" class="error-panel">
      {{ error }}<button class="btn" @click="load()">重试</button>
    </div>
    <div v-else-if="loading" class="loading-panel">
      <span class="spinner"></span>正在加载团队成员…
    </div>
    <div v-else class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>成员</th>
            <th>用户名</th>
            <th>角色</th>
            <th>账号状态</th>
            <th>加入时间</th>
            <th class="align-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in list.items" :key="u.id">
            <td>
              <div class="user-cell">
                <span
                  class="avatar"
                  :class="{
                    'avatar-peach': u.roles.includes('EDITOR'),
                    'avatar-green': u.roles.includes('EMPLOYEE'),
                  }"
                  >{{ u.displayName.slice(-1) }}</span
                >
                <div>
                  <strong>{{ u.displayName }}</strong
                  ><small v-if="u.protectedAccount">初始管理员</small
                  ><small v-else-if="u.id === auth.user?.id">当前账号</small>
                </div>
              </div>
            </td>
            <td class="mono">{{ u.username }}</td>
            <td>
              <span
                v-for="r in u.roles"
                :key="r"
                class="tag role-tag"
                :class="{ 'tag-blue': r === 'EDITOR', 'tag-neutral': r === 'EMPLOYEE' }"
                >{{ roleNames[r] }}</span
              >
            </td>
            <td>
              <span class="status-label" :class="u.enabled ? 'enabled' : 'disabled'"
                ><span></span>{{ u.enabled ? '正常' : '已停用' }}</span
              >
            </td>
            <td class="muted small-text">{{ dateTime(u.createdAt, true) }}</td>
            <td>
              <div class="row-actions">
                <button
                  v-if="can('user:update')"
                  class="icon-btn"
                  aria-label="编辑用户"
                  title="编辑用户"
                  @click="open('edit', u)"
                >
                  <Icon name="Pencil" :size="16" /></button
                ><template v-if="!u.protectedAccount && u.id !== auth.user?.id"
                  ><button
                    v-if="can('role:assign')"
                    class="icon-btn"
                    aria-label="分配角色"
                    title="分配角色"
                    @click="open('roles', u)"
                  >
                    <Icon name="ShieldCheck" :size="17" /></button
                  ><button
                    v-if="can('user:update')"
                    class="icon-btn"
                    :aria-label="u.enabled ? '停用账号' : '启用账号'"
                    :title="u.enabled ? '停用账号' : '启用账号'"
                    @click="toggle(u)"
                  >
                    <Icon
                      :name="u.enabled ? 'CirclePause' : 'CirclePlay'"
                      :size="17"
                    /></button></template
                ><span v-else class="protected-label"><Icon name="LockKeyhole" :size="13" /></span>
              </div>
            </td>
          </tr>
          <tr v-if="!list.items.length">
            <td colspan="6" class="empty-table">没有找到匹配的团队成员</td>
          </tr>
        </tbody>
      </table>
    </div>
    <Pagination
      :page="page"
      :size="10"
      :total="list.total"
      @change="
        page = $event;
        load();
      "
    />
  </section>
  <div class="bottom-note">
    <Icon
      name="ShieldCheck"
      :size="16"
    />初始管理员受到保护，无法停用或变更角色；普通用户无法自行提权。
  </div>
  <Modal
    v-if="modal"
    :title="modal === 'create' ? '添加团队成员' : modal === 'roles' ? '分配角色' : '编辑成员资料'"
    :busy="busy"
    @close="modal = ''"
    ><form class="stack-form" @submit.prevent="save">
      <template v-if="modal === 'create'"
        ><div>
          <label for="user-name">用户名<span class="required">*</span></label
          ><input
            id="user-name"
            v-model="form.username"
            required
            pattern="[a-zA-Z][a-zA-Z0-9_]{2,31}"
            maxlength="32"
            placeholder="例如：zhangsan"
          /><small class="field-hint">3—32 位，以字母开头，可含数字和下划线</small>
        </div></template
      >
      <div v-if="modal !== 'roles'">
        <label for="display-name">显示姓名<span class="required">*</span></label
        ><input
          id="display-name"
          v-model="form.displayName"
          required
          maxlength="40"
          placeholder="成员在工作空间中显示的姓名"
        />
      </div>
      <div v-if="modal === 'create'">
        <label for="initial-password">初始密码<span class="required">*</span></label
        ><input
          id="initial-password"
          v-model="form.password"
          type="password"
          autocomplete="new-password"
          required
          minlength="10"
          maxlength="128"
          placeholder="至少10位，包含字母和数字"
        /><small class="field-hint">通过合适的渠道将初始密码告知成员。</small>
      </div>
      <div v-if="modal === 'create' || modal === 'roles'">
        <label>分配角色<span class="required">*</span></label>
        <p v-if="selected" class="muted small-text">
          为 {{ selected.displayName }} 选择角色；调整后将在下一次请求生效。
        </p>
        <label v-for="(name, code) in roleNames" :key="code" class="role-option"
          ><input v-model="form.roles" type="checkbox" :value="code" />
          <div>
            <strong>{{ name }}</strong
            ><small>{{
              code === 'EMPLOYEE'
                ? '阅读通知、维护个人资料'
                : code === 'EDITOR'
                  ? '发布通知、编辑和删除本人通知'
                  : '管理全部通知、团队成员与角色'
            }}</small>
          </div></label
        ><small class="field-hint">可以分配多个角色，其权限取并集。</small>
      </div>
      <div v-if="formError" class="inline-error" role="alert">{{ formError }}</div>
      <div class="form-actions">
        <button class="btn" type="button" :disabled="busy" @click="modal = ''">取消</button
        ><button
          class="btn btn-primary"
          :disabled="busy || ((modal === 'create' || modal === 'roles') && !form.roles.length)"
        >
          {{ busy ? '正在保存…' : '保存' }}
        </button>
      </div>
    </form></Modal
  >
</template>
