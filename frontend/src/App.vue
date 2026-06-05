<template>
  <a-layout style="min-height: 100vh">
    <a-layout-sider v-model:collapsed="collapsed" collapsible width="240">
      <div class="logo">
        <WaterToolOutlined style="font-size: 24px; color: #1890ff; margin-right: 8px" />
        <span v-if="!collapsed" class="logo-text">智能灌溉控制</span>
      </div>
      <a-menu
        theme="dark"
        mode="inline"
        v-model:selectedKeys="selectedKeys"
        @click="handleMenuClick"
      >
        <a-menu-item key="/dashboard">
          <DashboardOutlined />
          <span>监控总览</span>
        </a-menu-item>
        <a-menu-item key="/realtime">
          <MonitorOutlined />
          <span>实时监控</span>
        </a-menu-item>
        <a-menu-item key="/devices">
          <SettingOutlined />
          <span>设备管理</span>
        </a-menu-item>
        <a-menu-item key="/irrigation">
          <ControlOutlined />
          <span>灌溉控制</span>
        </a-menu-item>
        <a-menu-item key="/crops">
          <SmileOutlined />
          <span>作物管理</span>
        </a-menu-item>
        <a-menu-item key="/zones">
          <AppstoreOutlined />
          <span>区域管理</span>
        </a-menu-item>
        <a-menu-item key="/alerts">
          <BellOutlined>
            <a-badge :count="alertCount" :number-style="{ backgroundColor: '#ff4d4f' }" />
          </BellOutlined>
          <span>告警中心</span>
        </a-menu-item>
        <a-menu-item key="/records">
          <FileTextOutlined />
          <span>灌溉记录</span>
        </a-menu-item>
        <a-menu-item key="/settings">
          <ToolOutlined />
          <span>系统设置</span>
        </a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="header">
        <div class="header-left">
          <h2 class="page-title">{{ currentPageTitle }}</h2>
        </div>
        <div class="header-right">
          <a-space size="middle">
            <a-tag :color="controlMode === 'auto' ? 'green' : 'orange'">
              {{ controlMode === 'auto' ? '自动模式' : '手动模式' }}
            </a-tag>
            <a-button 
              danger 
              type="primary" 
              @click="handleEmergencyStop"
              :loading="emergencyLoading"
            >
              <StopOutlined />
              紧急停止
            </a-button>
            <a-avatar style="background-color: #1890ff">
              <UserOutlined />
            </a-avatar>
          </a-space>
        </div>
      </a-layout-header>
      <a-layout-content class="content-wrapper">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { useAppStore } from '@/stores'
import { storeToRefs } from 'pinia'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const { unacknowledgedAlerts, controlStatus } = storeToRefs(appStore)

const collapsed = ref(false)
const selectedKeys = ref<string[]>([])
const emergencyLoading = ref(false)

const alertCount = computed(() => unacknowledgedAlerts.value.length)
const controlMode = computed(() => controlStatus.value?.controlMode || 'auto')

const currentPageTitle = computed(() => {
  return route.meta.title as string || '水肥一体化智能灌溉控制系统'
})

function handleMenuClick({ key }: { key: string }) {
  router.push(key)
}

function handleEmergencyStop() {
  Modal.confirm({
    title: '确认紧急停止',
    content: '此操作将立即关闭所有阀门，是否继续？',
    okText: '确认停止',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        emergencyLoading.value = true
        await appStore.emergencyStop()
        message.success('紧急停止已执行，所有阀门已关闭')
        await appStore.fetchAll()
      } catch (e) {
        message.error('紧急停止操作失败')
      } finally {
        emergencyLoading.value = false
      }
    }
  })
}

watch(() => route.path, (newPath) => {
  selectedKeys.value = [newPath]
}, { immediate: true })

onMounted(async () => {
  await appStore.fetchAll()
  
  setInterval(async () => {
    await Promise.all([
      appStore.fetchControlStatus(),
      appStore.fetchAlerts()
    ])
  }, 30000)
})
</script>

<style scoped>
.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.1);
  margin: 0;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}

.logo-text {
  color: #fff;
  font-weight: 600;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 64px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.header-left {
  flex: 1;
}

.header-right {
  display: flex;
  align-items: center;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #262626;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
