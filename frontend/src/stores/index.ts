import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ControlStatus, Alert, Device, Valve, User, LoginResponse } from '@/types'
import { dashboardApi, alertApi, irrigationApi, deviceApi, authApi } from '@/api'
import { alertWebSocket } from '@/utils/websocket'

export const useAppStore = defineStore('app', () => {
  const controlStatus = ref<ControlStatus | null>(null)
  const unacknowledgedAlerts = ref<Alert[]>([])
  const devices = ref<Device[]>([])
  const valves = ref<Valve[]>([])
  const overviewData = ref<any>(null)
  const loading = ref(false)
  const webSocketConnected = ref(false)
  
  const currentUser = ref<User | null>(null)
  const token = ref<string | null>(localStorage.getItem('token'))
  const userRoles = ref<string[]>([])
  const userPermissions = ref<string[]>([])
  const isLoggedIn = ref(!!token.value)
  
  let alertUnsubscribe: (() => void) | null = null

  const alertCount = computed(() => unacknowledgedAlerts.value.length)
  const criticalAlerts = computed(() => 
    unacknowledgedAlerts.value.filter(a => a.level === 'critical')
  )

  async function fetchControlStatus() {
    try {
      controlStatus.value = await irrigationApi.getControlStatus()
    } catch (e) {
      console.error('Failed to fetch control status:', e)
    }
  }

  async function fetchAlerts() {
    try {
      unacknowledgedAlerts.value = await alertApi.getUnacknowledged()
    } catch (e) {
      console.error('Failed to fetch alerts:', e)
    }
  }

  async function fetchOverview() {
    try {
      overviewData.value = await dashboardApi.getOverview()
    } catch (e) {
      console.error('Failed to fetch overview:', e)
    }
  }

  async function fetchDevices() {
    try {
      devices.value = await deviceApi.getAll()
    } catch (e) {
      console.error('Failed to fetch devices:', e)
    }
  }

  async function fetchValves() {
    try {
      valves.value = await deviceApi.getAllValves()
    } catch (e) {
      console.error('Failed to fetch valves:', e)
    }
  }

  async function fetchAll() {
    loading.value = true
    try {
      await Promise.all([
        fetchControlStatus(),
        fetchAlerts(),
        fetchOverview(),
        fetchDevices(),
        fetchValves()
      ])
    } finally {
      loading.value = false
    }
  }

  function setControlMode(mode: 'auto' | 'manual') {
    return irrigationApi.setControlMode(mode)
  }

  function emergencyStop() {
    return irrigationApi.emergencyStop()
  }

  function acknowledgeAlert(id: string) {
    return alertApi.acknowledge(id)
  }

  function acknowledgeAllAlerts() {
    return alertApi.acknowledgeAll()
  }

  function controlValve(valveId: string, open: boolean, reason?: string) {
    return irrigationApi.controlValve(valveId, open, reason)
  }

  function initWebSocket() {
    alertUnsubscribe = alertWebSocket.onAlert((alert) => {
      const exists = unacknowledgedAlerts.value.some(a => a.id === alert.id)
      if (!exists) {
        unacknowledgedAlerts.value.unshift(alert)
      }
    })
    alertWebSocket.connect()
    webSocketConnected.value = true
  }

  function disconnectWebSocket() {
    if (alertUnsubscribe) {
      alertUnsubscribe()
      alertUnsubscribe = null
    }
    alertWebSocket.disconnect()
    webSocketConnected.value = false
  }

  function handleRealtimeAlert(alert: Alert) {
    const exists = unacknowledgedAlerts.value.some(a => a.id === alert.id)
    if (!exists) {
      unacknowledgedAlerts.value.unshift(alert)
    }
  }

  async function login(username: string, password: string): Promise<LoginResponse> {
    const response = await authApi.login({ username, password })
    token.value = response.token
    currentUser.value = {
      id: response.userId,
      username: response.username,
      realName: response.realName,
      enabled: true,
      createdAt: '',
      updatedAt: '',
      roleCodes: response.roles
    }
    userRoles.value = response.roles
    userPermissions.value = response.permissions
    isLoggedIn.value = true
    localStorage.setItem('token', response.token)
    return response
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch (e) {
      console.error('Logout error:', e)
    }
    token.value = null
    currentUser.value = null
    userRoles.value = []
    userPermissions.value = []
    isLoggedIn.value = false
    localStorage.removeItem('token')
    disconnectWebSocket()
  }

  async function fetchCurrentUser() {
    if (!token.value) return
    try {
      const user = await authApi.getUserInfo()
      currentUser.value = user
      if (user.roleCodes) {
        userRoles.value = user.roleCodes
      }
      try {
        const perms = await authApi.getPermissions()
        userPermissions.value = perms
      } catch (e) {
        console.error('Fetch permissions error:', e)
      }
      isLoggedIn.value = true
    } catch (e) {
      console.error('Fetch user info error:', e)
      logout()
    }
  }

  function hasRole(role: string): boolean {
    return userRoles.value.includes(role)
  }

  function hasPermission(permission: string): boolean {
    return userPermissions.value.includes(permission)
  }

  function hasAnyRole(roles: string[]): boolean {
    return roles.some(r => userRoles.value.includes(r))
  }

  function hasAnyPermission(permissions: string[]): boolean {
    return permissions.some(p => userPermissions.value.includes(p))
  }

  const isAdmin = computed(() => userRoles.value.includes('admin'))
  const isOperator = computed(() => userRoles.value.includes('operator'))
  const isViewer = computed(() => userRoles.value.includes('viewer'))

  return {
    controlStatus,
    unacknowledgedAlerts,
    devices,
    valves,
    overviewData,
    loading,
    webSocketConnected,
    currentUser,
    token,
    userRoles,
    userPermissions,
    isLoggedIn,
    isAdmin,
    isOperator,
    isViewer,
    alertCount,
    criticalAlerts,
    fetchControlStatus,
    fetchAlerts,
    fetchOverview,
    fetchDevices,
    fetchValves,
    fetchAll,
    setControlMode,
    emergencyStop,
    acknowledgeAlert,
    acknowledgeAllAlerts,
    controlValve,
    initWebSocket,
    disconnectWebSocket,
    handleRealtimeAlert,
    login,
    logout,
    fetchCurrentUser,
    hasRole,
    hasPermission,
    hasAnyRole,
    hasAnyPermission
  }
})
