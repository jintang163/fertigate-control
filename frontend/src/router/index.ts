import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
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
    path: '/devices',
    name: 'Devices',
    component: () => import('@/views/DeviceManagement.vue'),
    meta: { title: '设备管理', icon: 'SettingOutlined' }
  },
  {
    path: '/irrigation',
    name: 'Irrigation',
    component: () => import('@/views/IrrigationControl.vue'),
    meta: { title: '灌溉控制', icon: 'WaterToolOutlined' }
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
    path: '/alerts',
    name: 'Alerts',
    component: () => import('@/views/AlertCenter.vue'),
    meta: { title: '告警中心', icon: 'BellOutlined' }
  },
  {
    path: '/records',
    name: 'Records',
    component: () => import('@/views/IrrigationRecords.vue'),
    meta: { title: '灌溉记录', icon: 'FileTextOutlined' }
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
