<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Icon from '../components/Icon.vue';
import Modal from '../components/Modal.vue';
import Pagination from '../components/Pagination.vue';
import {
  auth,
  can,
  api,
  categories,
  dateTime,
  toast,
  errorToast,
  confirmAction,
} from '../state.js';
const route = useRoute(),
  router = useRouter();
const list = ref({ items: [], total: 0, page: 1, size: 9 }),
  page = ref(1),
  q = ref(''),
  category = ref(''),
  mine = ref(false),
  loading = ref(true),
  error = ref(''),
  view = ref('grid');
const detail = ref(null),
  editing = ref(null),
  form = ref(null),
  busy = ref(false),
  formError = ref('');
let sequence = 0;
function editable(n) {
  return can('notice:manage') || (can('notice:update:own') && n.createdBy === auth.user?.id);
}
function deletable(n) {
  return can('notice:manage') || (can('notice:delete:own') && n.createdBy === auth.user?.id);
}
async function load(reset = false) {
  if (reset) page.value = 1;
  const s = ++sequence;
  loading.value = true;
  error.value = '';
  try {
    const result = await api(
      '/notices?' +
        new URLSearchParams({
          page: page.value,
          size: 9,
          q: q.value,
          category: category.value,
          mine: mine.value,
        }),
    );
    if (s === sequence) list.value = result;
  } catch (e) {
    if (s === sequence) error.value = e.message;
  } finally {
    if (s === sequence) loading.value = false;
  }
}
async function open(id) {
  try {
    detail.value = await api('/notices/' + id);
  } catch (e) {
    errorToast(e);
  }
}
function closeDetail() {
  detail.value = null;
  if (route.query.id) router.replace({ path: '/notices', query: {} });
}
function edit(n = null) {
  detail.value = null;
  editing.value = n;
  form.value = n
    ? {
        title: n.title,
        content: n.content,
        category: n.category,
        pinned: n.pinned,
        version: n.version,
      }
    : { title: '', content: '', category: '公司动态', pinned: false, version: null };
  formError.value = '';
}
async function save() {
  if (busy.value) return;
  busy.value = true;
  formError.value = '';
  try {
    await api(editing.value ? '/notices/' + editing.value.id : '/notices', {
      method: editing.value ? 'PUT' : 'POST',
      body: form.value,
    });
    form.value = null;
    toast(editing.value ? '通知已更新' : '通知已发布');
    await load();
  } catch (e) {
    formError.value = e.message;
  } finally {
    busy.value = false;
  }
}
async function remove(n) {
  if (!(await confirmAction('删除这条通知？', `“${n.title}”将从通知中心移除。`, true))) return;
  try {
    await api('/notices/' + n.id, { method: 'DELETE' });
    detail.value = null;
    toast('通知已删除');
    if (list.value.items.length === 1 && page.value > 1) page.value--;
    await load();
  } catch (e) {
    errorToast(e);
  }
}
onMounted(async () => {
  await load();
  if (route.query.id) open(route.query.id);
});
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">STAY IN THE LOOP</div>
      <h1>
        通知中心<span class="heading-count">{{ list.total }}</span>
      </h1>
      <p>重要的消息、有趣的分享，都在这里。</p>
    </div>
    <button v-if="can('notice:create')" class="btn btn-primary" @click="edit()">
      <Icon name="Plus" :size="18" />发布通知
    </button>
  </div>
  <div class="notice-controls">
    <div class="tabs">
      <button
        :class="{ active: !mine }"
        @click="
          mine = false;
          load(true);
        "
      >
        全部通知</button
      ><button
        :class="{ active: mine }"
        @click="
          mine = true;
          load(true);
        "
      >
        我的发布
      </button>
    </div>
    <div class="view-toggle">
      <button
        class="icon-btn"
        :class="{ active: view === 'grid' }"
        aria-label="卡片视图"
        @click="view = 'grid'"
      >
        <Icon name="Grid2X2" :size="17" /></button
      ><button
        class="icon-btn"
        :class="{ active: view === 'list' }"
        aria-label="列表视图"
        @click="view = 'list'"
      >
        <Icon name="List" :size="18" />
      </button>
    </div>
  </div>
  <div class="filter-bar">
    <form class="search-field" @submit.prevent="load(true)">
      <Icon name="Search" :size="18" /><input
        v-model="q"
        aria-label="搜索通知"
        placeholder="搜索通知标题或内容…"
        maxlength="120"
      /><button
        v-if="q"
        type="button"
        class="icon-btn"
        aria-label="清除搜索"
        @click="
          q = '';
          load(true);
        "
      >
        <Icon name="X" :size="15" /></button
      ><button class="btn btn-small" type="submit">搜索</button>
    </form>
    <div class="category-filters">
      <button
        :class="{ active: !category }"
        @click="
          category = '';
          load(true);
        "
      >
        全部分类</button
      ><button
        v-for="c in categories"
        :key="c"
        :class="{ active: category === c }"
        @click="
          category = c;
          load(true);
        "
      >
        {{ c }}
      </button>
    </div>
  </div>
  <div v-if="error" class="error-panel">
    {{ error }}<button class="btn" @click="load()">重试</button>
  </div>
  <div v-else-if="loading" class="loading-panel"><span class="spinner"></span>正在加载通知…</div>
  <div v-else-if="!list.items.length" class="panel empty-state">
    <span class="empty-icon"><Icon name="Inbox" :size="36" /></span>
    <h3>{{ q || category ? '没有找到相关通知' : '这里还没有通知' }}</h3>
    <p>
      {{
        q || category
          ? '试试其他关键词或分类。'
          : mine
            ? '你发布的通知将显示在这里。'
            : '发布第一条通知，和团队分享新消息。'
      }}
    </p>
    <button v-if="can('notice:create') && !q && !category" class="btn btn-primary" @click="edit()">
      发布通知
    </button>
  </div>
  <div v-else class="notices-grid" :class="{ 'list-mode': view === 'list' }">
    <article v-for="n in list.items" :key="n.id" class="notice-card">
      <div class="notice-card-top">
        <span
          class="tag"
          :class="{
            'tag-blue': n.category === '技术分享',
            'tag-orange': n.category === '活动资讯',
            'tag-green': n.category === '行政通知',
          }"
          >{{ n.category }}</span
        ><span v-if="n.pinned" class="pin-label"><Icon name="Pin" :size="13" />置顶</span
        ><span class="card-top-spacer"></span
        ><button v-if="editable(n)" class="icon-btn subtle" aria-label="编辑通知" @click="edit(n)">
          <Icon name="Pencil" :size="15" /></button
        ><button
          v-if="deletable(n)"
          class="icon-btn subtle danger-hover"
          aria-label="删除通知"
          @click="remove(n)"
        >
          <Icon name="Trash2" :size="15" />
        </button>
      </div>
      <button class="notice-card-body" @click="open(n.id)">
        <h2>{{ n.title }}</h2>
        <p>{{ n.content }}</p>
      </button>
      <div class="notice-card-footer">
        <span class="author"
          ><span class="mini-avatar">{{ n.authorName?.slice(-1) }}</span
          >{{ n.authorName }}</span
        ><time>{{ dateTime(n.createdAt, true) }}</time
        ><button class="icon-btn" aria-label="阅读通知" @click="open(n.id)">
          <Icon name="ArrowUpRight" :size="17" />
        </button>
      </div>
    </article>
  </div>
  <Pagination
    v-if="list.total > 0"
    :page="page"
    :total="list.total"
    :size="9"
    @change="
      page = $event;
      load();
    "
  />
  <Modal v-if="detail" title="通知详情" wide @close="closeDetail"
    ><div class="notice-detail">
      <div class="detail-meta">
        <span class="tag">{{ detail.category }}</span
        ><span v-if="detail.pinned" class="pin-label"><Icon name="Pin" :size="13" />置顶</span>
      </div>
      <h1>{{ detail.title }}</h1>
      <div class="detail-byline">
        <span class="avatar small">{{ detail.authorName?.slice(-1) }}</span
        ><span>{{ detail.authorName }}</span
        ><span>发布于 {{ dateTime(detail.createdAt) }}</span>
      </div>
      <div class="notice-content">{{ detail.content }}</div>
      <div class="detail-bottom">
        <span>最后更新 {{ dateTime(detail.updatedAt) }}</span
        ><button v-if="editable(detail)" class="btn" @click="edit(detail)">
          <Icon name="Pencil" :size="16" />编辑通知
        </button>
      </div>
    </div></Modal
  >
  <Modal
    v-if="form"
    :title="editing ? '编辑通知' : '发布新通知'"
    wide
    :busy="busy"
    @close="form = null"
    ><form class="stack-form" @submit.prevent="save">
      <div class="form-note">
        <Icon name="Info" :size="17" />通知发布后，所有团队成员都可以阅读。
      </div>
      <div>
        <label for="notice-title">通知标题<span class="required">*</span></label
        ><input
          id="notice-title"
          v-model="form.title"
          placeholder="用一个清晰的标题概括这条通知"
          required
          maxlength="120"
        /><small class="field-hint">{{ form.title.length }} / 120</small>
      </div>
      <div>
        <label for="notice-category">通知分类</label
        ><select id="notice-category" v-model="form.category">
          <option v-for="c in categories" :key="c">{{ c }}</option>
        </select>
      </div>
      <div>
        <label for="notice-content">正文<span class="required">*</span></label
        ><textarea
          id="notice-content"
          v-model="form.content"
          placeholder="写下需要与团队分享的内容…"
          rows="9"
          required
          maxlength="20000"
        ></textarea
        ><small class="field-hint">支持纯文本和换行 · {{ form.content.length }} / 20000</small>
      </div>
      <label v-if="can('notice:manage')" class="checkbox-label"
        ><input v-model="form.pinned" type="checkbox" />置顶这条通知</label
      >
      <div v-if="formError" class="inline-error" role="alert">{{ formError }}</div>
      <div class="form-actions">
        <button class="btn" type="button" :disabled="busy" @click="form = null">取消</button
        ><button class="btn btn-primary" :disabled="busy">
          {{ busy ? '正在保存…' : editing ? '保存修改' : '发布通知' }}
        </button>
      </div>
    </form></Modal
  >
</template>
