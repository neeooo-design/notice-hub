<script setup>
import { ref, onMounted, computed } from 'vue';
import Icon from '../components/Icon.vue';
import { api } from '../state.js';
const roles = ref([]),
  loading = ref(true),
  error = ref('');
const permissions = computed(() => {
  const map = new Map();
  roles.value.forEach((r) => r.permissions.forEach((p) => map.set(p.code, p)));
  return [...map.values()];
});
async function load() {
  loading.value = true;
  error.value = '';
  try {
    roles.value = await api('/roles');
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">THE RIGHT ACCESS FOR EVERYONE</div>
      <h1>角色权限</h1>
      <p>清晰的职责边界，让每个人都能安心协作。</p>
    </div>
    <span class="subtle-badge"><Icon name="ShieldCheck" :size="16" />3 种预设角色</span>
  </div>
  <div v-if="error" class="error-panel">
    {{ error }}<button class="btn" @click="load">重试</button>
  </div>
  <div v-else-if="loading" class="loading-panel"><span class="spinner"></span>正在加载角色…</div>
  <template v-else
    ><div class="role-cards">
      <article v-for="r in roles" :key="r.id" class="panel role-card">
        <div class="role-card-top">
          <span
            class="stat-icon"
            :class="r.code === 'ADMIN' ? 'lavender' : r.code === 'EDITOR' ? 'blue' : 'peach'"
            ><Icon
              :name="
                r.code === 'ADMIN' ? 'ShieldCheck' : r.code === 'EDITOR' ? 'PenLine' : 'UserRound'
              "
              :size="24" /></span
          ><span class="tag tag-neutral">{{ r.memberCount }} 位成员</span>
        </div>
        <h2>
          {{ r.name }}<span>{{ r.code }}</span>
        </h2>
        <p>{{ r.description }}</p>
        <div class="role-card-footer">
          <span>{{ r.permissions.length }} 项操作权限</span
          ><span class="status-label enabled"><span></span>已启用</span>
        </div>
      </article>
    </div>
    <section class="panel permission-panel">
      <div class="panel-heading">
        <div>
          <h2>权限一览</h2>
          <p>角色决定允许执行的操作，数据归属决定允许操作的内容。</p>
        </div>
        <Icon name="KeyRound" :size="20" />
      </div>
      <div class="table-scroll">
        <table class="permission-table">
          <thead>
            <tr>
              <th>操作权限</th>
              <th v-for="r in roles" :key="r.id">{{ r.name }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in permissions" :key="p.code">
              <td>
                <strong>{{ p.name }}</strong
                ><small class="permission-code">{{ p.code }}</small>
              </td>
              <td v-for="r in roles" :key="r.id">
                <span
                  v-if="r.permissions.some((x) => x.code === p.code)"
                  class="permission-check"
                  role="img"
                  aria-label="允许"
                  ><Icon name="Check" :size="17" /></span
                ><span v-else class="permission-dash" aria-label="不允许">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <div class="bottom-note">
      <Icon name="Info" :size="16" />角色及其权限由系统预设。请在用户管理中为成员分配角色。
    </div></template
  >
</template>
