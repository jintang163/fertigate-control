import { ref } from 'vue'
import type { Alert } from '@/types'

export interface WebSocketMessage {
  type: 'alert' | 'heartbeat' | 'welcome' | 'response' | 'error'
  id?: string
  alertType?: string
  level?: 'info' | 'warning' | 'critical' | 'error'
  message?: string
  sensorValue?: number
  thresholdValue?: number
  createdAt?: string
  zoneName?: string
  deviceName?: string
  timestamp?: string
  action?: string
  status?: string
}

class AlertWebSocket {
  private ws: WebSocket | null = null
  private reconnectAttempts = 0
  private maxReconnectAttempts = 5
  private reconnectDelay = 3000
  private heartbeatInterval: number | null = null
  private messageHandlers: Set<(alert: Alert) => void> = new Set()
  private url: string

  public isConnected = ref(false)
  public connectionError = ref<string | null>(null)

  constructor() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    this.url = `${protocol}//${host}/ws/alerts`
  }

  connect() {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      return
    }

    try {
      this.ws = new WebSocket(this.url)

      this.ws.onopen = () => {
        console.log('[WebSocket] Connected to alert service')
        this.isConnected.value = true
        this.connectionError.value = null
        this.reconnectAttempts = 0
        this.subscribe()
        this.startHeartbeat()
      }

      this.ws.onmessage = (event) => {
        try {
          const data: WebSocketMessage = JSON.parse(event.data)
          this.handleMessage(data)
        } catch (e) {
          console.error('[WebSocket] Failed to parse message:', e)
        }
      }

      this.ws.onclose = (event) => {
        console.log('[WebSocket] Disconnected:', event.code, event.reason)
        this.isConnected.value = false
        this.stopHeartbeat()
        this.attemptReconnect()
      }

      this.ws.onerror = (error) => {
        console.error('[WebSocket] Error:', error)
        this.connectionError.value = 'WebSocket连接错误'
      }
    } catch (e) {
      console.error('[WebSocket] Failed to connect:', e)
      this.connectionError.value = 'WebSocket连接失败'
      this.attemptReconnect()
    }
  }

  disconnect() {
    this.stopHeartbeat()
    if (this.ws) {
      this.ws.close(1000, 'Client disconnect')
      this.ws = null
    }
    this.isConnected.value = false
  }

  reconnect() {
    this.reconnectAttempts = 0
    this.connect()
  }

  private attemptReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('[WebSocket] Max reconnect attempts reached')
      this.connectionError.value = '连接失败，请稍后重试'
      return
    }

    this.reconnectAttempts++
    const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1)
    console.log(`[WebSocket] Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`)

    setTimeout(() => {
      this.connect()
    }, delay)
  }

  private handleMessage(data: WebSocketMessage) {
    switch (data.type) {
      case 'alert':
        this.handleAlert(data)
        break
      case 'heartbeat':
        break
      case 'welcome':
        console.log('[WebSocket] Server welcome:', data.message)
        break
      case 'response':
        console.log('[WebSocket] Server response:', data.status)
        break
      case 'error':
        console.error('[WebSocket] Server error:', data.message)
        break
      default:
        console.log('[WebSocket] Unknown message type:', data.type)
    }
  }

  private handleAlert(data: WebSocketMessage) {
    const alert: Alert = {
      id: data.id || '',
      alertType: data.alertType || '',
      level: data.level || 'warning',
      message: data.message || '',
      sensorValue: data.sensorValue,
      thresholdValue: data.thresholdValue,
      isAcknowledged: false,
      createdAt: data.createdAt || new Date().toISOString(),
      zoneName: data.zoneName,
      deviceName: data.deviceName
    } as Alert

    this.messageHandlers.forEach(handler => {
      try {
        handler(alert)
      } catch (e) {
        console.error('[WebSocket] Error in alert handler:', e)
      }
    })
  }

  subscribe() {
    this.send({ action: 'subscribe' })
  }

  unsubscribe() {
    this.send({ action: 'unsubscribe' })
  }

  ping() {
    this.send({ action: 'ping' })
  }

  private send(data: object) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data))
    }
  }

  private startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatInterval = window.setInterval(() => {
      this.ping()
    }, 10000)
  }

  private stopHeartbeat() {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval)
      this.heartbeatInterval = null
    }
  }

  onAlert(handler: (alert: Alert) => void) {
    this.messageHandlers.add(handler)
    return () => {
      this.messageHandlers.delete(handler)
    }
  }

  offAlert(handler: (alert: Alert) => void) {
    this.messageHandlers.delete(handler)
  }
}

export const alertWebSocket = new AlertWebSocket()

export function useAlertWebSocket() {
  return {
    alertWebSocket,
    isConnected: alertWebSocket.isConnected,
    connectionError: alertWebSocket.connectionError,
    connect: () => alertWebSocket.connect(),
    disconnect: () => alertWebSocket.disconnect(),
    reconnect: () => alertWebSocket.reconnect(),
    onAlert: (handler: (alert: Alert) => void) => alertWebSocket.onAlert(handler),
    offAlert: (handler: (alert: Alert) => void) => alertWebSocket.offAlert(handler)
  }
}
