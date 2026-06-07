<template>
  <div class="monitor-screen">
    <div class="monitor-header">
      <div class="header-left">
        <MonitorOutlined class="header-icon" />
        <h1 class="header-title">水肥一体化智能监控大屏</h1>
      </div>
      <div class="header-center">
        <a-tag color="blue" class="time-tag">
          <ClockCircleOutlined />
          {{ currentTime }}
        </a-tag>
      </div>
      <div class="header-right">
        <a-space>
          <a-tag :color="controlMode === 'auto' ? 'success' : 'warning'">
            {{ controlMode === 'auto' ? '自动控制模式' : '手动控制模式' }}
          </a-tag>
          <a-button size="small" type="primary" @click="refreshAll">
            <ReloadOutlined :spin="loading" />
            刷新数据
          </a-button>
        </a-space>
      </div>
    </div>

    <div class="monitor-content">
      <div class="monitor-left">
        <div class="panel stat-panel">
          <div class="panel-header">
            <span class="panel-title">运行概览</span>
          </div>
          <div class="stat-grid">
            <div class="stat-item">
              <div class="stat-icon blue">
                <BulbOutlined />
              </div>
              <div class="stat-info">
                <div class="stat-num">{{ overview.totalDevices || 0 }}</div>
                <div class="stat-label">设备总数</div>
              </div>
            </div>
            <div class="stat-item">
              <div class="stat-icon green">
                <CheckCircleOutlined />
              </div>
              <div class="stat-info">
                <div class="stat-num">{{ overview.onlineDevices || 0 }}</div>
                <div class="stat-label">在线设备</div>
              </div>
            </div>
            <div class="stat-item">
              <div class="stat-icon orange">
                <ThunderboltOutlined />
              </div>
              <div class="stat-info">
                <div class="stat-num">{{ overview.openValves || 0 }}</div>
                <div class="stat-label">开启阀门</div>
              </div>
            </div>
            <div class="stat-item">
              <div class="stat-icon red">
                <WarningOutlined />
              </div>
              <div class="stat-info">
                <div class="stat-num">{{ alertCount }}</div>
                <div class="stat-label">未处理告警</div>
              </div>
            </div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">土壤环境实时监测</span>
            <a-radio-group v-model:value="selectedZone" size="small" button-style="solid">
              <a-radio-button v-for="zone in zones" :key="zone.id" :value="zone.id">
                {{ zone.name }}
              </a-radio-button>
            </a-radio-group>
          </div>
          <div class="sensor-gauge-grid">
            <div class="gauge-wrapper">
              <div class="gauge-title">土壤湿度</div>
              <v-chart :option="humidityGaugeOption" autoresize class="gauge-chart" />
              <div class="gauge-range">阈值: {{ currentStrategy?.minHumidity || 30 }}% - {{ currentStrategy?.maxHumidity || 70 }}%</div>
            </div>
            <div class="gauge-wrapper">
              <div class="gauge-title">EC值</div>
              <v-chart :option="ecGaugeOption" autoresize class="gauge-chart" />
              <div class="gauge-range">阈值: {{ currentStrategy?.minEc || 1 }} - {{ currentStrategy?.maxEc || 3 }} mS/cm</div>
            </div>
            <div class="gauge-wrapper">
              <div class="gauge-title">pH值</div>
              <v-chart :option="phGaugeOption" autoresize class="gauge-chart" />
              <div class="gauge-range">阈值: {{ currentStrategy?.minPh || 5.5 }} - {{ currentStrategy?.maxPh || 7.5 }}</div>
            </div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">气象数据</span>
          </div>
          <div class="weather-grid">
            <div class="weather-item">
              <CloudOutlined class="weather-icon blue" />
              <div class="weather-info">
                <div class="weather-value">{{ weatherData.temperature?.toFixed(1) || '--' }}°C</div>
                <div class="weather-label">空气温度</div>
              </div>
            </div>
            <div class="weather-item">
              <DropletOutlined class="weather-icon cyan" />
              <div class="weather-info">
                <div class="weather-value">{{ weatherData.humidity?.toFixed(1) || '--' }}%</div>
                <div class="weather-label">空气湿度</div>
              </div>
            </div>
            <div class="weather-item">
              <WindOutlined class="weather-icon green" />
              <div class="weather-info">
                <div class="weather-value">{{ weatherData.windSpeed?.toFixed(1) || '--' }} m/s</div>
                <div class="weather-label">风速</div>
              </div>
            </div>
            <div class="weather-item">
              <CloudRainOutlined class="weather-icon blue" />
              <div class="weather-info">
                <div class="weather-value">{{ weatherData.rainfall?.toFixed(1) || '--' }} mm</div>
                <div class="weather-label">降雨量</div>
              </div>
            </div>
            <div class="weather-item">
              <SunOutlined class="weather-icon orange" />
              <div class="weather-info">
                <div class="weather-value">{{ weatherData.light?.toFixed(0) || '--' }} lux</div>
                <div class="weather-label">光照强度</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="monitor-center">
        <div class="panel map-panel">
          <div class="panel-header">
            <span class="panel-title">灌区平面分布图</span>
            <a-space>
              <a-tag color="success"><span class="legend-dot" style="background: #52c41a"></span>正常</a-tag>
              <a-tag color="warning"><span class="legend-dot" style="background: #faad14"></span>警告</a-tag>
              <a-tag color="error"><span class="legend-dot" style="background: #ff4d4f"></span>异常</a-tag>
              <a-tag color="blue"><span class="legend-dot" style="background: #1890ff"></span>灌溉中</a-tag>
            </a-space>
          </div>
          <div class="map-container">
            <svg viewBox="0 0 800 500" class="zone-map">
              <defs>
                <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
                  <path d="M 40 0 L 0 0 0 40" fill="none" stroke="rgba(24, 144, 255, 0.1)" stroke-width="1"/>
                </pattern>
              </defs>
              <rect width="800" height="500" fill="url(#grid)"/>
              
              <g v-for="zone in mapZones" :key="zone.id">
                <rect
                  :x="zone.x"
                  :y="zone.y"
                  :width="zone.width"
                  :height="zone.height"
                  :fill="getZoneColor(zone.status)"
                  :stroke="getZoneStroke(zone.status)"
                  stroke-width="2"
                  rx="8"
                  class="zone-rect"
                  @click="selectZone(zone.id)"
                />
                <text
                  :x="zone.x + zone.width / 2"
                  :y="zone.y + zone.height / 2 - 10"
                  text-anchor="middle"
                  fill="#fff"
                  font-size="14"
                  font-weight="600"
                  class="zone-text"
                >
                  {{ zone.name }}
                </text>
                <text
                  :x="zone.x + zone.width / 2"
                  :y="zone.y + zone.height / 2 + 15"
                  text-anchor="middle"
                  fill="rgba(255,255,255,0.8)"
                  font-size="12"
                  class="zone-text"
                >
                  {{ getZoneStatusText(zone.status) }}
                </text>
                
                <g v-for="device in zone.devices" :key="device.id">
                  <circle
                    :cx="zone.x + device.x"
                    :cy="zone.y + device.y"
                    r="12"
                    :fill="device.status === 'online' ? (device.isOpen ? '#52c41a' : '#1890ff') : '#8c8c8c'"
                    stroke="#fff"
                    stroke-width="2"
                    class="device-dot"
                    @click.stop="showDeviceInfo(device)"
                  />
                  <text
                    :x="zone.x + device.x"
                    :y="zone.y + device.y + 4"
                    text-anchor="middle"
                    fill="#fff"
                    font-size="10"
                    font-weight="600"
                    class="device-text"
                  >
                    {{ getDeviceIcon(device.type) }}
                  </text>
                </g>
              </g>
            </svg>
          </div>
        </div>

        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">实时数据趋势</span>
            <a-radio-group v-model:value="trendType" size="small" button-style="solid">
              <a-radio-button value="humidity">土壤湿度</a-radio-button>
              <a-radio-button value="ec">EC值</a-radio-button>
              <a-radio-button value="ph">pH值</a-radio-button>
              <a-radio-button value="temperature">温度</a-radio-button>
            </a-radio-group>
          </div>
          <v-chart :option="trendChartOption" autoresize class="trend-chart" />
        </div>
      </div>

      <div class="monitor-right">
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">实时告警</span>
            <a-badge :count="alertCount" :number-style="{ backgroundColor: '#ff4d4f' }" />
          </div>
          <div class="alert-list" ref="alertListRef">
            <div
              v-for="alert in recentAlerts"
              :key="alert.id"
              :class="['alert-card', alert.level]"
            >
              <div class="alert-icon">
                <ExclamationCircleOutlined v-if="alert.level === 'critical'" />
                <WarningOutlined v-else-if="alert.level === 'warning'" />
                <InfoCircleOutlined v-else />
              </div>
              <div class="alert-content">
                <div class="alert-header">
                  <span class="alert-type">{{ alert.alertType }}</span>
                  <span class="alert-time">{{ formatTime(alert.createdAt) }}</span>
                </div>
                <div class="alert-message">{{ alert.message }}</div>
                <div v-if="alert.sensorValue !== undefined" class="alert-detail">
                  当前值: {{ alert.sensorValue }} / 阈值: {{ alert.thresholdValue }}
                </div>
              </div>
            </div>
            <a-empty v-if="recentAlerts.length === 0" description="暂无告警" :image="null" />
          </div>
        </div>

        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">灌溉执行状态</span>
          </div>
          <div class="irrigation-status">
            <div
              v-for="decision in irrigationDecisions"
              :key="decision.zoneId"
              class="irrigation-item"
            >
              <div class="irrigation-zone">{{ decision.zoneName }}</div>
              <div class="irrigation-detail">
                <div class="irrigation-progress">
                  <div class="progress-label">
                    <span>当前湿度</span>
                    <span>{{ decision.currentHumidity?.toFixed(1) || '--' }}%</span>
                  </div>
                  <a-progress
                    :percent="decision.currentHumidity || 0"
                    :stroke-color="getHumidityColor(decision.currentHumidity, decision.minHumidity, decision.maxHumidity)"
                    show-info="false"
                    :show-default="false"
                  >
                    <template #gap>
                      <div class="progress-gap" :style="{ left: decision.minHumidity + '%', width: (decision.maxHumidity - decision.minHumidity) + '%' }"></div>
                    </template>
                  </a-progress>
                  <div class="progress-range">
                    <span>{{ decision.minHumidity }}%</span>
                    <span>{{ decision.maxHumidity }}%</span>
                  </div>
                </div>
                <a-tag :color="decision.needIrrigation ? 'warning' : 'success'" class="irrigation-tag">
                  {{ decision.needIrrigation ? '待灌溉' : '正常' }}
                </a-tag>
              </div>
            </div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">今日轮灌计划</span>
          </div>
          <div class="rotation-list">
            <div
              v-for="schedule in todaySchedules"
              :key="schedule.id"
              :class="['rotation-item', schedule.isActive ? 'active' : '']"
            >
              <div class="rotation-time">{{ schedule.startTime }} - {{ schedule.endTime }}</div>
              <div class="rotation-info">
                <div class="rotation-name">{{ schedule.name }}</div>
                <div class="rotation-detail">
                  持续: {{ schedule.duration }}分钟 | 水量: {{ schedule.waterAmount }}m³
                </div>
              </div>
              <a-tag :color="schedule.isActive ? 'processing' : 'default'">
                {{ schedule.isActive ? '执行中' : '待执行' }}
              </a-tag>
            </div>
            <a-empty v-if="todaySchedules.length === 0" description="今日暂无计划" :image="null" />
          </div>
        </div>
      </div>
    </div>

    <a-modal
      v-model:open="deviceModalVisible"
      title="设备详情"
      :footer="null"
      width="400px"
    >
      <div v-if="selectedDevice" class="device-detail">
        <a-descriptions bordered size="small" :column="1">
          <a-descriptions-item label="设备名称">{{ selectedDevice.name }}</a-descriptions-item>
          <a-descriptions-item label="设备类型">{{ selectedDevice.type }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="selectedDevice.status === 'online' ? 'success' : 'error'">
              {{ selectedDevice.status === 'online' ? '在线' : '离线' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item v-if="selectedDevice.type === 'valve'" label="阀门状态">
            <a-tag :color="selectedDevice.isOpen ? 'success' : 'default'">
              {{ selectedDevice.isOpen ? '开启' : '关闭' }}
            </a-tag>
          </a-descriptions-item>
        </a-descriptions>
        <div v-if="selectedDevice.type === 'valve'" class="device-actions">
          <a-button
            type="primary"
            :danger="selectedDevice.isOpen"
            block
            @click="controlValve(selectedDevice)"
          >
            {{ selectedDevice.isOpen ? '关闭阀门' : '开启阀门' }}
          </a-button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useAppStore } from '@/stores'
import { storeToRefs } from 'pinia'
import { message } from 'ant-design-vue'
import {
  MonitorOutlined,
  ClockCircleOutlined,
  ReloadOutlined,
  BulbOutlined,
  CheckCircleOutlined,
  ThunderboltOutlined,
  WarningOutlined,
  CloudOutlined,
  DropletOutlined,
  WindOutlined,
  CloudRainOutlined,
  SunOutlined,
  ExclamationCircleOutlined,
  InfoCircleOutlined
} from '@ant-design/icons-vue'
import {
  monitorApi,
  thresholdApi,
  rotationApi,
  irrigationApi,
  zoneApi
} from '@/api'
import type {
  Zone,
  ThresholdStrategy,
  RotationSchedule,
  IrrigationDecision,
  Alert,
  MapZone,
  MapDevice,
  WeatherData
} from '@/types'
import dayjs from 'dayjs'

const appStore = useAppStore()
const { overviewData, unacknowledgedAlerts, controlStatus, loading } = storeToRefs(appStore)

const currentTime = ref('')
const selectedZone = ref('')
const trendType = ref('humidity')
const deviceModalVisible = ref(false)
const selectedDevice = ref<MapDevice | null>(null)
const alertListRef = ref<HTMLElement | null>(null)

const zones = ref<Zone[]>([])
const strategies = ref<ThresholdStrategy[]>([])
const schedules = ref<RotationSchedule[]>([])
const irrigationDecisions = ref<IrrigationDecision[]>([])
const weatherData = ref<Partial<WeatherData>>({})

const humidityData = ref<number[]>([])
const ecData = ref<number[]>([])
const phData = ref<number[]>([])
const temperatureData = ref<number[]>([])
const timeLabels = ref<string[]>([])

let timeTimer: number | null = null
let dataTimer: number | null = null

const currentHumidity = ref(65)
const currentEc = ref(1.8)
const currentPh = ref(6.5)

const overview = computed(() => overviewData.value || {})
const alertCount = computed(() => unacknowledgedAlerts.value.length)
const controlMode = computed(() => controlStatus.value?.controlMode || 'auto')

const currentStrategy = computed(() => {
  return strategies.value.find(s => s.zoneId === selectedZone.value) || strategies.value[0]
})

const recentAlerts = computed(() => {
  return unacknowledgedAlerts.value.slice(0, 8)
})

const todaySchedules = computed(() => {
  return schedules.value.filter(s => s.isActive).slice(0, 5)
})

const mapZones = ref<MapZone[]>([
  {
    id: 'zone-1',
    name: '1号灌区',
    x: 50,
    y: 50,
    width: 220,
    height: 180,
    status: 'normal',
    devices: [
      { id: 'v1', name: '阀门1', type: 'valve', x: 40, y: 40, status: 'online', isOpen: false },
      { id: 'v2', name: '阀门2', type: 'valve', x: 180, y: 40, status: 'online', isOpen: true },
      { id: 's1', name: '土壤传感器', type: 'sensor', x: 110, y: 90, status: 'online' },
      { id: 's2', name: '土壤传感器', type: 'sensor', x: 110, y: 140, status: 'online' }
    ]
  },
  {
    id: 'zone-2',
    name: '2号灌区',
    x: 290,
    y: 50,
    width: 220,
    height: 180,
    status: 'irrigation',
    devices: [
      { id: 'v3', name: '阀门3', type: 'valve', x: 40, y: 40, status: 'online', isOpen: true },
      { id: 'v4', name: '阀门4', type: 'valve', x: 180, y: 40, status: 'online', isOpen: true },
      { id: 's3', name: '土壤传感器', type: 'sensor', x: 110, y: 90, status: 'online' },
      { id: 's4', name: '土壤传感器', type: 'sensor', x: 110, y: 140, status: 'offline' }
    ]
  },
  {
    id: 'zone-3',
    name: '3号灌区',
    x: 530,
    y: 50,
    width: 220,
    height: 180,
    status: 'warning',
    devices: [
      { id: 'v5', name: '阀门5', type: 'valve', x: 40, y: 40, status: 'online', isOpen: false },
      { id: 'v6', name: '阀门6', type: 'valve', x: 180, y: 40, status: 'offline', isOpen: false },
      { id: 's5', name: '土壤传感器', type: 'sensor', x: 110, y: 90, status: 'online' },
      { id: 's6', name: '土壤传感器', type: 'sensor', x: 110, y: 140, status: 'online' }
    ]
  },
  {
    id: 'zone-4',
    name: '4号灌区',
    x: 50,
    y: 260,
    width: 220,
    height: 180,
    status: 'normal',
    devices: [
      { id: 'v7', name: '阀门7', type: 'valve', x: 40, y: 40, status: 'online', isOpen: false },
      { id: 'v8', name: '阀门8', type: 'valve', x: 180, y: 40, status: 'online', isOpen: false },
      { id: 's7', name: '土壤传感器', type: 'sensor', x: 110, y: 90, status: 'online' },
      { id: 'p1', name: '水泵', type: 'pump', x: 110, y: 140, status: 'online', isOpen: false }
    ]
  },
  {
    id: 'zone-5',
    name: '5号灌区',
    x: 290,
    y: 260,
    width: 220,
    height: 180,
    status: 'error',
    devices: [
      { id: 'v9', name: '阀门9', type: 'valve', x: 40, y: 40, status: 'online', isOpen: false },
      { id: 'v10', name: '阀门10', type: 'valve', x: 180, y: 40, status: 'offline', isOpen: false },
      { id: 's8', name: '土壤传感器', type: 'sensor', x: 110, y: 90, status: 'offline' },
      { id: 's9', name: '土壤传感器', type: 'sensor', x: 110, y: 140, status: 'online' }
    ]
  },
  {
    id: 'zone-6',
    name: '6号灌区',
    x: 530,
    y: 260,
    width: 220,
    height: 180,
    status: 'normal',
    devices: [
      { id: 'v11', name: '阀门11', type: 'valve', x: 40, y: 40, status: 'online', isOpen: false },
      { id: 'v12', name: '阀门12', type: 'valve', x: 180, y: 40, status: 'online', isOpen: false },
      { id: 's10', name: '土壤传感器', type: 'sensor', x: 110, y: 90, status: 'online' },
      { id: 'p2', name: '水泵', type: 'pump', x: 110, y: 140, status: 'online', isOpen: false }
    ]
  }
])

const humidityGaugeOption = computed(() => ({
  series: [{
    type: 'gauge',
    startAngle: 225,
    endAngle: -45,
    min: 0,
    max: 100,
    splitNumber: 10,
    center: ['50%', '60%'],
    radius: '85%',
    axisLine: {
      lineStyle: {
        width: 12,
        color: [
          [0.3, '#ff4d4f'],
          [0.6, '#faad14'],
          [1, '#52c41a']
        ]
      }
    },
    pointer: {
      length: '65%',
      width: 4,
      itemStyle: { color: '#1890ff' }
    },
    axisTick: { length: 6, lineStyle: { color: 'auto', width: 1 } },
    splitLine: { length: 10, lineStyle: { color: 'auto', width: 2 } },
    axisLabel: { color: '#8c8c8c', fontSize: 10, distance: -25, formatter: '{value}' },
    detail: {
      fontSize: 24,
      offsetCenter: [0, '10%'],
      valueAnimation: true,
      formatter: '{value}%',
      color: '#1890ff',
      fontWeight: 'bold'
    },
    data: [{ value: currentHumidity.value, name: '' }]
  }]
}))

const ecGaugeOption = computed(() => ({
  series: [{
    type: 'gauge',
    startAngle: 225,
    endAngle: -45,
    min: 0,
    max: 5,
    splitNumber: 10,
    center: ['50%', '60%'],
    radius: '85%',
    axisLine: {
      lineStyle: {
        width: 12,
        color: [
          [0.4, '#1890ff'],
          [0.8, '#52c41a'],
          [1, '#ff4d4f']
        ]
      }
    },
    pointer: {
      length: '65%',
      width: 4,
      itemStyle: { color: '#1890ff' }
    },
    axisTick: { length: 6, lineStyle: { color: 'auto', width: 1 } },
    splitLine: { length: 10, lineStyle: { color: 'auto', width: 2 } },
    axisLabel: { color: '#8c8c8c', fontSize: 10, distance: -25, formatter: '{value}' },
    detail: {
      fontSize: 24,
      offsetCenter: [0, '10%'],
      valueAnimation: true,
      formatter: '{value}',
      color: '#52c41a',
      fontWeight: 'bold'
    },
    data: [{ value: currentEc.value, name: '' }]
  }]
}))

const phGaugeOption = computed(() => ({
  series: [{
    type: 'gauge',
    startAngle: 225,
    endAngle: -45,
    min: 4,
    max: 9,
    splitNumber: 10,
    center: ['50%', '60%'],
    radius: '85%',
    axisLine: {
      lineStyle: {
        width: 12,
        color: [
          [0.2, '#ff4d4f'],
          [0.4, '#52c41a'],
          [0.6, '#52c41a'],
          [0.8, '#ff4d4f'],
          [1, '#ff4d4f']
        ]
      }
    },
    pointer: {
      length: '65%',
      width: 4,
      itemStyle: { color: '#1890ff' }
    },
    axisTick: { length: 6, lineStyle: { color: 'auto', width: 1 } },
    splitLine: { length: 10, lineStyle: { color: 'auto', width: 2 } },
    axisLabel: { color: '#8c8c8c', fontSize: 10, distance: -25, formatter: '{value}' },
    detail: {
      fontSize: 24,
      offsetCenter: [0, '10%'],
      valueAnimation: true,
      formatter: '{value}',
      color: '#52c41a',
      fontWeight: 'bold'
    },
    data: [{ value: currentPh.value, name: '' }]
  }]
}))

const trendChartOption = computed(() => {
  const dataMap: Record<string, { data: number[], color: string, name: string, unit: string }> = {
    humidity: { data: humidityData.value, color: '#1890ff', name: '土壤湿度', unit: '%' },
    ec: { data: ecData.value, color: '#52c41a', name: 'EC值', unit: 'mS/cm' },
    ph: { data: phData.value, color: '#faad14', name: 'pH值', unit: '' },
    temperature: { data: temperatureData.value, color: '#f5222d', name: '空气温度', unit: '°C' }
  }
  const config = dataMap[trendType.value]

  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(0, 30, 60, 0.9)',
      borderColor: '#1890ff',
      textStyle: { color: '#fff' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: timeLabels.value,
      axisLine: { lineStyle: { color: '#1890ff' } },
      axisLabel: { color: '#8c8c8c', fontSize: 10 }
    },
    yAxis: {
      type: 'value',
      name: config.unit,
      axisLine: { lineStyle: { color: '#1890ff' } },
      axisLabel: { color: '#8c8c8c', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(24, 144, 255, 0.1)' } }
    },
    series: [{
      name: config.name,
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      showSymbol: false,
      lineStyle: { width: 3, color: config.color },
      itemStyle: { color: config.color },
      areaStyle: {
        opacity: 0.2,
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: config.color },
            { offset: 1, color: 'rgba(24, 144, 255, 0)' }
          ]
        }
      },
      data: config.data
    }]
  }
})

function updateTime() {
  currentTime.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
}

function formatTime(time: string) {
  return dayjs(time).format('MM-DD HH:mm:ss')
}

function getZoneColor(status: string) {
  const colors: Record<string, string> = {
    normal: 'rgba(82, 196, 26, 0.3)',
    warning: 'rgba(250, 173, 20, 0.3)',
    error: 'rgba(255, 77, 79, 0.3)',
    irrigation: 'rgba(24, 144, 255, 0.4)'
  }
  return colors[status] || colors.normal
}

function getZoneStroke(status: string) {
  const strokes: Record<string, string> = {
    normal: '#52c41a',
    warning: '#faad14',
    error: '#ff4d4f',
    irrigation: '#1890ff'
  }
  return strokes[status] || strokes.normal
}

function getZoneStatusText(status: string) {
  const texts: Record<string, string> = {
    normal: '运行正常',
    warning: '湿度偏低',
    error: '设备故障',
    irrigation: '正在灌溉'
  }
  return texts[status] || texts.normal
}

function getDeviceIcon(type: string) {
  const icons: Record<string, string> = {
    valve: 'V',
    sensor: 'S',
    pump: 'P'
  }
  return icons[type] || 'D'
}

function getHumidityColor(value?: number, min = 30, max = 70) {
  if (!value) return '#d9d9d9'
  if (value < min) return '#ff4d4f'
  if (value > max) return '#1890ff'
  return '#52c41a'
}

function selectZone(zoneId: string) {
  selectedZone.value = zoneId
}

function showDeviceInfo(device: MapDevice) {
  selectedDevice.value = device
  deviceModalVisible.value = true
}

async function controlValve(device: MapDevice) {
  try {
    await irrigationApi.controlValve(device.id, !device.isOpen, '大屏手动操作')
    message.success(`阀门${device.isOpen ? '关闭' : '开启'}成功`)
    device.isOpen = !device.isOpen
    await appStore.fetchAll()
  } catch (e) {
    message.error('操作失败')
  }
}

async function refreshAll() {
  await Promise.all([
    appStore.fetchAll(),
    fetchZones(),
    fetchStrategies(),
    fetchSchedules(),
    fetchDecisions(),
    fetchWeatherData()
  ])
  message.success('数据刷新成功')
}

async function fetchZones() {
  try {
    zones.value = await zoneApi.getAll()
    if (zones.value.length > 0 && !selectedZone.value) {
      selectedZone.value = zones.value[0].id
    }
  } catch (e) {
    console.error('Failed to fetch zones:', e)
    zones.value = [
      { id: 'zone-1', name: '1号灌区', createdAt: '', updatedAt: '' },
      { id: 'zone-2', name: '2号灌区', createdAt: '', updatedAt: '' },
      { id: 'zone-3', name: '3号灌区', createdAt: '', updatedAt: '' }
    ]
    selectedZone.value = 'zone-1'
  }
}

async function fetchStrategies() {
  try {
    strategies.value = await thresholdApi.getActive()
  } catch (e) {
    console.error('Failed to fetch strategies:', e)
    strategies.value = [{
      id: '1',
      name: '默认策略',
      minHumidity: 30,
      maxHumidity: 70,
      minEc: 1,
      maxEc: 3,
      minPh: 5.5,
      maxPh: 7.5,
      minTemperature: 10,
      maxTemperature: 35,
      maxWindSpeed: 10,
      minRainfall: 0,
      weatherLinkEnabled: true,
      avoidRainIrrigation: true,
      highTempIrrigation: false,
      isActive: true,
      priority: 1,
      createdAt: '',
      updatedAt: ''
    }]
  }
}

async function fetchSchedules() {
  try {
    schedules.value = await rotationApi.getActive()
  } catch (e) {
    console.error('Failed to fetch schedules:', e)
    schedules.value = [
      {
        id: '1',
        name: '早间灌溉',
        zoneIds: ['zone-1'],
        startTime: '06:00',
        endTime: '07:30',
        duration: 90,
        intervalHours: 24,
        priority: 1,
        waterAmount: 15,
        fertilizerAmount: 5,
        irrigationType: 'drip',
        isActive: true,
        createdAt: '',
        updatedAt: ''
      },
      {
        id: '2',
        name: '午间补水',
        zoneIds: ['zone-2'],
        startTime: '12:00',
        endTime: '12:30',
        duration: 30,
        intervalHours: 24,
        priority: 2,
        waterAmount: 8,
        fertilizerAmount: 0,
        irrigationType: 'sprinkler',
        isActive: true,
        createdAt: '',
        updatedAt: ''
      },
      {
        id: '3',
        name: '晚间灌溉',
        zoneIds: ['zone-3'],
        startTime: '18:00',
        endTime: '19:00',
        duration: 60,
        intervalHours: 24,
        priority: 1,
        waterAmount: 12,
        fertilizerAmount: 3,
        irrigationType: 'drip',
        isActive: false,
        createdAt: '',
        updatedAt: ''
      }
    ]
  }
}

async function fetchDecisions() {
  try {
    irrigationDecisions.value = await irrigationApi.getAllDecisions()
  } catch (e) {
    console.error('Failed to fetch decisions:', e)
    irrigationDecisions.value = [
      { zoneId: 'zone-1', zoneName: '1号灌区', minHumidity: 30, maxHumidity: 70, needIrrigation: false, reason: '湿度在正常范围内', currentHumidity: 58 },
      { zoneId: 'zone-2', zoneName: '2号灌区', minHumidity: 30, maxHumidity: 70, needIrrigation: true, reason: '湿度低于阈值，需要灌溉', currentHumidity: 25 },
      { zoneId: 'zone-3', zoneName: '3号灌区', minHumidity: 30, maxHumidity: 70, needIrrigation: false, reason: '湿度在正常范围内', currentHumidity: 62 },
      { zoneId: 'zone-4', zoneName: '4号灌区', minHumidity: 30, maxHumidity: 70, needIrrigation: false, reason: '湿度在正常范围内', currentHumidity: 55 }
    ]
  }
}

async function fetchWeatherData() {
  try {
    weatherData.value = await monitorApi.getWeatherData()
  } catch (e) {
    console.error('Failed to fetch weather data:', e)
    weatherData.value = {
      temperature: 26.5,
      humidity: 65,
      windSpeed: 3.2,
      rainfall: 0,
      light: 45000
    }
  }
}

function generateMockData() {
  const now = new Date()
  const timeStr = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`
  timeLabels.value.push(timeStr)
  if (timeLabels.value.length > 30) {
    timeLabels.value.shift()
  }

  currentHumidity.value = 40 + Math.random() * 40
  currentEc.value = 1 + Math.random() * 2
  currentPh.value = 5.5 + Math.random() * 2

  humidityData.value.push(currentHumidity.value)
  ecData.value.push(currentEc.value)
  phData.value.push(currentPh.value)
  temperatureData.value.push(20 + Math.random() * 10)

  if (humidityData.value.length > 30) {
    humidityData.value.shift()
    ecData.value.shift()
    phData.value.shift()
    temperatureData.value.shift()
  }

  weatherData.value.temperature = 20 + Math.random() * 15
  weatherData.value.humidity = 40 + Math.random() * 40
  weatherData.value.windSpeed = Math.random() * 8
  weatherData.value.light = 20000 + Math.random() * 60000
}

onMounted(async () => {
  updateTime()
  timeTimer = window.setInterval(updateTime, 1000)
  
  await fetchZones()
  await fetchStrategies()
  await fetchSchedules()
  await fetchDecisions()
  await fetchWeatherData()
  await appStore.fetchAll()

  for (let i = 0; i < 20; i++) {
    generateMockData()
  }

  dataTimer = window.setInterval(() => {
    generateMockData()
    appStore.fetchOverview()
    appStore.fetchAlerts()
  }, 5000)
})

onUnmounted(() => {
  if (timeTimer) clearInterval(timeTimer)
  if (dataTimer) clearInterval(dataTimer)
})

watch(selectedZone, () => {
  fetchStrategies()
})
</script>

<style scoped>
.monitor-screen {
  height: calc(100vh - 64px);
  background: linear-gradient(135deg, #001529 0%, #002140 50%, #001529 100%);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.monitor-header {
  height: 60px;
  background: linear-gradient(90deg, rgba(24, 144, 255, 0.1) 0%, rgba(24, 144, 255, 0.3) 50%, rgba(24, 144, 255, 0.1) 100%);
  border-bottom: 1px solid rgba(24, 144, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon {
  font-size: 28px;
  color: #1890ff;
}

.header-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 2px;
  text-shadow: 0 0 20px rgba(24, 144, 255, 0.5);
}

.header-center {
  flex: 1;
  text-align: center;
}

.time-tag {
  font-size: 16px;
  font-family: 'Courier New', monospace;
  padding: 4px 16px;
}

.monitor-content {
  flex: 1;
  display: grid;
  grid-template-columns: 380px 1fr 380px;
  gap: 12px;
  padding: 12px;
  overflow: hidden;
}

.monitor-left,
.monitor-center,
.monitor-right {
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}

.panel {
  background: rgba(0, 30, 60, 0.6);
  border: 1px solid rgba(24, 144, 255, 0.3);
  border-radius: 8px;
  backdrop-filter: blur(10px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.stat-panel {
  padding: 16px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(24, 144, 255, 0.2);
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #1890ff;
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-title::before {
  content: '';
  width: 4px;
  height: 16px;
  background: linear-gradient(180deg, #1890ff, #52c41a);
  border-radius: 2px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: rgba(24, 144, 255, 0.1);
  border-radius: 8px;
  border: 1px solid rgba(24, 144, 255, 0.2);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.stat-icon.blue {
  background: linear-gradient(135deg, rgba(24, 144, 255, 0.3), rgba(24, 144, 255, 0.1));
  color: #1890ff;
}

.stat-icon.green {
  background: linear-gradient(135deg, rgba(82, 196, 26, 0.3), rgba(82, 196, 26, 0.1));
  color: #52c41a;
}

.stat-icon.orange {
  background: linear-gradient(135deg, rgba(250, 173, 20, 0.3), rgba(250, 173, 20, 0.1));
  color: #faad14;
}

.stat-icon.red {
  background: linear-gradient(135deg, rgba(255, 77, 79, 0.3), rgba(255, 77, 79, 0.1));
  color: #ff4d4f;
}

.stat-num {
  font-size: 24px;
  font-weight: 700;
  color: #fff;
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 2px;
}

.sensor-gauge-grid {
  display: flex;
  padding: 16px;
  gap: 12px;
}

.gauge-wrapper {
  flex: 1;
  text-align: center;
}

.gauge-title {
  font-size: 13px;
  color: #1890ff;
  margin-bottom: 4px;
  font-weight: 600;
}

.gauge-chart {
  height: 140px;
}

.gauge-range {
  font-size: 11px;
  color: #8c8c8c;
  margin-top: -10px;
}

.weather-grid {
  display: flex;
  padding: 16px;
  gap: 12px;
  flex-wrap: wrap;
}

.weather-item {
  flex: 1;
  min-width: 80px;
  text-align: center;
  padding: 12px 8px;
  background: rgba(24, 144, 255, 0.1);
  border-radius: 8px;
  border: 1px solid rgba(24, 144, 255, 0.2);
}

.weather-icon {
  font-size: 28px;
  margin-bottom: 8px;
}

.weather-icon.blue { color: #1890ff; }
.weather-icon.cyan { color: #13c2c2; }
.weather-icon.green { color: #52c41a; }
.weather-icon.orange { color: #faad14; }

.weather-value {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}

.weather-label {
  font-size: 11px;
  color: #8c8c8c;
  margin-top: 4px;
}

.map-panel {
  flex: 1;
  min-height: 0;
}

.map-container {
  flex: 1;
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.zone-map {
  width: 100%;
  height: 100%;
  max-height: 450px;
}

.zone-rect {
  cursor: pointer;
  transition: all 0.3s ease;
}

.zone-rect:hover {
  filter: brightness(1.3);
  stroke-width: 3;
}

.zone-text {
  pointer-events: none;
  user-select: none;
}

.device-dot {
  cursor: pointer;
  transition: all 0.2s ease;
}

.device-dot:hover {
  r: 15;
  filter: drop-shadow(0 0 8px currentColor);
}

.device-text {
  pointer-events: none;
  user-select: none;
}

.legend-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}

.trend-chart {
  flex: 1;
  height: 250px;
  padding: 16px;
}

.alert-list {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
  max-height: 280px;
}

.alert-list::-webkit-scrollbar {
  width: 4px;
}

.alert-list::-webkit-scrollbar-thumb {
  background: rgba(24, 144, 255, 0.3);
  border-radius: 2px;
}

.alert-card {
  display: flex;
  gap: 12px;
  padding: 12px;
  margin-bottom: 10px;
  border-radius: 8px;
  border-left: 3px solid;
  animation: slideIn 0.3s ease;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.alert-card.critical {
  background: rgba(255, 77, 79, 0.15);
  border-color: #ff4d4f;
}

.alert-card.warning {
  background: rgba(250, 173, 20, 0.15);
  border-color: #faad14;
}

.alert-card.info {
  background: rgba(24, 144, 255, 0.15);
  border-color: #1890ff;
}

.alert-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.alert-card.critical .alert-icon { color: #ff4d4f; }
.alert-card.warning .alert-icon { color: #faad14; }
.alert-card.info .alert-icon { color: #1890ff; }

.alert-content {
  flex: 1;
  min-width: 0;
}

.alert-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.alert-type {
  font-size: 13px;
  font-weight: 600;
  color: #fff;
}

.alert-time {
  font-size: 11px;
  color: #8c8c8c;
}

.alert-message {
  font-size: 12px;
  color: #d9d9d9;
  margin-bottom: 4px;
}

.alert-detail {
  font-size: 11px;
  color: #8c8c8c;
}

.irrigation-status {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
  max-height: 250px;
}

.irrigation-item {
  padding: 12px;
  margin-bottom: 10px;
  background: rgba(24, 144, 255, 0.1);
  border-radius: 8px;
  border: 1px solid rgba(24, 144, 255, 0.2);
}

.irrigation-zone {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  margin-bottom: 8px;
}

.irrigation-detail {
  display: flex;
  align-items: center;
  gap: 12px;
}

.irrigation-progress {
  flex: 1;
}

.progress-label {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #8c8c8c;
  margin-bottom: 4px;
}

.progress-range {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: #8c8c8c;
  margin-top: 2px;
}

.progress-gap {
  position: absolute;
  top: 0;
  height: 100%;
  background: rgba(82, 196, 26, 0.2);
  border-radius: 4px;
}

.rotation-list {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
  max-height: 200px;
}

.rotation-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  margin-bottom: 8px;
  background: rgba(24, 144, 255, 0.1);
  border-radius: 8px;
  border: 1px solid rgba(24, 144, 255, 0.2);
  transition: all 0.3s ease;
}

.rotation-item.active {
  border-color: #52c41a;
  background: rgba(82, 196, 26, 0.15);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(82, 196, 26, 0.4); }
  50% { box-shadow: 0 0 0 8px rgba(82, 196, 26, 0); }
}

.rotation-time {
  font-size: 12px;
  font-weight: 600;
  color: #1890ff;
  white-space: nowrap;
  font-family: 'Courier New', monospace;
}

.rotation-info {
  flex: 1;
  min-width: 0;
}

.rotation-name {
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  margin-bottom: 2px;
}

.rotation-detail {
  font-size: 11px;
  color: #8c8c8c;
}

.device-detail {
  padding: 8px 0;
}

.device-actions {
  margin-top: 16px;
}

:deep(.ant-modal) {
  .ant-modal-content {
    background: #001529;
    border: 1px solid rgba(24, 144, 255, 0.3);
  }
  .ant-modal-header {
    background: transparent;
    border-bottom: 1px solid rgba(24, 144, 255, 0.3);
  }
  .ant-modal-title {
    color: #1890ff;
  }
  .ant-descriptions {
    .ant-descriptions-item-label {
      background: rgba(24, 144, 255, 0.1);
      color: #8c8c8c;
    }
    .ant-descriptions-item-content {
      color: #fff;
    }
  }
}

:deep(.ant-radio-button-wrapper) {
  background: rgba(24, 144, 255, 0.1);
  border-color: rgba(24, 144, 255, 0.3);
  color: #8c8c8c;
  
  &:hover {
    color: #1890ff;
  }
  
  &.ant-radio-button-wrapper-checked {
    background: #1890ff;
    border-color: #1890ff;
    color: #fff;
  }
}

:deep(.ant-progress) {
  position: relative;
}

:deep(.ant-empty-description) {
  color: #8c8c8c;
}
</style>
