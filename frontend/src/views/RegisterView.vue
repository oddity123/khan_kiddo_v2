<script setup lang="ts">
import { Lock, Message, User } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { register as registerApi } from '@/api/auth'
import { getErrorMessage } from '@/utils/error'

const router = useRouter()
const submitting = ref(false)

const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度为 3–50 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

async function onSubmit() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    const email = form.email.trim()
    await registerApi({
      username: form.username.trim(),
      password: form.password,
      email: email || undefined,
    })
    await router.push({ name: 'login', query: { registered: '1' } })
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '注册失败，请稍后重试'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <section class="auth-card kk-glass kk-glass--panel">
      <header class="auth-header">
        <span class="auth-badge">新用户</span>
        <h1 class="auth-title">注册 Khan Kiddo</h1>
        <p class="auth-subtitle">创建账号，开始你的英语学习之旅</p>
      </header>

      <el-form
        ref="formRef"
        class="auth-form"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="3–50 个字符，用于登录"
            autocomplete="username"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="至少 6 位"
            show-password
            autocomplete="new-password"
            :prefix-icon="Lock"
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="再次输入密码"
            show-password
            autocomplete="new-password"
            :prefix-icon="Lock"
          />
        </el-form-item>

        <el-form-item label="邮箱（可选）" prop="email">
          <el-input
            v-model="form.email"
            placeholder="用于找回密码和通知"
            autocomplete="email"
            :prefix-icon="Message"
          />
        </el-form-item>

        <el-button
          type="primary"
          class="auth-submit"
          :loading="submitting"
          native-type="submit"
        >
          注册
        </el-button>
      </el-form>

      <p class="auth-footer">
        已有账号？
        <router-link to="/login" class="auth-link">去登录</router-link>
      </p>
    </section>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 2rem 0 3rem;
  min-height: calc(100vh - var(--kk-navbar-offset) - 4rem);
}

.auth-card {
  width: min(100%, 420px);
  padding: 2rem 1.75rem 1.5rem;
  border-radius: var(--kk-radius-lg);
  box-shadow: var(--kk-shadow-card);
}

.auth-header {
  margin-bottom: 1.5rem;
  text-align: center;
}

.auth-badge {
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

.auth-title {
  margin: 0 0 0.5rem;
  font-family: var(--kk-font-display);
  font-size: 1.75rem;
  font-weight: 600;
  color: var(--kk-color-primary);
  letter-spacing: -0.02em;
}

.auth-subtitle {
  margin: 0;
  font-family: var(--kk-font-body);
  font-size: 0.95rem;
  color: var(--kk-color-text-muted);
}

.auth-form {
  margin-top: 0.25rem;
}

.auth-form :deep(.el-form-item__label) {
  font-family: var(--kk-font-body);
  font-weight: 600;
  color: var(--kk-color-text-secondary);
}

.auth-submit {
  width: 100%;
  margin-top: 0.5rem;
  font-family: var(--kk-font-body);
  font-weight: 600;
  border-radius: var(--kk-radius-md);
}

.auth-footer {
  margin: 1.25rem 0 0;
  text-align: center;
  font-family: var(--kk-font-body);
  font-size: 0.9rem;
  color: var(--kk-color-text-muted);
}

.auth-link {
  color: var(--kk-color-link);
  font-weight: 600;
  text-decoration: none;
}

.auth-link:hover {
  color: var(--kk-color-primary);
}
</style>
