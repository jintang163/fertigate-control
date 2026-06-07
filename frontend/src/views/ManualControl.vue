<template>
  <div class="manual-control">
    <a-row :gutter="[16, 16]" class="main-layout">
      <a-col :xs="24" :lg="6" class="left-panel">
        <div class="card-container zone-list-card">
          <div class="section-header">
            <span class="section-title">灌区列表</span>
            <a-badge :count="zones.length" :number-style="{ backgroundColor: '#1890ff' }" />
          </div>
          
          <a-input-search
            v-model:value="zoneSearchKeyword"
            placeholder="搜索灌区名称"
            allow-clear
            style="margin-bottom: 12px"
            @search="filterZones"
          />
          
          <a-radio-group
            v-model:value="zoneStatusFilter"
            button-style="solid"
            size="small"
            style="margin-bottom: 16px; width: 100%"
          >
            <a-radio-button value="all">全部</a-radio-button>
            <a-radio-button value="normal">正常</a-radio-button>
            <a-radio-button value="warning">告警</a-radio-button>
            <a-radio-button value="irrigation">灌溉中</a-radio-button>
          </a-radio-group>
          
          <div class="zone-list">
            <div
              v-for="zone in filteredZones"
              :key="zone.id"
              :class="['zone-card', { selected: selectedZoneId === zone.id, 'is-irrigation': getZoneStatus(zone) === 'irrigation' }]"
              @click="selectZone(zone)"
            >
              <div class="zone-header">
                <span class="zone-name">{{ zone.name }}</span>
                <a-tag :color="getZoneStatusColor(getZoneStatus(zone))">
                  {{ getZoneStatusText(getZoneStatus(zone)) }}
                </a-tag>
              </div>
              <div class="zone-info">
                <span class="info-item">
                  <EnvironmentOutlined /> 面积: {{ zone.area || '--' }} 亩
                </span>
                <span class="info-item">
                  <WaterDropOutlined /> 湿度: {{ getZoneHumidity(zone) }}%
                </span>
              </div>
              <div class="zone-valve-count">
                阀门: {{ getZoneValveCount(zone.id) }} 个
                <span class="open-count">
                  (开: {{ getZoneOpenValveCount(zone.id) }})
                </span>
              </div>
              <div class="zone-actions" v-if="hasPermission('irrigation:control')">
                <a-button
                  type="primary"
                  size="small"
                  :loading="zoneLoadingMap[zone.id + '_open']"
                  @click.stop="openZoneValves(zone)"
                >
                  全部开启
                </a-button>
                <a-button
                  danger
                  size="small"
                  :loading="zoneLoadingMap[zone.id + '_close']"
                  @click.stop="closeZoneValves(zone)"
                >
                  全部关闭
                </a-button>
              </div>
            </div>
            
            <a-empty v-if="filteredZones.length === 0" description="暂无符合条件的灌区" />
          </div>
        </div>
        
        <div class="card-container realtime-panel" v-if="selectedZone">
          <div class="section-header">
            <span class="section-title">实时数据 - {{ selectedZone.name }}</span>
          </div>
          <a-spin :spinning="sensorDataLoading">
            <div class="sensor-data-grid">
              <div class="sensor-gauge">
                <div class="gauge-label">土壤湿度</div>
                <div class="gauge-wrapper">
                  <v-chart :option="humidityGaugeOption" autoresize />
                </div>
                <div class="gauge-value">
                  <span :style="{ color: getHumidityColor(sensorData.humidity) }">
                    {{ sensorData.humidity?.toFixed(1) || '--' }}
                  </span>%
                </div>
              </div>
              
              <div class="sensor-gauge">
                <div class="gauge-label">EC值</div>
                <div class="gauge-wrapper">
                  <v-chart :option="ecGaugeOption" autoresize />
                </div>
                <div class="gauge-value">
                  <span :style="{ color: getEcColor(sensorData.ec) }">
                    {{ sensorData.ec?.toFixed(2) || '--' }}
                  </span> mS/cm
                </div>
              </div>
              
              <div class="sensor-gauge">
                <div class="gauge-label">pH值</div>
                <div class="gauge-wrapper">
                  <v-chart :option="phGaugeOption" autoresize />
                </div>
                <div class="gauge-value">
                  <span :style="{ color: getPhColor(sensorData.ph) }">
                    {{ sensorData.ph?.toFixed(2) || '--' }}
                  </span>
                </div>
              </div>
              
              <div class="sensor-gauge">
                <div class="gauge-label">温度</div>
                <div class="gauge-wrapper">
                  <v-chart :option="tempGaugeOption" autoresize />
                </div>
                <div class="gauge-value">
                  <span :style="{ color: getTempColor(sensorData.temperature) }">
                    {{ sensorData.temperature?.toFixed(1) || '--' }}
                  </span>°C
                </div>
              </div>
            </div>
          </a-spin>
        </div>
      </a-col>
      
      <a-col :xs="24" :lg="12" class="center-panel">
        <div class="card-container valve-control-card">
          <div class="section-header">
            <span class="section-title">阀门控制矩阵</span>
            <a-space>
              <a-tag color="blue">共 {{ filteredValves.length }} 个阀门</a-tag>
              <a-tag color="success">开启: {{ openValveCount }}</a-tag>
              <a-tag color="default">关闭: {{ filteredValves.length - openValveCount }}</a-tag>
            </a-space>
          </div>
          
          <div class="valve-toolbar">
            <a-space wrap>
              <a-select
                v-model:value="valveZoneFilter"
                placeholder="按灌区筛选"
                allow-clear
                style="min-width: 150px"
                size="middle"
              >
                <a-select-option v-for="zone in zones" :key="zone.id" :value="zone.id">
                  {{ zone.name }}
                </a-select-option>
              </a-select>
              
              <a-select
                v-model:value="valveStatusFilter"
                placeholder="按状态筛选"
                allow-clear
                style="min-width: 120px"
                size="middle"
              >
                <a-select-option value="open">开启</a-select-option>
                <a-select-option value="closed">关闭</a-select-option>
              </a-select>
              
              <template v-if="hasPermission('irrigation:control')">
                <a-button
                  type="primary"
                  :disabled="selectedValveIds.length === 0"
                  @click="batchOpenValves"
                >
                  <PlayCircleOutlined /> 批量开启
                </a-button>
                <a-button
                  danger
                  :disabled="selectedValveIds.length === 0"
                  @click="batchCloseValves"
                >
                  <StopOutlined /> 批量关闭
                </a-button>
                <a-button
                  :disabled="selectedValveIds.length === 0"
                  @click="batchSetAuto"
                >
                  <SettingOutlined /> 批量设为自动
                </a-button>
              </template>
            </a-space>
          </div>
          
          <a-table
            :columns="valveColumns"
            :data-source="filteredValves"
            :pagination="{ pageSize: 8, showSizeChanger: false, showTotal: (total) => `共 ${total} 条` }"
            :loading="valveLoading"
            row-key="id"
            row-selection={{
              selectedRowKeys: selectedValveIds,
              onChange: (keys) => { selectedValveIds = keys as string[] },
              getCheckboxProps: (record) => ({
                disabled: controlMode === 'auto' && record.autoControl
              })
            }}
            class="valve-table"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'valveNumber'">
                <span class="valve-number">{{ record.device?.deviceCode || record.valveNumber || record.id }}</span>
              </template>
              
              <template v-else-if="column.key === 'zoneName'">
                {{ getZoneName(record.zoneId) }}
              </template>
              
              <template v-else-if="column.key === 'isOpen'">
                <div class="status-wrapper">
                  <span :class="['status-dot', { blinking: record.isOpen }]" />
                  <a-tag :color="record.isOpen ? 'success' : 'default'">
                    {{ record.isOpen ? '开启' : '关闭' }}
                  </a-tag>
                </div>
              </template>
              
              <template v-else-if="column.key === 'flowRate'">
                <span v-if="record.isOpen">
                  {{ record.flowRate?.toFixed(2) || '--' }} L/min
                </span>
                <span v-else class="text-muted">--</span>
              </template>
              
              <template v-else-if="column.key === 'autoControl'">
                <a-switch
                  v-if="hasPermission('irrigation:control')"
                  :checked="record.autoControl"
                  :disabled="controlMode === 'auto'"
                  size="small"
                  checked-children="自动"
                  un-checked-children="手动"
                  @change="handleValveAutoChange(record, $event)"
                />
              </template>
              
              <template v-else-if="column.key === 'action'">
                <a-space size="small" v-if="hasPermission('irrigation:control')">
                  <a-button
                    v-if="!record.isOpen"
                    type="primary"
                    size="small"
                    :loading="valveLoadingMap[record.id]"
                    :disabled="controlMode === 'auto' && record.autoControl"
                    @click="toggleValve(record, true)"
                  >
                    <PlayCircleOutlined /> 开启
                  </a-button>
                  <a-button
                    v-else
                    danger
                    size="small"
                    :loading="valveLoadingMap[record.id]"
                    @click="toggleValve(record, false)"
                  >
                    <StopOutlined /> 关闭
                  </a-button>
                </a-space>
              </template>
            </template>
          </a-table>
        </div>
      </a-col>
      
      <a-col :xs="24" :lg="6" class="right-panel">
        <div class="card-container control-mode-card">
          <div class="section-header">
            <span class="section-title">控制模式</span>
          </div>
          
          <div class="mode-switcher">
            <div class="mode-info">
              <span class="mode-label">当前模式</span>
              <a-tag :color="controlMode === 'auto' ? 'success' : 'warning'" class="mode-tag">
                {{ controlMode === 'auto' ? '自动控制' : '手动控制' }}
              </a-tag>
            </div>
            <a-switch
              v-if="hasPermission('irrigation:control')"
              :checked="controlMode === 'auto'"
              checked-children="自动"
              un-checked-children="手动"
              size="large"
              @change="handleModeChange"
            />
          </div>
          
          <a-alert
            v-if="controlMode === 'auto'"
            type="success"
            show-icon
            message="自动控制已启用"
            description="系统根据传感器数据和阈值策略自动控制阀门"
            style="margin-top: 12px"
          />
          <a-alert
            v-else
            type="warning"
            show-icon
            message="手动控制已启用"
            description="请谨慎操作，所有阀门需手动控制"
            style="margin-top: 12px"
          />
          
          <a-button
            v-if="hasPermission('irrigation:control')"
            danger
            size="large"
            block
            class="emergency-stop-btn"
            :loading="emergencyStopLoading"
            @click="handleEmergencyStop"
          >
            <WarningOutlined /> 紧急停止 - 关闭所有阀门
          </a-button>
        </div>
        
        <div class="card-container operation-log-card">
          <div class="section-header">
            <span class="section-title">操作日志</span>
            <a-button type="link" size="small" @click="refreshLogs">
              <ReloadOutlined /> 刷新
            </a-button>
          </div>
          
          <div class="operation-log-list">
            <div
              v-for="(log, index) in operationLogs"
              :key="index"
              class="log-item"
            >
              <div class="log-header">
                <a-tag :color="getLogTypeColor(log.type)" size="small">
                  {{ log.typeText }}
                </a-tag>
                <span class="log-time">{{ formatTime(log.timestamp) }}</span>
              </div>
              <div class="log-content">
                <span class="log-operator">{{ log.operator }}</span>
                <span class="log-action">{{ log.action }}</span>
              </div>
              <div class="log-target" v-if="log.target">
                <span class="log-target-label">对象:</span>
                <span class="log-target-value">{{ log.target }}</span>
                <a-tag :color="log.status ? 'success' : 'error'" size="small">
                  {{ log.status ? '成功' : '失败' }}
                </a-tag>
              </div>
            </div>
            
            <a-empty v-if="operationLogs.length === 0" description="暂无操作记录" />
          </div>
        </div>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch, h } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  EnvironmentOutlined,
  WaterDropOutlined,
  PlayCircleOutlined,
  StopOutlined,
  SettingOutlined,
  WarningOutlined,
  ReloadOutlined
} from '@ant-design/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { GaugeChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import type { Valve, Zone, ControlStatus, IrrigationDecision, ZoneSensorData } from '@/types'
import { irrigationApi, deviceApi, zoneApi, sensorApi, monitorApi } from '@/api'
import { useAppStore } from '@/stores'
import dayjs from 'dayjs'

use([GaugeChart, CanvasRenderer])

const store = useAppStore()
const hasPermission = store.hasPermission
const hasAnyRole = store.hasAnyRole

interface OperationLog {
  type: 'open' | 'close' | 'mode' | 'emergency' | 'auto'
  typeText: string
  operator: string
  action: string
  target?: string
  status: boolean
  timestamp: string
}

interface SensorData {
  humidity?: number
  ec?: number
  ph?: number
  temperature?: number
}

const mockZones: Zone[] = [
  { id: 'zone1', name: '东区1号灌区', area: 50, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'zone2', name: '东区2号灌区', area: 45, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'zone3', name: '西区1号灌区', area: 60, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'zone4', name: '西区2号灌区', area: 55, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'zone5', name: '南区灌区', area: 40, createdAt: '2024-01-01', updatedAt: '2024-01-01' }
]

const mockValves: Valve[] = [
  { id: 'v1', zoneId: 'zone1', valveNumber: 1, flowRate: 12.5, isOpen: true, autoControl: false, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'v2', zoneId: 'zone1', valveNumber: 2, flowRate: 11.8, isOpen: false, autoControl: true, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'v3', zoneId: 'zone1', valveNumber: 3, flowRate: 13.2, isOpen: true, autoControl: false, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'v4', zoneId: 'zone2', valveNumber: 4, flowRate: 10.5, isOpen: false, autoControl: true, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'v5', zoneId: 'zone2', valveNumber: 5, flowRate: 12.0, isOpen: false, autoControl: false, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'v6', zoneId: 'zone3', valveNumber: 6, flowRate: 14.2, isOpen: true, autoControl: true, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'v7', zoneId: 'zone3', valveNumber: 7, flowRate: 13.8, isOpen: false, autoControl: false, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'v8', zoneId: 'zone4', valveNumber: 8, flowRate: 11.5, isOpen: false, autoControl: true, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'v9', zoneId: 'zone4', valveNumber: 9, flowRate: 12.8, isOpen: true, autoControl: false, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'v10', zoneId: 'zone5', valveNumber: 10, flowRate: 10.0, isOpen: false, autoControl: true, createdAt: '2024-01-01', updatedAt: '2024-01-01' }
]

const zones = ref<Zone[]>([])
const valves = ref<Valve[]>([])
const controlMode = ref<'auto' | 'manual'>('manual')
const controlStatus = ref<ControlStatus | null>(null)
const selectedZoneId = ref<string | null>(null)
const selectedValveIds = ref<string[]>([])

const zoneSearchKeyword = ref('')
const zoneStatusFilter = ref('all')
const valveZoneFilter = ref<string | undefined>(undefined)
const valveStatusFilter = ref<string | undefined>(undefined)

const valveLoading = ref(false)
const sensorDataLoading = ref(false)
const emergencyStopLoading = ref(false)
const zoneLoadingMap = reactive<Record<string, boolean>>({})
const valveLoadingMap = reactive<Record<string, boolean>>({})

const sensorData = reactive<SensorData>({})
const operationLogs = ref<OperationLog[]>([])

let refreshTimer: number | null = null

const selectedZone = computed(() => {
  return zones.value.find(z => z.id === selectedZoneId.value) || null
})

const filteredZones = computed(() => {
  let result = zones.value
  
  if (zoneSearchKeyword.value) {
    const keyword = zoneSearchKeyword.value.toLowerCase()
    result = result.filter(z => z.name.toLowerCase().includes(keyword))
  }
  
  if (zoneStatusFilter.value !== 'all') {
    result = result.filter(z => getZoneStatus(z) === zoneStatusFilter.value)
  }
  
  return result
})

const filteredValves = computed(() => {
  let result = valves.value
  
  if (valveZoneFilter.value) {
    result = result.filter(v => v.zoneId === valveZoneFilter.value)
  }
  
  if (valveStatusFilter.value) {
    const isOpen = valveStatusFilter.value === 'open'
    result = result.filter(v => v.isOpen === isOpen)
  }
  
  return result
})

const openValveCount = computed(() => {
  return filteredValves.value.filter(v => v.isOpen).length
})

const valveColumns = [
  { title: '阀门编号', key: 'valveNumber', width: 120 },
  { title: '所属灌区', key: 'zoneName', width: 120 },
  { title: '当前状态', key: 'isOpen', width: 100 },
  { title: '流量(L/min)', key: 'flowRate', width: 110 },
  { title: '自动控制', key: 'autoControl', width: 100 },
  { title: '操作', key: 'action', width: 140, fixed: 'right' as const }
]

const humidityGaugeOption = computed(() => createGaugeOption(sensorData.humidity || 0, 0, 100, '%', getHumidityColor(sensorData.humidity)))
const ecGaugeOption = computed(() => createGaugeOption(sensorData.ec || 0, 0, 3, 'mS/cm', getEcColor(sensorData.ec)))
const phGaugeOption = computed(() => createGaugeOption(sensorData.ph || 0, 4, 9, '', getPhColor(sensorData.ph)))
const tempGaugeOption = computed(() => createGaugeOption(sensorData.temperature || 0, 0, 50, '°C', getTempColor(sensorData.temperature)))

function createGaugeOption(value: number, min: number, max: number, unit: string, color: string) {
  return {
    series: [{
      type: 'gauge',
      startAngle: 200,
      endAngle: -20,
      min,
      max,
      splitNumber: 5,
      radius: '90%',
      center: ['50%', '60%'],
      progress: {
        show: true,
        width: 12,
        itemStyle: { color }
      },
      pointer: {
        show: false
      },
      axisLine: {
        lineStyle: {
          width: 12,
          color: [[1, '#f0f0f0']]
        }
      },
      axisTick: {
        show: false
      },
      splitLine: {
        show: false
      },
      axisLabel: {
        show: false
      },
      detail: {
        show: false
      }
    }]
  }
}

function getHumidityColor(value?: number) {
  if (value === undefined) return '#8c8c8c'
  if (value < 30) return '#ff4d4f'
  if (value < 50) return '#faad14'
  if (value <= 80) return '#52c41a'
  return '#1890ff'
}

function getEcColor(value?: number) {
  if (value === undefined) return '#8c8c8c'
  if (value < 0.8 || value > 2.5) return '#ff4d4f'
  if (value < 1.0 || value > 2.0) return '#faad14'
  return '#52c41a'
}

function getPhColor(value?: number) {
  if (value === undefined) return '#8c8c8c'
  if (value < 5.5 || value > 7.5) return '#ff4d4f'
  if (value < 6.0 || value > 7.0) return '#faad14'
  return '#52c41a'
}

function getTempColor(value?: number) {
  if (value === undefined) return '#8c8c8c'
  if (value < 10 || value > 35) return '#ff4d4f'
  if (value < 15 || value > 30) return '#faad14'
  return '#52c41a'
}

function getZoneStatus(zone: Zone): string {
  const zoneValves = valves.value.filter(v => v.zoneId === zone.id)
  if (zoneValves.some(v => v.isOpen)) return 'irrigation'
  if (Math.random() > 0.9) return 'warning'
  return 'normal'
}

function getZoneStatusColor(status: string) {
  switch (status) {
    case 'normal': return 'success'
    case 'warning': return 'warning'
    case 'irrigation': return 'processing'
    default: return 'default'
  }
}

function getZoneStatusText(status: string) {
  switch (status) {
    case 'normal': return '正常'
    case 'warning': return '告警'
    case 'irrigation': return '灌溉中'
    default: return '未知'
  }
}

function getZoneHumidity(zone: Zone): string {
  const humidity = 40 + Math.random() * 40
  return humidity.toFixed(1)
}

function getZoneValveCount(zoneId: string): number {
  return valves.value.filter(v => v.zoneId === zoneId).length
}

function getZoneOpenValveCount(zoneId: string): number {
  return valves.value.filter(v => v.zoneId === zoneId && v.isOpen).length
}

function getZoneName(zoneId?: string): string {
  if (!zoneId) return '--'
  const zone = zones.value.find(z => z.id === zoneId)
  return zone?.name || '--'
}

function getLogTypeColor(type: string) {
  switch (type) {
    case 'open': return 'success'
    case 'close': return 'default'
    case 'mode': return 'blue'
    case 'emergency': return 'red'
    case 'auto': return 'cyan'
    default: return 'default'
  }
}

function formatTime(time: string) {
  return dayjs(time).format('MM-DD HH:mm:ss')
}

function selectZone(zone: Zone) {
  selectedZoneId.value = zone.id
  fetchSensorData(zone.id)
}

function filterZones() {
}

function addLog(type: OperationLog['type'], action: string, target?: string, status = true) {
  const typeText: Record<string, string> = {
    open: '开启',
    close: '关闭',
    mode: '模式切换',
    emergency: '紧急停止',
    auto: '自动设置'
  }
  
  operationLogs.value.unshift({
    type,
    typeText: typeText[type],
    operator: '管理员',
    action,
    target,
    status,
    timestamp: new Date().toISOString()
  })
  
  if (operationLogs.value.length > 50) {
    operationLogs.value = operationLogs.value.slice(0, 50)
  }
}

async function fetchZones() {
  try {
    zones.value = await zoneApi.getAll()
  } catch (e) {
    console.warn('Failed to fetch zones, using mock data:', e)
    zones.value = mockZones
  }
}

async function fetchValves() {
  valveLoading.value = true
  try {
    valves.value = await deviceApi.getAllValves()
  } catch (e) {
    console.warn('Failed to fetch valves, using mock data:', e)
    valves.value = mockValves
  } finally {
    valveLoading.value = false
  }
}

async function fetchControlStatus() {
  try {
    const status = await irrigationApi.getControlStatus()
    controlStatus.value = status
    controlMode.value = status.controlMode === 'auto' ? 'auto' : 'manual'
  } catch (e) {
    console.warn('Failed to fetch control status:', e)
  }
}

async function fetchSensorData(zoneId: string) {
  sensorDataLoading.value = true
  try {
    const data = await monitorApi.getZoneSensorData(zoneId)
    sensorData.humidity = data.humidity
    sensorData.ec = data.ec
    sensorData.ph = data.ph
    sensorData.temperature = data.temperature
  } catch (e) {
    console.warn('Failed to fetch sensor data, using mock data:', e)
    sensorData.humidity = 45 + Math.random() * 30
    sensorData.ec = 1.2 + Math.random() * 0.8
    sensorData.ph = 6.0 + Math.random() * 1.5
    sensorData.temperature = 20 + Math.random() * 10
  } finally {
    sensorDataLoading.value = false
  }
}

function refreshLogs() {
  message.success('日志已刷新')
}

function handleModeChange(checked: boolean) {
  const newMode: 'auto' | 'manual' = checked ? 'auto' : 'manual'
  Modal.confirm({
    title: `确认切换到${newMode === 'auto' ? '自动' : '手动'}模式`,
    content: newMode === 'auto' 
      ? '切换到自动模式后，系统将根据传感器数据和阈值策略自动控制阀门开关。是否继续？'
      : '切换到手动模式后，所有自动控制将暂停，需手动操作所有阀门。是否继续？',
    onOk: async () => {
      try {
        await irrigationApi.setControlMode(newMode)
        controlMode.value = newMode
        message.success(`已切换到${newMode === 'auto' ? '自动' : '手动'}模式`)
        addLog('mode', `切换到${newMode === 'auto' ? '自动' : '手动'}模式`)
      } catch (e) {
        message.error('切换控制模式失败')
        addLog('mode', `切换到${newMode === 'auto' ? '自动' : '手动'}模式`, undefined, false)
      }
    }
  })
}

function handleEmergencyStop() {
  Modal.confirm({
    title: '确认紧急停止',
    content: '此操作将立即关闭所有阀门！请确认是否继续？',
    okType: 'danger',
    okText: '确认紧急停止',
    onOk: async () => {
      emergencyStopLoading.value = true
      try {
        await irrigationApi.emergencyStop()
        valves.value.forEach(v => { v.isOpen = false })
        message.success('紧急停止已执行，所有阀门已关闭')
        addLog('emergency', '执行紧急停止，关闭所有阀门', '全部阀门')
      } catch (e) {
        message.error('紧急停止操作失败')
        addLog('emergency', '执行紧急停止', '全部阀门', false)
      } finally {
        emergencyStopLoading.value = false
      }
    }
  })
}

function toggleValve(valve: Valve, open: boolean) {
  if (controlMode.value === 'auto' && valve.autoControl) {
    message.warning('该阀门处于自动控制模式，请先切换到手动模式或关闭该阀门的自动控制')
    return
  }
  
  Modal.confirm({
    title: `确认${open ? '开启' : '关闭'}阀门`,
    content: `确定要${open ? '开启' : '关闭'}阀门【${valve.device?.deviceCode || valve.valveNumber || valve.id}】吗？`,
    onOk: async () => {
      valveLoadingMap[valve.id] = true
      try {
        await irrigationApi.controlValve(valve.id, open, '手动操作')
        valve.isOpen = open
        message.success(`阀门已${open ? '开启' : '关闭'}`)
        addLog(
          open ? 'open' : 'close',
          `${open ? '开启' : '关闭'}阀门`,
          `${getZoneName(valve.zoneId)} - ${valve.device?.deviceCode || valve.valveNumber || valve.id}`
        )
      } catch (e) {
        message.error(`${open ? '开启' : '关闭'}阀门失败`)
        addLog(
          open ? 'open' : 'close',
          `${open ? '开启' : '关闭'}阀门`,
          `${getZoneName(valve.zoneId)} - ${valve.device?.deviceCode || valve.valveNumber || valve.id}`,
          false
        )
      } finally {
        valveLoadingMap[valve.id] = false
      }
    }
  })
}

function handleValveAutoChange(valve: Valve, checked: boolean) {
  Modal.confirm({
    title: `确认${checked ? '开启' : '关闭'}阀门自动控制`,
    content: `确定要将阀门【${valve.device?.deviceCode || valve.valveNumber || valve.id}】设置为${checked ? '自动' : '手动'}控制吗？`,
    onOk: async () => {
      try {
        await deviceApi.setValveAuto(valve.id, checked)
        valve.autoControl = checked
        message.success(`阀门已设置为${checked ? '自动' : '手动'}控制`)
        addLog(
          'auto',
          `设置为${checked ? '自动' : '手动'}控制`,
          `${getZoneName(valve.zoneId)} - ${valve.device?.deviceCode || valve.valveNumber || valve.id}`
        )
      } catch (e) {
        message.error('设置失败')
        addLog('auto', `设置为${checked ? '自动' : '手动'}控制`, undefined, false)
      }
    }
  })
}

function openZoneValves(zone: Zone) {
  const zoneValves = valves.value.filter(v => v.zoneId === zone.id)
  if (zoneValves.length === 0) {
    message.warning('该灌区暂无阀门')
    return
  }
  
  Modal.confirm({
    title: '确认开启灌区所有阀门',
    content: `确定要开启灌区【${zone.name}】的所有 ${zoneValves.length} 个阀门吗？`,
    onOk: async () => {
      zoneLoadingMap[zone.id + '_open'] = true
      try {
        const results = await Promise.all(
          zoneValves.map(v => irrigationApi.controlValve(v.id, true, '灌区批量开启'))
        )
        zoneValves.forEach(v => { v.isOpen = true })
        message.success(`已开启灌区【${zone.name}】的所有阀门`)
        addLog('open', '批量开启灌区所有阀门', zone.name)
      } catch (e) {
        message.error('开启阀门失败')
        addLog('open', '批量开启灌区所有阀门', zone.name, false)
      } finally {
        zoneLoadingMap[zone.id + '_open'] = false
      }
    }
  })
}

function closeZoneValves(zone: Zone) {
  const zoneValves = valves.value.filter(v => v.zoneId === zone.id)
  if (zoneValves.length === 0) {
    message.warning('该灌区暂无阀门')
    return
  }
  
  Modal.confirm({
    title: '确认关闭灌区所有阀门',
    content: `确定要关闭灌区【${zone.name}】的所有 ${zoneValves.length} 个阀门吗？`,
    okType: 'danger',
    onOk: async () => {
      zoneLoadingMap[zone.id + '_close'] = true
      try {
        const results = await Promise.all(
          zoneValves.map(v => irrigationApi.controlValve(v.id, false, '灌区批量关闭'))
        )
        zoneValves.forEach(v => { v.isOpen = false })
        message.success(`已关闭灌区【${zone.name}】的所有阀门`)
        addLog('close', '批量关闭灌区所有阀门', zone.name)
      } catch (e) {
        message.error('关闭阀门失败')
        addLog('close', '批量关闭灌区所有阀门', zone.name, false)
      } finally {
        zoneLoadingMap[zone.id + '_close'] = false
      }
    }
  })
}

function batchOpenValves() {
  if (selectedValveIds.value.length === 0) return
  
  Modal.confirm({
    title: '确认批量开启阀门',
    content: `确定要开启选中的 ${selectedValveIds.value.length} 个阀门吗？`,
    onOk: async () => {
      try {
        const results = await Promise.all(
          selectedValveIds.value.map(id => irrigationApi.controlValve(id, true, '批量开启'))
        )
        selectedValveIds.value.forEach(id => {
          const valve = valves.value.find(v => v.id === id)
          if (valve) valve.isOpen = true
        })
        message.success(`已开启 ${selectedValveIds.value.length} 个阀门`)
        addLog('open', `批量开启 ${selectedValveIds.value.length} 个阀门`, '批量操作')
        selectedValveIds.value = []
      } catch (e) {
        message.error('批量开启阀门失败')
        addLog('open', `批量开启 ${selectedValveIds.value.length} 个阀门`, '批量操作', false)
      }
    }
  })
}

function batchCloseValves() {
  if (selectedValveIds.value.length === 0) return
  
  Modal.confirm({
    title: '确认批量关闭阀门',
    content: `确定要关闭选中的 ${selectedValveIds.value.length} 个阀门吗？`,
    okType: 'danger',
    onOk: async () => {
      try {
        const results = await Promise.all(
          selectedValveIds.value.map(id => irrigationApi.controlValve(id, false, '批量关闭'))
        )
        selectedValveIds.value.forEach(id => {
          const valve = valves.value.find(v => v.id === id)
          if (valve) valve.isOpen = false
        })
        message.success(`已关闭 ${selectedValveIds.value.length} 个阀门`)
        addLog('close', `批量关闭 ${selectedValveIds.value.length} 个阀门`, '批量操作')
        selectedValveIds.value = []
      } catch (e) {
        message.error('批量关闭阀门失败')
        addLog('close', `批量关闭 ${selectedValveIds.value.length} 个阀门`, '批量操作', false)
      }
    }
  })
}

function batchSetAuto() {
  if (selectedValveIds.value.length === 0) return
  
  Modal.confirm({
    title: '确认批量设置自动控制',
    content: `确定要将选中的 ${selectedValveIds.value.length} 个阀门设置为自动控制吗？`,
    onOk: async () => {
      try {
        const results = await Promise.all(
          selectedValveIds.value.map(id => deviceApi.setValveAuto(id, true))
        )
        selectedValveIds.value.forEach(id => {
          const valve = valves.value.find(v => v.id === id)
          if (valve) valve.autoControl = true
        })
        message.success(`已将 ${selectedValveIds.value.length} 个阀门设置为自动控制`)
        addLog('auto', `批量设置 ${selectedValveIds.value.length} 个阀门为自动控制`, '批量操作')
        selectedValveIds.value = []
      } catch (e) {
        message.error('批量设置失败')
        addLog('auto', `批量设置 ${selectedValveIds.value.length} 个阀门为自动控制`, '批量操作', false)
      }
    }
  })
}

async function refreshData() {
  await Promise.all([
    fetchValves(),
    fetchControlStatus()
  ])
  
  if (selectedZoneId.value) {
    fetchSensorData(selectedZoneId.value)
  }
}

onMounted(async () => {
  await Promise.all([
    fetchZones(),
    fetchValves(),
    fetchControlStatus()
  ])
  
  if (zones.value.length > 0) {
    selectZone(zones.value[0])
  }
  
  refreshTimer = window.setInterval(refreshData, 10000)
  
  addLog('mode', '进入手动控制面板')
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})

watch(selectedZoneId, (newId) => {
  if (newId) {
    valveZoneFilter.value = newId
  }
})
</script>

<style scoped lang="css">
.manual-control {
  min-height: 100%;
}

.main-layout {
  min-height: 100%;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #262626;
}

.left-panel,
.center-panel,
.right-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.zone-list-card {
  flex: 1;
  max-height: 400px;
  display: flex;
  flex-direction: column;
}

.zone-list {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.zone-list::-webkit-scrollbar {
  width: 6px;
}

.zone-list::-webkit-scrollbar-thumb {
  background: #d9d9d9;
  border-radius: 3px;
}

.zone-card {
  padding: 16px;
  margin-bottom: 12px;
  background: #fafafa;
  border: 2px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.zone-card:hover {
  background: #f0f5ff;
  border-color: #1890ff;
}

.zone-card.selected {
  background: #e6f7ff;
  border-color: #1890ff;
}

.zone-card.is-irrigation {
  animation: pulse-irrigation 2s ease-in-out infinite;
}

@keyframes pulse-irrigation {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(24, 144, 255, 0.4);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(24, 144, 255, 0);
  }
}

.zone-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.zone-name {
  font-weight: 600;
  color: #262626;
  font-size: 14px;
}

.zone-info {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #8c8c8c;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.zone-valve-count {
  font-size: 12px;
  color: #595959;
  margin-bottom: 12px;
}

.open-count {
  color: #52c41a;
  font-weight: 500;
}

.zone-actions {
  display: flex;
  gap: 8px;
}

.zone-actions .ant-btn {
  flex: 1;
}

.realtime-panel {
  flex: 1;
}

.sensor-data-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.sensor-gauge {
  text-align: center;
  padding: 8px;
  background: #fafafa;
  border-radius: 8px;
}

.gauge-label {
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 4px;
}

.gauge-wrapper {
  height: 70px;
}

.gauge-value {
  font-size: 18px;
  font-weight: 600;
}

.valve-control-card {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.valve-toolbar {
  margin-bottom: 16px;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
}

.valve-table {
  flex: 1;
}

.valve-number {
  font-family: 'Courier New', monospace;
  font-weight: 600;
  color: #262626;
}

.status-wrapper {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d9d9d9;
}

.status-dot.blinking {
  background: #52c41a;
  animation: blinking 1s ease-in-out infinite;
}

@keyframes blinking {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.5;
    transform: scale(1.2);
  }
}

.text-muted {
  color: #bfbfbf;
}

.control-mode-card {
  margin-bottom: 16px;
}

.mode-switcher {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
  margin-bottom: 16px;
}

.mode-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mode-label {
  font-size: 12px;
  color: #8c8c8c;
}

.mode-tag {
  font-size: 14px;
  padding: 4px 12px;
}

.emergency-stop-btn {
  height: 56px !important;
  font-size: 16px !important;
  font-weight: 600;
  margin-top: 16px;
  animation: emergency-pulse 2s ease-in-out infinite;
}

@keyframes emergency-pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(255, 77, 79, 0.4);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(255, 77, 79, 0);
  }
}

.operation-log-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 400px;
}

.operation-log-list {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.operation-log-list::-webkit-scrollbar {
  width: 6px;
}

.operation-log-list::-webkit-scrollbar-thumb {
  background: #d9d9d9;
  border-radius: 3px;
}

.log-item {
  padding: 12px;
  margin-bottom: 8px;
  background: #fafafa;
  border-radius: 8px;
  border-left: 3px solid #1890ff;
}

.log-item:nth-child(1) {
  border-left-color: #52c41a;
  background: #f6ffed;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.log-time {
  font-size: 11px;
  color: #8c8c8c;
}

.log-content {
  font-size: 13px;
  color: #262626;
  margin-bottom: 4px;
}

.log-operator {
  font-weight: 500;
  margin-right: 8px;
}

.log-target {
  font-size: 12px;
  color: #595959;
  display: flex;
  align-items: center;
  gap: 6px;
}

.log-target-label {
  color: #8c8c8c;
}

.text-right {
  text-align: right;
}

@media (max-width: 992px) {
  .left-panel,
  .center-panel,
  .right-panel {
    gap: 12px;
  }
  
  .sensor-data-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
