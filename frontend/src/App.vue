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
        v-model:openKeys="openKeys"
        @click="handleMenuClick"
      >
        <a-sub-menu key="monitor" v-if="canViewAll">
          <template #icon>
            <DesktopOutlined />
          </template>
          <template #title>监控中心</template>
          <a-menu-item key="/monitor-screen">
            <MonitorOutlined />
            <span>监控大屏</span>
          </a-menu-item>
          <a-menu-item key="/monitor-dashboard">
            <DashboardOutlined />
            <span>监控总览</span>
          </a-menu-item>
          <a-menu-item key="/realtime">
            <BarChartOutlined />
            <span>实时监控</span>
          </a-menu-item>
          <a-menu-item key="/history-trends">
            <LineChartOutlined />
            <span>历史趋势</span>
          </a-menu-item>
        </a-sub-menu>

        <a-sub-menu key="map" v-if="canViewAll">
          <template #icon>
            <EnvironmentOutlined />
          </template>
          <template #title>灌区地图</template>
          <a-menu-item key="/zone-map">
            <ApartmentOutlined />
            <span>平面分布</span>
          </a-menu-item>
        </a-sub-menu>

        <a-sub-menu key="control" v-if="canManage">
          <template #icon>
            <ControlOutlined />
          </template>
          <template #title>控制管理</template>
          <a-menu-item key="/manual-control">
            <ThunderboltOutlined />
            <span>手动控制</span>
          </a-menu-item>
          <a-menu-item key="/threshold-config">
            <SlidersOutlined />
            <span>阈值策略</span>
          </a-menu-item>
          <a-menu-item key="/rotation-plan">
            <CalendarOutlined />
            <span>轮灌计划</span>
          </a-menu-item>
        </a-sub-menu>

        <a-sub-menu key="alert" v-if="canViewAll">
          <template #icon>
            <BellOutlined />
          </template>
          <template #title>告警管理</template>
          <a-menu-item key="/alert-list">
            <BellOutlined>
              <a-badge :count="alertCount" :number-style="{ backgroundColor: '#ff4d4f' }" />
            </BellOutlined>
            <span>告警列表</span>
          </a-menu-item>
        </a-sub-menu>

        <a-sub-menu key="record" v-if="canViewAll">
          <template #icon>
            <FileTextOutlined />
          </template>
          <template #title>数据记录</template>
          <a-menu-item key="/fertigation-ledger">
            <FileExcelOutlined />
            <span>灌肥台账</span>
          </a-menu-item>
        </a-sub-menu>

        <a-sub-menu key="config" v-if="canManage || isAdmin">
          <template #icon>
            <SettingOutlined />
          </template>
          <template #title>系统配置</template>
          <a-menu-item key="/devices" v-if="canManage">
            <ToolOutlined />
            <span>设备管理</span>
          </a-menu-item>
          <a-menu-item key="/crops" v-if="canManage">
            <SmileOutlined />
            <span>作物管理</span>
          </a-menu-item>
          <a-menu-item key="/zones" v-if="canManage">
            <AppstoreOutlined />
            <span>区域管理</span>
          </a-menu-item>
          <a-menu-item key="/settings" v-if="canManage">
            <SettingOutlined />
            <span>系统设置</span>
          </a-menu-item>
          <a-menu-item key="/user-management" v-if="isAdmin">
            <UserOutlined />
            <span>用户管理</span>
          </a-menu-item>
          <a-menu-item key="/operation-logs" v-if="isAdmin || isOperator">
            <FileTextOutlined />
            <span>操作日志</span>
          </a-menu-item>
        </a-sub-menu>
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
              v-if="canControl"
              danger 
              type="primary" 
              @click="handleEmergencyStop"
              :loading="emergencyLoading"
            >
              <StopOutlined />
              紧急停止
            </a-button>
            <a-dropdown>
              <div class="user-dropdown-trigger">
                <a-avatar style="background-color: #1890ff">
                  <UserOutlined />
                </a-avatar>
                <span class="username" v-if="currentUser">{{ currentUser.realName || currentUser.username }}</span>
                <DownOutlined class="dropdown-icon" />
              </div>
              <template #overlay>
                <a-menu>
                  <a-menu-item disabled class="user-info-item">
                    <div class="user-info">
                      <div class="user-name">{{ currentUser?.realName || currentUser?.username }}</div>
                      <div class="user-roles">
                        <a-tag
                          v-for="role in currentUser?.roleCodes"
                          :key="role"
                          :color="getRoleColor(role)"
                          size="small"
                        >
                          {{ getRoleName(role) }}
                        </a-tag>
                      </div>
                    </div>
                  </a-menu-item>
                  <a-menu-divider />
                  <a-menu-item key="logout" @click="handleLogout">
                    <LogoutOutlined />
                    <span>退出登录</span>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
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
import {
  WaterToolOutlined,
  DesktopOutlined,
  MonitorOutlined,
  DashboardOutlined,
  LineChartOutlined,
  BarChartOutlined,
  EnvironmentOutlined,
  ApartmentOutlined,
  ControlOutlined,
  ThunderboltOutlined,
  SlidersOutlined,
  CalendarOutlined,
  BellOutlined,
  FileTextOutlined,
  FileExcelOutlined,
  SettingOutlined,
  ToolOutlined,
  SmileOutlined,
  AppstoreOutlined,
  UserOutlined,
  DownOutlined,
  LogoutOutlined,
  StopOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const { unacknowledgedAlerts, controlStatus, currentUser, isAdmin, isOperator, isViewer } = storeToRefs(appStore)
const canControl = appStore.hasPermission('irrigation:control')
const canViewAll = appStore.hasAnyRole(['admin', 'operator', 'viewer'])
const canManage = appStore.hasAnyRole(['admin', 'operator'])

const collapsed = ref(false)
const selectedKeys = ref<string[]>([])
const openKeys = ref<string[]>(['monitor'])
const emergencyLoading = ref(false)

const pathToMenuMap: Record<string, string> = {
  '/monitor-screen': 'monitor',
  '/monitor-dashboard': 'monitor',
  '/realtime': 'monitor',
  '/history-trends': 'monitor',
  '/zone-map': 'map',
  '/manual-control': 'control',
  '/threshold-config': 'control',
  '/rotation-plan': 'control',
  '/alert-list': 'alert',
  '/fertigation-ledger': 'record',
  '/devices': 'config',
  '/crops': 'config',
  '/zones': 'config',
  '/settings': 'config',
  '/user-management': 'config',
  '/operation-logs': 'config'
}

const alertCount = computed(() => unacknowledgedAlerts.value.length)
const controlMode = computed(() => controlStatus.value?.controlMode || 'auto')

const currentPageTitle = computed(() => {
  return route.meta.title as string || '水肥一体化智能灌溉控制系统'
})

function getRoleColor(roleCode: string): string {
  const colors: Record<string, string> = {
    admin: 'red',
    operator: 'blue',
    viewer: 'default'
  }
  return colors[roleCode] || 'default'
}

function getRoleName(roleCode: string): string {
  const names: Record<string, string> = {
    admin: '管理员',
    operator: '操作员',
    viewer: '只读用户'
  }
  return names[roleCode] || roleCode
}

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

function handleLogout() {
  Modal.confirm({
    title: '确认退出',
    content: '确定要退出登录吗？',
    okText: '确认退出',
    cancelText: '取消',
    onOk: async () => {
      await appStore.logout()
      message.success('已退出登录')
      router.push('/login')
    }
  })
}

watch(() => route.path, (newPath) => {
  selectedKeys.value = [newPath]
  const menuKey = pathToMenuMap[newPath]
  if (menuKey && !openKeys.value.includes(menuKey)) {
    openKeys.value = [menuKey]
  }
}, { immediate: true })

onMounted(async () => {
  await appStore.fetchCurrentUser()
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

.user-dropdown-trigger {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.user-dropdown-trigger:hover {
  background-color: #f5f5f5;
}

.username {
  margin-left: 8px;
  margin-right: 4px;
  color: #262626;
  font-size: 14px;
}

.dropdown-icon {
  color: #8c8c8c;
  font-size: 12px;
}

.user-info-item {
  cursor: default !important;
}

.user-info-item:hover {
  background-color: transparent !important;
}

.user-info {
  min-width: 180px;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 8px;
}

.user-roles {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
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
