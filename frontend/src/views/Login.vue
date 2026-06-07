<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <div class="logo-wrapper">
          <WaterToolOutlined class="logo-icon" />
        </div>
        <h1 class="system-title">智能灌溉控制系统</h1>
        <p class="system-subtitle">水肥一体化 · 精准控制</p>
      </div>

      <a-form
        ref="formRef"
        :model="loginForm"
        layout="vertical"
        @finish="handleLogin"
      >
        <a-form-item
          name="username"
          :rules="[{ required: true, message: '请输入用户名' }]"
        >
          <a-input
            v-model:value="loginForm.username"
            size="large"
            placeholder="请输入用户名"
          >
            <template #prefix>
              <UserOutlined />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item
          name="password"
          :rules="[{ required: true, message: '请输入密码' }]"
        >
          <a-input-password
            v-model:value="loginForm.password"
            size="large"
            placeholder="请输入密码"
          >
            <template #prefix>
              <LockOutlined />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item>
          <a-checkbox v-model:checked="loginForm.remember">
            记住我
          </a-checkbox>
        </a-form-item>

        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            size="large"
            :loading="loading"
            block
          >
            {{ loading ? '登录中...' : '登录' }}
          </a-button>
        </a-form-item>
      </a-form>

      <div class="default-accounts">
        <a-alert
          type="info"
          show-icon
        >
          <template #message>
            <span>默认账号：</span>
          </template>
          <template #description>
            <ul class="account-list">
              <li><a-tag color="red">管理员</a-tag> admin / admin123</li>
              <li><a-tag color="blue">操作员</a-tag> operator / operator123</li>
              <li><a-tag color="default">只读用户</a-tag> viewer / viewer123</li>
            </ul>
          </template>
        </a-alert>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useAppStore } from '@/stores'
import {
  UserOutlined,
  LockOutlined,
  WaterToolOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const formRef = ref()
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
  remember: false
})

onMounted(() => {
  const savedUsername = localStorage.getItem('rememberedUsername')
  if (savedUsername) {
    loginForm.username = savedUsername
    loginForm.remember = true
  }
})

async function handleLogin() {
  try {
    loading.value = true
    await appStore.login(loginForm.username, loginForm.password)
    
    if (loginForm.remember) {
      localStorage.setItem('rememberedUsername', loginForm.username)
    } else {
      localStorage.removeItem('rememberedUsername')
    }
    
    message.success('登录成功')
    
    const redirect = route.query.redirect as string
    router.push(redirect || '/')
  } catch (e: any) {
    message.error(e?.response?.data?.message || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
  overflow: hidden;
}

.login-container::before {
  content: '';
  position: absolute;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle at 20% 80%, rgba(255, 255, 255, 0.1) 0%, transparent 50%);
  animation: float 20s ease-in-out infinite;
}

@keyframes float {
  0%, 100% {
    transform: translate(0, 0) rotate(0deg);
  }
  50% {
    transform: translate(30px, -30px) rotate(5deg);
  }
}

.login-card {
  width: 420px;
  padding: 40px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  position: relative;
  z-index: 1;
  backdrop-filter: blur(10px);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo-wrapper {
  width: 80px;
  height: 80px;
  margin: 0 auto 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-icon {
  font-size: 40px;
  color: #fff;
}

.system-title {
  font-size: 24px;
  font-weight: 600;
  color: #262626;
  margin: 0 0 8px;
}

.system-subtitle {
  font-size: 14px;
  color: #8c8c8c;
  margin: 0;
}

.default-accounts {
  margin-top: 24px;
}

.account-list {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.account-list li {
  font-size: 13px;
  color: #595959;
  line-height: 2;
}
</style>
