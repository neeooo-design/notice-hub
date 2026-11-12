<script setup>
import { ref, onMounted } from 'vue';
import Icon from '../components/Icon.vue';
import Pagination from '../components/Pagination.vue';
import { api, dateTime, actionNames } from '../state.js';
const list = ref({ items: [], total: 0 }),
  page = ref(1),
  q = ref(''),
  action = ref(''),
  loading = ref(true),
  error = ref('');
async function load(reset = false) {
  if (reset) page.value = 1;
  loading.value = true;
  error.value = '';
  try {
    list.value = await api(
      '/audit-logs?' +
        new URLSearchParams({ q: q.value, action: action.value, page: page.value, size: 12 }),
    );
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
      <div class="eyebrow">A CLEAR RECORD OF WHAT HAPPENED</div>
      <h1>审计日志</h1>
      <p>关键操作有迹可循，工作空间更透明。</p>
    </div>
    <span class="subtle-badge"><Icon name="History" :size="16" />{{ list.total }} 条记录</span>
  </div>
  <section class="panel">
    <div class="table-toolbar">
      <form class="search-field" @submit.prevent="load(true)">
        <Icon name="Search" :size="18" /><input
          v-model="q"
          aria-label="搜索操作人或对象"
          placeholder="搜索操作人或操作对象…"
          maxlength="40"
        /><button class="btn btn-small" type="submit">搜索</button>
      </form>
      <select v-model="action" aria-label="操作类型" class="filter-select" @change="load(true)">
        <option value="">全部操作类型</option>
        <option v-for="(label, value) in actionNames" :key="value" :value="value">
          {{ label }}
        </option>
      </select>
    </div>
    <div v-if="error" class="error-panel">
      {{ error }}<button class="btn" @click="load()">重试</button>
    </div>
    <div v-else-if="loading" class="loading-panel"><span class="spinner"></span>正在加载记录…</div>
    <div v-else class="table-scroll">
      <table class="audit-table">
        <thead>
          <tr>
            <th>操作时间</th>
            <th>操作人</th>
            <th>操作类型</th>
            <th>操作对象 / 说明</th>
            <th>结果</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in list.items" :key="item.id">
            <td class="muted mono small-text">{{ dateTime(item.createdAt) }}</td>
            <td>
              <span class="audit-actor"
                ><span class="mini-avatar">{{ item.actorName.slice(-1) }}</span
                >{{ item.actorName }}</span
              >
            </td>
            <td>
              <span class="tag tag-neutral">{{ actionNames[item.action] || item.action }}</span>
            </td>
            <td>
              <strong>{{ item.target }}</strong
              ><small class="audit-detail">{{ item.detail }}</small>
            </td>
            <td>
              <span class="result-pill" :class="item.outcome === 'SUCCESS' ? 'success' : 'failed'"
                ><Icon
                  :name="item.outcome === 'SUCCESS' ? 'CircleCheck' : 'CircleX'"
                  :size="13"
                />{{ item.outcome === 'SUCCESS' ? '成功' : '失败' }}</span
              >
            </td>
          </tr>
          <tr v-if="!list.items.length">
            <td colspan="5" class="empty-table">没有找到相关操作记录</td>
          </tr>
        </tbody>
      </table>
    </div>
    <Pagination
      :page="page"
      :size="12"
      :total="list.total"
      @change="
        page = $event;
        load();
      "
    />
  </section>
  <div class="bottom-note">
    <Icon name="LockKeyhole" :size="16" />日志仅记录操作信息，不记录密码和完整登录凭证。
  </div>
</template>
