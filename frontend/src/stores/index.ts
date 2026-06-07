import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ControlStatus, Alert, Device, Valve } from '@/types'
import { dashboardApi, alertApi, irrigationApi, deviceApi } from '@/api'
import { alertWebSocket } from '@/utils/websocket'

export const useAppStore = defineStore('app', () => {
  const controlStatus = ref<ControlStatus | null>(null)
  const unacknowledgedAlerts = ref<Alert[]>([])
  const devices = ref<Device[]>([])
  const valves = ref<Valve[]>([])
  const overviewData = ref<any>(null)
  const loading = ref(false)
  const webSocketConnected = ref(false)
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

  return {
    controlStatus,
    unacknowledgedAlerts,
    devices,
    valves,
    overviewData,
    loading,
    webSocketConnected,
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
    handleRealtimeAlert
  }
})
