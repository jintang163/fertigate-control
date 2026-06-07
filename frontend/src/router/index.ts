import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/monitor-screen'
  },
  {
    path: '/monitor-screen',
    name: 'MonitorScreen',
    component: () => import('@/views/MonitorScreen.vue'),
    meta: { title: '监控大屏', icon: 'DesktopOutlined' }
  },
  {
    path: '/monitor-dashboard',
    name: 'MonitorDashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '监控总览', icon: 'DashboardOutlined' }
  },
  {
    path: '/realtime',
    name: 'Realtime',
    component: () => import('@/views/RealtimeMonitor.vue'),
    meta: { title: '实时监控', icon: 'MonitorOutlined' }
  },
  {
    path: '/history-trends',
    name: 'HistoryTrends',
    component: () => import('@/views/HistoryTrends.vue'),
    meta: { title: '历史趋势', icon: 'LineChartOutlined' }
  },
  {
    path: '/zone-map',
    name: 'ZoneMap',
    component: () => import('@/views/MonitorScreen.vue'),
    meta: { title: '灌区地图', icon: 'EnvironmentOutlined' }
  },
  {
    path: '/threshold-config',
    name: 'ThresholdConfig',
    component: () => import('@/views/ThresholdConfig.vue'),
    meta: { title: '阈值策略', icon: 'SlidersOutlined' }
  },
  {
    path: '/rotation-plan',
    name: 'RotationPlan',
    component: () => import('@/views/RotationPlan.vue'),
    meta: { title: '轮灌计划', icon: 'CalendarOutlined' }
  },
  {
    path: '/manual-control',
    name: 'ManualControl',
    component: () => import('@/views/ManualControl.vue'),
    meta: { title: '手动控制', icon: 'ControlOutlined' }
  },
  {
    path: '/alert-list',
    name: 'AlertList',
    component: () => import('@/views/AlertCenter.vue'),
    meta: { title: '告警中心', icon: 'BellOutlined' }
  },
  {
    path: '/fertigation-ledger',
    name: 'FertigationLedger',
    component: () => import('@/views/FertigationLedger.vue'),
    meta: { title: '灌肥台账', icon: 'FileExcelOutlined' }
  },
  {
    path: '/devices',
    name: 'Devices',
    component: () => import('@/views/DeviceManagement.vue'),
    meta: { title: '设备管理', icon: 'SettingOutlined' }
  },
  {
    path: '/crops',
    name: 'Crops',
    component: () => import('@/views/CropManagement.vue'),
    meta: { title: '作物管理', icon: 'SmileOutlined' }
  },
  {
    path: '/zones',
    name: 'Zones',
    component: () => import('@/views/ZoneManagement.vue'),
    meta: { title: '区域管理', icon: 'AppstoreOutlined' }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('@/views/SystemSettings.vue'),
    meta: { title: '系统设置', icon: 'ToolOutlined' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || '水肥一体化'} - 智能灌溉控制系统`
  next()
})

export default router
