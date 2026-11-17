<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'

import {
  authorizeConnectSession,
  denyConnectSession,
  fetchConnectSession,
  type ConnectSessionStatus,
} from '@/api/extensionConnect'
import {getErrorMessage} from '@/utils/error'
import {useAuthStore} from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const sessionId = computed(() => {
  const raw = route.query.session
  return typeof raw === 'string' ? raw.trim() : ''
})

const loading = ref(true)
const acting = ref(false)
const status = ref<ConnectSessionStatus | ''>('')
const expiresAt = ref<string | null>(null)
const loadError = ref('')
const doneMessage = ref('')

const canDecide = computed(
  () => status.value === 'PENDING' || status.value === 'AUTHORIZED',
)

async function loadSession() {
  loading.value = true
  loadError.value = ''
  doneMessage.value = ''
  if (!sessionId.value) {
    loadError.value = '缺少连接会话参数，请从浏览器扩展重新发起连接。'
    loading.value = false
    return
  }
  try {
    const {data} = await fetchConnectSession(sessionId.value)
    status.value = data.status
    expiresAt.value = data.expiresAt
    if (data.status === 'EXCHANGED') {
      doneMessage.value = '此连接已完成，可关闭本页并回到扩展。'
    } else if (data.status === 'DENIED') {
      doneMessage.value = '你已拒绝此次连接。'
    } else if (data.status === 'EXPIRED') {
      doneMessage.value = '连接会话已过期，请在扩展中重新发起。'
    }
  } catch (error) {
    loadError.value = getErrorMessage(error, '无法读取连接会话')
  } finally {
    loading.value = false
  }
}

async function onAllow() {
  if (!sessionId.value || acting.value) {
    return
  }
  acting.value = true
  try {
    const {data} = await authorizeConnectSession(sessionId.value)
    status.value = data.status
    doneMessage.value = '已允许连接。请回到浏览器扩展，连接将自动完成。'
    ElMessage.success('已授权扩展')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '授权失败'))
  } finally {
    acting.value = false
  }
}

async function onDeny() {
  if (!sessionId.value || acting.value) {
    return
  }
  acting.value = true
  try {
    const {data} = await denyConnectSession(sessionId.value)
    status.value = data.status
    doneMessage.value = '已拒绝连接。可关闭本页。'
    ElMessage.info('已拒绝')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '操作失败'))
  } finally {
    acting.value = false
  }
}

function goHome() {
  void router.push('/')
}

onMounted(() => {
  void loadSession()
})
</script>

<template>
  <div class="connect-page">
    <section class="connect-card kk-glass kk-glass--panel">
      <p class="eyebrow">Browser extension</p>
      <h1>连接 Khan Kiddo 扩展</h1>
      <p class="lead">
        扩展请求连接到你的账号，以便提交对话分析并查看结果。凭证可随时在扩展中断开。
      </p>

      <p v-if="auth.user" class="account">
        当前账号：<strong>{{ auth.user.username }}</strong>
      </p>

      <div v-if="loading" class="state">正在核对连接会话…</div>
      <div v-else-if="loadError" class="state error">{{ loadError }}</div>
      <template v-else>
        <ul class="perms">
          <li>以你的身份向 Khan Kiddo 提交对话内容做分析</li>
          <li>查询连接状态；可随时撤销扩展凭证</li>
          <li>不会把网站登录令牌交给 ChatGPT 页面</li>
        </ul>

        <p v-if="doneMessage" class="state ok">{{ doneMessage }}</p>

        <div v-if="canDecide" class="actions">
          <el-button type="primary" size="large" :loading="acting" @click="onAllow">
            允许连接
          </el-button>
          <el-button size="large" :disabled="acting" @click="onDeny">拒绝</el-button>
        </div>
        <div v-else class="actions">
          <el-button size="large" @click="goHome">返回首页</el-button>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.connect-page {
  display: flex;
  justify-content: center;
  padding: 48px 0 72px;
}

.connect-card {
  width: min(520px, 100%);
  padding: 36px 32px;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--kk-accent, #5064a6);
}

h1 {
  margin: 0 0 12px;
  font-family: var(--kk-font-display);
  font-size: 1.75rem;
  color: var(--kk-ink, #18243b);
}

.lead,
.account,
.state {
  margin: 0 0 16px;
  font-size: 14px;
  line-height: 1.65;
  color: var(--kk-muted, #647189);
}

.account strong {
  color: var(--kk-ink, #18243b);
}

.perms {
  margin: 0 0 24px;
  padding-left: 1.2em;
  font-size: 13px;
  line-height: 1.7;
  color: var(--kk-muted, #647189);
}

.state.error {
  color: #ad3152;
}

.state.ok {
  color: #26704c;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
