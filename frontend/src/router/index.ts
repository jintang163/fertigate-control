import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAppStore } from '@/stores'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    redirect: '/monitor-screen'
  },
  {
    path: '/monitor-screen',
    name: 'MonitorScreen',
    component: () => import('@/views/MonitorScreen.vue'),
    meta: { title: '监控大屏', icon: 'DesktopOutlined', roles: ['admin', 'operator', 'viewer'] }
  },
  {
    path: '/monitor-dashboard',
    name: 'MonitorDashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '监控总览', icon: 'DashboardOutlined', roles: ['admin', 'operator', 'viewer'] }
  },
  {
    path: '/realtime',
    name: 'Realtime',
    component: () => import('@/views/RealtimeMonitor.vue'),
    meta: { title: '实时监控', icon: 'MonitorOutlined', roles: ['admin', 'operator', 'viewer'] }
  },
  {
    path: '/history-trends',
    name: 'HistoryTrends',
    component: () => import('@/views/HistoryTrends.vue'),
    meta: { title: '历史趋势', icon: 'LineChartOutlined', roles: ['admin', 'operator', 'viewer'] }
  },
  {
    path: '/zone-map',
    name: 'ZoneMap',
    component: () => import('@/views/MonitorScreen.vue'),
    meta: { title: '灌区地图', icon: 'EnvironmentOutlined', roles: ['admin', 'operator', 'viewer'] }
  },
  {
    path: '/threshold-config',
    name: 'ThresholdConfig',
    component: () => import('@/views/ThresholdConfig.vue'),
    meta: { title: '阈值策略', icon: 'SlidersOutlined', roles: ['admin', 'operator'] }
  },
  {
    path: '/rotation-plan',
    name: 'RotationPlan',
    component: () => import('@/views/RotationPlan.vue'),
    meta: { title: '轮灌计划', icon: 'CalendarOutlined', roles: ['admin', 'operator'] }
  },
  {
    path: '/manual-control',
    name: 'ManualControl',
    component: () => import('@/views/ManualControl.vue'),
    meta: { title: '手动控制', icon: 'ControlOutlined', roles: ['admin', 'operator'] }
  },
  {
    path: '/alert-list',
    name: 'AlertList',
    component: () => import('@/views/AlertCenter.vue'),
    meta: { title: '告警中心', icon: 'BellOutlined', roles: ['admin', 'operator', 'viewer'] }
  },
  {
    path: '/fertigation-ledger',
    name: 'FertigationLedger',
    component: () => import('@/views/FertigationLedger.vue'),
    meta: { title: '灌肥台账', icon: 'FileExcelOutlined', roles: ['admin', 'operator', 'viewer'] }
  },
  {
    path: '/devices',
    name: 'Devices',
    component: () => import('@/views/DeviceManagement.vue'),
    meta: { title: '设备管理', icon: 'SettingOutlined', roles: ['admin', 'operator'] }
  },
  {
    path: '/crops',
    name: 'Crops',
    component: () => import('@/views/CropManagement.vue'),
    meta: { title: '作物管理', icon: 'SmileOutlined', roles: ['admin', 'operator'] }
  },
  {
    path: '/zones',
    name: 'Zones',
    component: () => import('@/views/ZoneManagement.vue'),
    meta: { title: '区域管理', icon: 'AppstoreOutlined', roles: ['admin', 'operator'] }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('@/views/SystemSettings.vue'),
    meta: { title: '系统设置', icon: 'ToolOutlined', roles: ['admin', 'operator'] }
  },
  {
    path: '/user-management',
    name: 'UserManagement',
    component: () => import('@/views/UserManagement.vue'),
    meta: { title: '用户管理', icon: 'UserOutlined', roles: ['admin'] }
  },
  {
    path: '/operation-logs',
    name: 'OperationLogs',
    component: () => import('@/views/OperationLogs.vue'),
    meta: { title: '操作日志', icon: 'FileTextOutlined', roles: ['admin', 'operator'] }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, _from, next) => {
  document.title = `${to.meta.title || '水肥一体化'} - 智能灌溉控制系统`
  
  const store = useAppStore()
  
  if (to.meta.public) {
    if (store.isLoggedIn && to.path === '/login') {
      next('/')
      return
    }
    next()
    return
  }
  
  if (!store.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  
  if (!store.currentUser) {
    try {
      await store.fetchCurrentUser()
    } catch (e) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
  }
  
  if (to.meta.roles) {
    const roles = to.meta.roles as string[]
    if (!store.hasAnyRole(roles)) {
      next('/403')
      return
    }
  }
  
  next()
})

export default router
