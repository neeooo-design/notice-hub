<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '../components/Icon.vue';
import { api, setAuth } from '../state.js';
const router = useRouter(),
  username = ref(''),
  password = ref(''),
  show = ref(false),
  busy = ref(false),
  error = ref('');
async function login() {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    setAuth(
      await api('/auth/login', {
        method: 'POST',
        body: { username: username.value, password: password.value },
      }),
    );
    router.replace('/dashboard');
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="login-page">
    <section class="login-story">
      <div class="brand light">
        <span class="brand-mark">N<span></span></span>
        <div>Notice Hub<small>团队通知与协作空间</small></div>
      </div>
      <div class="login-story-content">
        <span class="eyebrow light-text">A LITTLE MORE CONNECTED</span>
        <h1>重要的信息，<br />和每一个人有关。</h1>
        <p>让通知有处可寻，让协作井然有序。<br />在同一个空间，了解团队正在发生的事。</p>
        <div class="story-art">
          <div class="art-orbit orbit-one"></div>
          <div class="art-orbit orbit-two"></div>
          <div class="art-card art-card-back"><span></span><span></span></div>
          <div class="art-card art-card-front">
            <div class="art-icon"><Icon name="BellRing" :size="27" /></div>
            <span class="art-line"></span><span class="art-line short"></span>
            <div class="art-avatars">
              <i>陈</i><i>周</i><i>许</i><small>在这里，与团队保持同步</small>
            </div>
          </div>
          <span class="art-spark">✦</span>
        </div>
      </div>
      <div class="login-story-footer">信息清晰一点，团队靠近一点。</div>
    </section>
    <section class="login-form-side">
      <div class="login-form-wrap">
        <span class="login-mini-label"><span class="status-dot"></span> 你的团队工作空间</span>
        <h2>欢迎回来</h2>
        <p class="muted">登录账号，看看团队的新动态。</p>
        <form @submit.prevent="login">
          <label for="username">用户名</label>
          <div class="input-icon">
            <Icon name="UserRound" :size="18" /><input
              id="username"
              v-model="username"
              autocomplete="username"
              placeholder="请输入用户名"
              required
              maxlength="32"
            />
          </div>
          <label for="password">密码</label>
          <div class="input-icon">
            <Icon name="LockKeyhole" :size="18" /><input
              id="password"
              v-model="password"
              :type="show ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="请输入密码"
              required
              maxlength="128"
            /><button
              type="button"
              class="icon-btn"
              :aria-label="show ? '隐藏密码' : '显示密码'"
              @click="show = !show"
            >
              <Icon :name="show ? 'EyeOff' : 'Eye'" :size="18" />
            </button>
          </div>
          <div v-if="error" class="inline-error" role="alert">
            <Icon name="CircleAlert" :size="17" />{{ error }}
          </div>
          <button class="btn btn-primary login-submit" :disabled="busy">
            {{ busy ? '正在登录…' : '登录工作空间' }}<Icon name="ArrowRight" :size="18" />
          </button>
        </form>
        <div class="login-help">
          <Icon name="ShieldCheck" :size="17" /><span
            >账号由管理员统一分配。需要开通或恢复账号？<br />请联系你的团队管理员。</span
          >
        </div>
      </div>
      <div class="login-bottom">NOTICE HUB <span>·</span> 为清晰的沟通而设计</div>
    </section>
  </div>
</template>
