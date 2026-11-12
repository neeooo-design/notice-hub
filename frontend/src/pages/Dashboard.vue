<script setup>
import { ref, onMounted, computed } from 'vue';
import Icon from '../components/Icon.vue';
import { auth, can, api, dateTime, actionNames, errorToast } from '../state.js';
const data = ref(null),
  loading = ref(true),
  error = ref('');
const today = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long',
}).format(new Date());
const greeting = computed(() =>
  new Date().getHours() < 12 ? '上午好' : new Date().getHours() < 18 ? '下午好' : '晚上好',
);
async function load() {
  loading.value = true;
  error.value = '';
  try {
    data.value = await api('/dashboard');
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
      <div class="eyebrow">YOUR WORKSPACE, AT A GLANCE</div>
      <h1>工作台</h1>
      <p>从这里开始，和团队保持同步。</p>
    </div>
    <div class="date-label"><Icon name="CalendarDays" :size="16" />{{ today }}</div>
  </div>
  <div v-if="error" class="error-panel">
    {{ error }} <button class="btn" @click="load">重试</button>
  </div>
  <template v-else-if="data"
    ><section class="welcome-banner">
      <div>
        <span class="banner-kicker">新的一天，新的连接</span>
        <h2>{{ greeting }}，{{ auth.user?.displayName }} <span class="wave">✳</span></h2>
        <p>团队共有 {{ data.noticeCount }} 条通知。花一点时间，了解正在发生的事。</p>
        <RouterLink class="btn btn-white" to="/notices"
          >前往通知中心<Icon name="ArrowUpRight" :size="17"
        /></RouterLink>
      </div>
      <div class="banner-art" aria-hidden="true">
        <div class="banner-circle"></div>
        <div class="banner-sheet">
          <Icon name="BellRing" :size="34" /><span></span><span></span><i></i>
        </div>
        <div class="banner-stamp"><Icon name="Check" :size="22" /></div>
      </div>
    </section>
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-top">
          <span>全部通知</span><span class="stat-icon lavender"><Icon name="Files" /></span>
        </div>
        <strong>{{ data.noticeCount }}<small>条</small></strong>
        <p>团队共享的信息与动态</p>
      </div>
      <div class="stat-card">
        <div class="stat-top">
          <span>我的发布</span><span class="stat-icon blue"><Icon name="Send" /></span>
        </div>
        <strong>{{ data.myNoticeCount }}<small>条</small></strong>
        <p>{{ can('notice:create') ? '你的每一次分享都很重要' : '阅读与发现，和团队保持同步' }}</p>
      </div>
      <div class="stat-card">
        <div class="stat-top">
          <span>{{ can('user:read') ? '团队成员' : '我的角色' }}</span
          ><span class="stat-icon peach"><Icon name="Users" /></span>
        </div>
        <strong v-if="can('user:read')">{{ data.userCount }}<small>人</small></strong
        ><strong v-else class="stat-word">{{
          auth.user?.roles?.includes('EDITOR') ? '编辑' : '员工'
        }}</strong>
        <p>
          {{
            can('user:read')
              ? `${data.activeUserCount} 位成员的账号正在使用`
              : '各司其职，让协作更顺畅'
          }}
        </p>
      </div>
    </div>
    <div class="dashboard-grid">
      <section class="panel">
        <div class="panel-heading">
          <div>
            <h2>最新通知</h2>
            <p>团队里值得了解的新鲜事</p>
          </div>
          <RouterLink to="/notices" class="text-link"
            >查看全部<Icon name="ArrowRight" :size="15"
          /></RouterLink>
        </div>
        <div v-if="!data.recent.length" class="empty-state">
          <Icon name="Inbox" :size="36" />
          <h3>还没有通知</h3>
          <p>第一条团队动态，即将从这里开始。</p>
        </div>
        <RouterLink
          v-for="item in data.recent"
          :key="item.id"
          :to="{ path: '/notices', query: { id: item.id } }"
          class="recent-notice"
          ><span
            class="notice-square"
            :class="
              item.category === '技术分享'
                ? 'blue'
                : item.category === '活动资讯'
                  ? 'peach'
                  : 'lavender'
            "
            ><Icon
              :name="
                item.category === '技术分享'
                  ? 'Code2'
                  : item.category === '活动资讯'
                    ? 'Sparkles'
                    : 'Megaphone'
              "
              :size="20"
          /></span>
          <div>
            <h3>{{ item.title }}<span v-if="item.pinned" class="tiny-pin">置顶</span></h3>
            <p>
              {{ item.authorName }}<span>·</span>{{ dateTime(item.createdAt, true) }}<span>·</span
              >{{ item.category }}
            </p>
          </div>
          <Icon name="ChevronRight" :size="17"
        /></RouterLink>
      </section>
      <section v-if="can('audit:read')" class="panel">
        <div class="panel-heading">
          <div>
            <h2>最近动态</h2>
            <p>工作空间的活动记录</p>
          </div>
          <RouterLink to="/audit" class="text-link"
            ><Icon name="ArrowUpRight" :size="18"
          /></RouterLink>
        </div>
        <div class="activity-list">
          <div v-for="item in data.recentActivity" :key="item.id" class="activity-item">
            <span class="activity-dot" :class="{ failed: item.outcome === 'FAILED' }"></span>
            <div>
              <p>
                <strong>{{ item.actorName }}</strong> {{ actionNames[item.action] || item.action }}
              </p>
              <small>{{ dateTime(item.createdAt) }}</small>
            </div>
          </div>
          <p v-if="!data.recentActivity.length" class="muted">暂无活动记录</p>
        </div>
      </section>
      <section v-else class="panel team-tip">
        <span class="stat-icon lavender"><Icon name="MessagesSquare" :size="25" /></span>
        <h2>让好信息，<br />遇见对的人。</h2>
        <p>在通知中心查看团队动态，<br />在个人设置中维护你的资料。</p>
        <RouterLink to="/profile" class="text-link"
          >完善个人资料<Icon name="ArrowRight" :size="16"
        /></RouterLink>
      </section></div
  ></template>
  <div v-else-if="loading" class="loading-panel">
    <span class="spinner"></span>正在加载工作空间…
  </div>
</template>
