<script setup lang="ts">
import {Lock, User} from '@element-plus/icons-vue'
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage} from 'element-plus'
import {onMounted, reactive, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import {useAuthStore} from '@/stores/auth'
import {getErrorMessage} from '@/utils/error'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

onMounted(() => {
  if (route.query.registered === '1') {
    ElMessage.success('注册成功，请登录')
  }
})

async function onSubmit() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  try {
    await auth.login(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    const redirect =
      typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
        ? route.query.redirect
        : '/'
    await router.replace(redirect)
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '用户名或密码错误'))
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-card kk-glass kk-glass--panel">
      <header class="login-header">
        <span class="login-badge">欢迎回来</span>
        <h1 class="login-title">登录 Khan Kiddo</h1>
        <p class="login-subtitle">使用账号继续你的英语学习之旅</p>
      </header>

      <el-form
        ref="formRef"
        class="login-form"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            autocomplete="username"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            autocomplete="current-password"
            :prefix-icon="Lock"
            @keyup.enter="onSubmit"
          />
        </el-form-item>

        <el-button
          type="primary"
          class="login-submit"
          :loading="auth.loading"
          native-type="submit"
        >
          登录
        </el-button>
      </el-form>

      <p class="login-footer">
        还没有账号？
        <router-link to="/register" class="login-link">立即注册</router-link>
      </p>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 2rem 0 3rem;
  min-height: calc(100vh - var(--kk-navbar-offset) - 4rem);
}

.login-card {
  width: min(100%, 420px);
  padding: 2rem 1.75rem 1.5rem;
  border-radius: var(--kk-radius-lg);
  box-shadow: var(--kk-shadow-card);
}

.login-header {
  margin-bottom: 1.5rem;
  text-align: center;
}

.login-badge {
  display: inline-block;
  margin-bottom: 0.75rem;
  padding: 0.25rem 0.75rem;
  border-radius: var(--kk-radius-pill);
  font-size: 0.75rem;
  font-family: var(--kk-font-body);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--kk-color-accent-text);
  background: var(--kk-color-accent-bg);
}

.login-title {
  margin: 0 0 0.5rem;
  font-family: var(--kk-font-display);
  font-size: 1.75rem;
  font-weight: 600;
  color: var(--kk-color-primary);
  letter-spacing: -0.02em;
}

.login-subtitle {
  margin: 0;
  font-family: var(--kk-font-body);
  font-size: 0.95rem;
  color: var(--kk-color-text-muted);
}

.login-form {
  margin-top: 0.25rem;
}

.login-form :deep(.el-form-item__label) {
  font-family: var(--kk-font-body);
  font-weight: 600;
  color: var(--kk-color-text-secondary);
}

.login-submit {
  width: 100%;
  margin-top: 0.5rem;
  font-family: var(--kk-font-body);
  font-weight: 600;
  border-radius: var(--kk-radius-md);
}

.login-footer {
  margin: 1.25rem 0 0;
  text-align: center;
  font-family: var(--kk-font-body);
  font-size: 0.9rem;
  color: var(--kk-color-text-muted);
}

.login-link {
  color: var(--kk-color-link);
  font-weight: 600;
  text-decoration: none;
}

.login-link:hover {
  color: var(--kk-color-primary);
}
</style>
