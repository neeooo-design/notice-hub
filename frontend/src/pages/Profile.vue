<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '../components/Icon.vue';
import { auth, api, clearAuth, roleNames, dateTime, toast } from '../state.js';
const router = useRouter(),
  name = ref(auth.user?.displayName || ''),
  saving = ref(false),
  changing = ref(false),
  profileError = ref(''),
  passwordError = ref(''),
  oldPassword = ref(''),
  newPassword = ref(''),
  confirmation = ref('');
async function save() {
  if (saving.value) return;
  saving.value = true;
  profileError.value = '';
  try {
    auth.user = await api('/auth/profile', { method: 'PUT', body: { displayName: name.value } });
    toast('个人资料已更新');
  } catch (e) {
    profileError.value = e.message;
  } finally {
    saving.value = false;
  }
}
async function password() {
  if (changing.value) return;
  passwordError.value = '';
  if (newPassword.value !== confirmation.value) {
    passwordError.value = '两次输入的新密码不一致';
    return;
  }
  changing.value = true;
  try {
    await api('/auth/password', {
      method: 'PUT',
      body: { oldPassword: oldPassword.value, newPassword: newPassword.value },
    });
    clearAuth();
    toast('密码已修改，请使用新密码登录');
    router.replace('/login');
  } catch (e) {
    passwordError.value = e.message;
  } finally {
    changing.value = false;
  }
}
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">MAKE YOURSELF AT HOME</div>
      <h1>个人设置</h1>
      <p>维护个人资料，让团队更好地认识你。</p>
    </div>
  </div>
  <div class="profile-grid">
    <aside class="panel profile-card">
      <span class="profile-avatar">{{ auth.user?.displayName?.slice(-1) }}</span>
      <h2>{{ auth.user?.displayName }}</h2>
      <p class="mono muted">@{{ auth.user?.username }}</p>
      <div class="profile-tags">
        <span v-for="r in auth.user?.roles" :key="r" class="tag">{{ roleNames[r] }}</span>
      </div>
      <div class="profile-info">
        <div>
          <Icon name="CalendarDays" :size="16" /><span
            >加入于 {{ dateTime(auth.user?.createdAt, true) }}</span
          >
        </div>
        <div><Icon name="ShieldCheck" :size="16" /><span>账号状态正常</span></div>
      </div>
    </aside>
    <div class="profile-forms">
      <section class="panel">
        <div class="panel-heading">
          <div>
            <h2>基本资料</h2>
            <p>显示姓名会出现在通知和团队成员列表中。</p>
          </div>
          <Icon name="UserRound" :size="20" />
        </div>
        <form class="stack-form padded-form" @submit.prevent="save">
          <div>
            <label for="profile-username">用户名</label
            ><input id="profile-username" :value="auth.user?.username" disabled /><small
              class="field-hint"
              >用户名用于登录，创建后保持不变。</small
            >
          </div>
          <div>
            <label for="profile-name">显示姓名</label
            ><input id="profile-name" v-model="name" required maxlength="40" />
          </div>
          <div v-if="profileError" class="inline-error" role="alert">{{ profileError }}</div>
          <div class="form-actions">
            <button class="btn btn-primary" :disabled="saving">
              {{ saving ? '正在保存…' : '保存资料' }}
            </button>
          </div>
        </form>
      </section>
      <section class="panel">
        <div class="panel-heading">
          <div>
            <h2>账号安全</h2>
            <p>修改密码后，全部登录会话将失效，需要重新登录。</p>
          </div>
          <Icon name="LockKeyhole" :size="20" />
        </div>
        <form class="stack-form padded-form" @submit.prevent="password">
          <div>
            <label for="old-password">当前密码</label
            ><input
              id="old-password"
              v-model="oldPassword"
              type="password"
              autocomplete="current-password"
              required
              maxlength="128"
            />
          </div>
          <div class="form-columns">
            <div>
              <label for="new-password">新密码</label
              ><input
                id="new-password"
                v-model="newPassword"
                type="password"
                autocomplete="new-password"
                required
                minlength="10"
                maxlength="128"
              />
            </div>
            <div>
              <label for="confirm-password">确认新密码</label
              ><input
                id="confirm-password"
                v-model="confirmation"
                type="password"
                autocomplete="new-password"
                required
                minlength="10"
                maxlength="128"
              />
            </div>
          </div>
          <small class="field-hint">使用10—128位密码，包含字母和数字。</small>
          <div v-if="passwordError" class="inline-error" role="alert">{{ passwordError }}</div>
          <div class="form-actions">
            <button class="btn" :disabled="changing">
              {{ changing ? '正在更新…' : '更新密码' }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>
