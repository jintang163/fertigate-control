<template>
  <div class="history-trends">
    <div class="card-container">
      <div class="filter-section">
        <a-row :gutter="[16, 16]">
          <a-col :xs="24" :sm="12" :lg="6">
            <div class="filter-label">灌区选择</div>
            <a-select
              v-model:value="selectedZones"
              mode="multiple"
              placeholder="请选择灌区"
              style="width: 100%"
              :max-tag-count="3"
            >
              <a-select-option v-for="zone in zones" :key="zone.id" :value="zone.id">
                {{ zone.name }}
              </a-select-option>
            </a-select>
          </a-col>

          <a-col :xs="24" :sm="12" :lg="6">
            <div class="filter-label">数据类型</div>
            <a-select
              v-model:value="selectedDataTypes"
              mode="multiple"
              placeholder="请选择数据类型"
              style="width: 100%"
              :max-tag-count="3"
            >
              <a-select-option v-for="type in dataTypes" :key="type.value" :value="type.value">
                {{ type.label }}
              </a-select-option>
            </a-select>
          </a-col>

          <a-col :xs="24" :sm="12" :lg="8">
            <div class="filter-label">时间范围</div>
            <a-range-picker
              v-model:value="dateRange"
              show-time
              format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </a-col>

          <a-col :xs="24" :sm="12" :lg="4">
            <div class="filter-label">快捷选择</div>
            <a-space>
              <a-button size="small" @click="setQuickTime('today')">今天</a-button>
              <a-button size="small" @click="setQuickTime('yesterday')">昨天</a-button>
              <a-button size="small" @click="setQuickTime('7days')">近7天</a-button>
              <a-button size="small" @click="setQuickTime('30days')">近30天</a-button>
            </a-space>
          </a-col>
        </a-row>

        <a-row :gutter="[16, 16]" style="margin-top: 16px">
          <a-col :xs="24" :sm="12" :lg="6">
            <div class="filter-label">数据对比</div>
            <a-space>
              <a-switch v-model:checked="compareMode" @change="onCompareModeChange" />
              <span v-if="compareMode" style="color: #52c41a">对比模式已开启</span>
            </a-space>
          </a-col>

          <a-col :xs="24" :sm="12" :lg="6" v-if="compareMode">
            <div class="filter-label">对比灌区1</div>
            <a-select v-model:value="compareZone1" placeholder="选择灌区1" style="width: 100%">
              <a-select-option v-for="zone in zones" :key="zone.id" :value="zone.id">
                {{ zone.name }}
              </a-select-option>
            </a-select>
          </a-col>

          <a-col :xs="24" :sm="12" :lg="6" v-if="compareMode">
            <div class="filter-label">对比灌区2</div>
            <a-select v-model:value="compareZone2" placeholder="选择灌区2" style="width: 100%">
              <a-select-option v-for="zone in zones" :key="zone.id" :value="zone.id">
                {{ zone.name }}
              </a-select-option>
            </a-select>
          </a-col>

          <a-col :xs="24" :sm="12" :lg="6">
            <div class="filter-label">实时刷新</div>
            <a-space>
              <a-switch v-model:checked="autoRefresh" @change="onAutoRefreshChange" />
              <a-select
                v-model:value="refreshInterval"
                :disabled="!autoRefresh"
                style="width: 100px"
                size="small"
              >
                <a-select-option :value="5000">5秒</a-select-option>
                <a-select-option :value="10000">10秒</a-select-option>
                <a-select-option :value="30000">30秒</a-select-option>
              </a-select>
            </a-space>
          </a-col>
        </a-row>

        <div class="action-buttons">
          <a-space>
            <a-button type="primary" :loading="loading" @click="fetchData">
              <SearchOutlined />
              查询
            </a-button>
            <a-button @click="resetFilters">
              <ReloadOutlined />
              重置
            </a-button>
            <a-button @click="exportChart">
              <DownloadOutlined />
              导出图片
            </a-button>
          </a-space>
        </div>
      </div>
    </div>

    <div class="card-container" style="margin-top: 16px">
      <div class="page-title">历史趋势曲线</div>
      <div class="large-chart-container">
        <v-chart ref="chartRef" :option="chartOption" autoresize theme="light" />
      </div>
    </div>

    <div class="card-container" style="margin-top: 16px">
      <div class="page-title">数据统计</div>
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :lg="6" v-for="stat in statistics" :key="stat.key">
          <div class="stat-card">
            <div style="display: flex; justify-content: space-between; align-items: flex-start">
              <div>
                <div class="stat-label" style="font-size: 16px; font-weight: 500; color: #262626">
                  {{ stat.label }}
                </div>
                <div class="stat-value" :style="{ color: stat.color }" style="margin-top: 12px">
                  {{ stat.avg }}
                  <span style="font-size: 14px; font-weight: 400">{{ stat.unit }}</span>
                </div>
              </div>
              <div class="stat-icon" :style="{ color: stat.color }">
                <component :is="stat.icon" :style="{ fontSize: '32px' }" />
              </div>
            </div>
            <a-divider style="margin: 12px 0" />
            <div class="stat-details">
              <div class="stat-detail-item">
                <span class="detail-label">最大值</span>
                <span class="detail-value">{{ stat.max }}</span>
              </div>
              <div class="stat-detail-item">
                <span class="detail-label">最小值</span>
                <span class="detail-value">{{ stat.min }}</span>
              </div>
              <div class="stat-detail-item">
                <span class="detail-label">标准差</span>
                <span class="detail-value">{{ stat.std }}</span>
              </div>
            </div>
          </div>
        </a-col>
      </a-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, shallowRef } from 'vue'
import { message } from 'ant-design-vue'
import type { ECharts } from 'echarts'
import {
  SearchOutlined,
  ReloadOutlined,
  DownloadOutlined,
  ThunderboltOutlined,
  DashboardOutlined,
  ExperimentOutlined,
  CloudOutlined,
  CloudServerOutlined
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { zoneApi, sensorApi, monitorApi } from '@/api'
import type { Zone, SensorDataPoint } from '@/types'

interface DataTypeConfig {
  value: string
  label: string
  unit: string
  color: string
  min: number
  max: number
  icon: any
}

interface StatisticsData {
  key: string
  label: string
  unit: string
  color: string
  avg: string
  max: string
  min: string
  std: string
  icon: any
}

interface ChartSeriesData {
  name: string
  type: string
  data: Array<[string, number]>
  yAxisIndex: number
  smooth: boolean
  lineStyle: {
    width: number
    color: string
  }
  itemStyle: {
    color: string
  }
  symbol: string
  symbolSize: number
}

const zones = ref<Zone[]>([])
const selectedZones = ref<string[]>([])
const selectedDataTypes = ref<string[]>(['humidity'])
const dateRange = ref<[dayjs.Dayjs, dayjs.Dayjs] | null>(null)

const compareMode = ref(false)
const compareZone1 = ref<string>('')
const compareZone2 = ref<string>('')

const autoRefresh = ref(false)
const refreshInterval = ref(5000)
let refreshTimer: number | null = null

const loading = ref(false)
const chartRef = shallowRef<ECharts | null>(null)
const chartData = ref<Map<string, SensorDataPoint[]>>(new Map())

const dataTypes: DataTypeConfig[] = [
  { value: 'humidity', label: '土壤湿度', unit: '%', color: '#1890ff', min: 0, max: 100, icon: DashboardOutlined },
  { value: 'ec', label: 'EC值', unit: 'mS/cm', color: '#52c41a', min: 0, max: 5, icon: ThunderboltOutlined },
  { value: 'ph', label: 'pH值', unit: '', color: '#faad14', min: 4, max: 9, icon: ExperimentOutlined },
  { value: 'temperature', label: '空气温度', unit: '°C', color: '#f5222d', min: -10, max: 50, icon: CloudOutlined },
  { value: 'airHumidity', label: '空气湿度', unit: '%', color: '#722ed1', min: 0, max: 100, icon: CloudServerOutlined }
]

const zoneColors = [
  '#1890ff', '#52c41a', '#faad14', '#f5222d', '#722ed1',
  '#13c2c2', '#eb2f96', '#fa8c16', '#a0d911', '#2f54eb'
]

const chartOption = computed(() => {
  const series: ChartSeriesData[] = []
  const yAxis: any[] = []
  const legendData: string[] = []

  const activeTypes = selectedDataTypes.value
  const activeZones = compareMode.value
    ? [compareZone1.value, compareZone2.value].filter(Boolean)
    : selectedZones.value

  if (activeTypes.length === 0 || activeZones.length === 0) {
    return {
      title: {
        text: '请选择灌区和数据类型查看趋势',
        left: 'center',
        top: 'center',
        textStyle: { color: '#8c8c8c', fontSize: 16, fontWeight: 'normal' }
      },
      tooltip: { trigger: 'axis' },
      grid: { left: '3%', right: '4%', bottom: '15%', top: '10%', containLabel: true },
      xAxis: { type: 'time', boundaryGap: false },
      yAxis: { type: 'value' },
      dataZoom: [
        { type: 'inside', start: 0, end: 100 },
        { type: 'slider', start: 0, end: 100, height: 20, bottom: 10 }
      ],
      series: []
    }
  }

  activeTypes.forEach((typeValue, typeIndex) => {
    const typeConfig = dataTypes.find(t => t.value === typeValue)!
    yAxis.push({
      type: 'value',
      name: `${typeConfig.label}(${typeConfig.unit})`,
      position: typeIndex % 2 === 0 ? 'left' : 'right',
      offset: Math.floor(typeIndex / 2) * 60,
      min: typeConfig.min,
      max: typeConfig.max,
      axisLine: {
        show: true,
        lineStyle: { color: typeConfig.color }
      },
      axisLabel: {
        formatter: `{value}${typeConfig.unit}`
      },
      splitLine: {
        show: typeIndex === 0,
        lineStyle: { type: 'dashed', opacity: 0.3 }
      }
    })

    activeZones.forEach((zoneId, zoneIndex) => {
      const zone = zones.value.find(z => z.id === zoneId)
      if (!zone) return

      const dataKey = `${zoneId}-${typeValue}`
      const dataPoints = chartData.value.get(dataKey) || []

      const seriesName = compareMode.value
        ? `${zone.name} - ${typeConfig.label}`
        : `${zone.name} - ${typeConfig.label}`

      legendData.push(seriesName)

      const color = compareMode.value
        ? zoneColors[zoneIndex % zoneColors.length]
        : zoneColors[(typeIndex * activeZones.length + zoneIndex) % zoneColors.length]

      series.push({
        name: seriesName,
        type: 'line',
        data: dataPoints.map(p => [p.time, p.value]),
        yAxisIndex: typeIndex,
        smooth: true,
        lineStyle: {
          width: 2,
          color
        },
        itemStyle: { color },
        symbol: 'circle',
        symbolSize: 4
      })
    })
  })

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
        label: {
          backgroundColor: '#6a7985'
        }
      },
      formatter: (params: any) => {
        if (!Array.isArray(params) || params.length === 0) return ''
        let result = `<div style="font-weight: 500; margin-bottom: 8px">${dayjs(params[0].value[0]).format('YYYY-MM-DD HH:mm:ss')}</div>`
        params.forEach((param: any) => {
          result += `<div style="display: flex; align-items: center; margin: 4px 0">
            <span style="display: inline-block; width: 10px; height: 10px; border-radius: 50%; background: ${param.color}; margin-right: 8px"></span>
            <span style="flex: 1">${param.seriesName}:</span>
            <span style="font-weight: 500; margin-left: 12px">${param.value[1]?.toFixed(2) || '--'}</span>
          </div>`
        })
        return result
      }
    },
    legend: {
      data: legendData,
      top: 0,
      type: 'scroll',
      textStyle: { fontSize: 12 }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      top: '12%',
      containLabel: true
    },
    xAxis: {
      type: 'time',
      boundaryGap: false,
      axisLabel: {
        formatter: (value: number) => dayjs(value).format('MM-DD HH:mm')
      }
    },
    yAxis,
    dataZoom: [
      {
        type: 'inside',
        start: 0,
        end: 100,
        zoomOnMouseWheel: true,
        moveOnMouseMove: true
      },
      {
        type: 'slider',
        start: 0,
        end: 100,
        height: 20,
        bottom: 10,
        brushSelect: true
      }
    ],
    series
  }
})

const statistics = computed<StatisticsData[]>(() => {
  const activeZones = compareMode.value
    ? [compareZone1.value, compareZone2.value].filter(Boolean)
    : selectedZones.value

  if (activeZones.length === 0) return []

  return selectedDataTypes.value.map(typeValue => {
    const typeConfig = dataTypes.find(t => t.value === typeValue)!
    const allValues: number[] = []

    activeZones.forEach(zoneId => {
      const dataKey = `${zoneId}-${typeValue}`
      const dataPoints = chartData.value.get(dataKey) || []
      dataPoints.forEach(p => allValues.push(p.value))
    })

    let avg = '--'
    let max = '--'
    let min = '--'
    let std = '--'

    if (allValues.length > 0) {
      const sum = allValues.reduce((a, b) => a + b, 0)
      const mean = sum / allValues.length
      const variance = allValues.reduce((acc, val) => acc + Math.pow(val - mean, 2), 0) / allValues.length

      avg = mean.toFixed(2)
      max = Math.max(...allValues).toFixed(2)
      min = Math.min(...allValues).toFixed(2)
      std = Math.sqrt(variance).toFixed(2)
    }

    return {
      key: typeValue,
      label: typeConfig.label,
      unit: typeConfig.unit,
      color: typeConfig.color,
      avg,
      max,
      min,
      std,
      icon: typeConfig.icon
    }
  })
})

function setQuickTime(type: string) {
  const now = dayjs()
  switch (type) {
    case 'today':
      dateRange.value = [now.startOf('day'), now]
      break
    case 'yesterday':
      dateRange.value = [now.subtract(1, 'day').startOf('day'), now.subtract(1, 'day').endOf('day')]
      break
    case '7days':
      dateRange.value = [now.subtract(7, 'day').startOf('day'), now]
      break
    case '30days':
      dateRange.value = [now.subtract(30, 'day').startOf('day'), now]
      break
  }
}

function onCompareModeChange(checked: boolean) {
  if (checked) {
    if (selectedZones.value.length >= 2) {
      compareZone1.value = selectedZones.value[0]
      compareZone2.value = selectedZones.value[1]
    }
  }
}

function onAutoRefreshChange(checked: boolean) {
  if (checked) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
}

function startAutoRefresh() {
  if (refreshTimer) clearInterval(refreshTimer)
  refreshTimer = window.setInterval(() => {
    if (dateRange.value) {
      const now = dayjs()
      const diff = dateRange.value[1].valueOf() - dateRange.value[0].valueOf()
      dateRange.value = [now.subtract(diff, 'millisecond'), now]
    }
    fetchData()
  }, refreshInterval.value)
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function resetFilters() {
  selectedZones.value = []
  selectedDataTypes.value = ['humidity']
  dateRange.value = null
  compareMode.value = false
  compareZone1.value = ''
  compareZone2.value = ''
  autoRefresh.value = false
  stopAutoRefresh()
  chartData.value.clear()
}

async function exportChart() {
  if (!chartRef.value) {
    message.warning('图表尚未初始化')
    return
  }
  try {
    const dataUrl = chartRef.value.getDataURL({
      type: 'png',
      pixelRatio: 2,
      backgroundColor: '#fff'
    })
    const link = document.createElement('a')
    link.download = `历史趋势_${dayjs().format('YYYYMMDD_HHmmss')}.png`
    link.href = dataUrl
    link.click()
    message.success('导出成功')
  } catch (e) {
    console.error('Export failed:', e)
    message.error('导出失败')
  }
}

function generateMockData(zoneId: string, dataType: string, startTime: dayjs.Dayjs, endTime: dayjs.Dayjs): SensorDataPoint[] {
  const typeConfig = dataTypes.find(t => t.value === dataType)
  if (!typeConfig) return []

  const zone = zones.value.find(z => z.id === zoneId)
  const zoneName = zone?.name || '未知灌区'

  const data: SensorDataPoint[] = []
  const duration = endTime.valueOf() - startTime.valueOf()
  const pointCount = Math.min(200, Math.max(24, Math.floor(duration / (30 * 60 * 1000))))
  const interval = duration / pointCount

  const baseValue = (typeConfig.min + typeConfig.max) / 2
  const amplitude = (typeConfig.max - typeConfig.min) * 0.3
  const zoneOffset = zoneId.charCodeAt(zoneId.length - 1) % 5

  for (let i = 0; i < pointCount; i++) {
    const time = startTime.valueOf() + i * interval
    const noise = (Math.random() - 0.5) * amplitude * 0.5
    const trend = Math.sin(i / pointCount * Math.PI * 2) * amplitude * 0.5
    const dailyCycle = Math.sin((time / (24 * 60 * 60 * 1000)) * Math.PI * 2) * amplitude * 0.3

    let value = baseValue + noise + trend + dailyCycle + zoneOffset
    value = Math.max(typeConfig.min, Math.min(typeConfig.max, value))

    data.push({
      time: dayjs(time).format('YYYY-MM-DD HH:mm:ss'),
      value: Number(value.toFixed(2)),
      device_code: `SENSOR-${dataType.toUpperCase()}-${zoneId}`,
      zone: zoneName
    })
  }

  return data
}

async function fetchData() {
  if (!dateRange.value) {
    message.warning('请选择时间范围')
    return
  }

  const activeZones = compareMode.value
    ? [compareZone1.value, compareZone2.value].filter(Boolean)
    : selectedZones.value

  if (activeZones.length === 0) {
    message.warning('请选择灌区')
    return
  }

  if (selectedDataTypes.value.length === 0) {
    message.warning('请选择数据类型')
    return
  }

  loading.value = true
  const startTime = dateRange.value[0].format('YYYY-MM-DD HH:mm:ss')
  const endTime = dateRange.value[1].format('YYYY-MM-DD HH:mm:ss')

  try {
    const newChartData = new Map<string, SensorDataPoint[]>()

    for (const zoneId of activeZones) {
      for (const dataType of selectedDataTypes.value) {
        const dataKey = `${zoneId}-${dataType}`
        try {
          const data = await monitorApi.getHistoricalData(zoneId, dataType, startTime, endTime)
          if (data && data.length > 0) {
            newChartData.set(dataKey, data)
          } else {
            newChartData.set(dataKey, generateMockData(zoneId, dataType, dateRange.value[0], dateRange.value[1]))
          }
        } catch (e) {
          console.warn(`Failed to fetch data for ${dataKey}, using mock data:`, e)
          newChartData.set(dataKey, generateMockData(zoneId, dataType, dateRange.value[0], dateRange.value[1]))
        }
      }
    }

    chartData.value = newChartData
    message.success('数据加载成功')
  } catch (e) {
    console.error('Fetch data failed:', e)
    message.error('数据加载失败，已使用模拟数据')
  } finally {
    loading.value = false
  }
}

async function loadZones() {
  try {
    const data = await zoneApi.getAll()
    zones.value = data
    if (data.length > 0) {
      selectedZones.value = [data[0].id]
    }
  } catch (e) {
    console.error('Failed to load zones:', e)
    zones.value = [
      { id: 'zone-001', name: '1号灌区', description: '东区', createdAt: '', updatedAt: '' },
      { id: 'zone-002', name: '2号灌区', description: '西区', createdAt: '', updatedAt: '' },
      { id: 'zone-003', name: '3号灌区', description: '南区', createdAt: '', updatedAt: '' },
      { id: 'zone-004', name: '4号灌区', description: '北区', createdAt: '', updatedAt: '' }
    ]
    selectedZones.value = [zones.value[0].id]
  }
}

onMounted(async () => {
  await loadZones()
  setQuickTime('7days')
  await fetchData()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.history-trends {
  padding: 24px;
  background-color: #f0f2f5;
  min-height: 100%;
}

.filter-section {
  background: #fff;
}

.filter-label {
  font-size: 13px;
  color: #595959;
  margin-bottom: 8px;
  font-weight: 500;
}

.action-buttons {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: flex-end;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03), 0 1px 6px -1px rgba(0, 0, 0, 0.02), 0 2px 4px rgba(0, 0, 0, 0.02);
  transition: all 0.3s ease;
  border: 1px solid #f0f0f0;
}

.stat-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.stat-value {
  font-size: 32px;
  font-weight: 600;
  line-height: 1.2;
}

.stat-label {
  font-size: 14px;
  color: #8c8c8c;
}

.stat-icon {
  opacity: 0.8;
}

.stat-details {
  display: flex;
  justify-content: space-between;
}

.stat-detail-item {
  text-align: center;
  flex: 1;
}

.detail-label {
  display: block;
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 4px;
}

.detail-value {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #262626;
}

.large-chart-container {
  width: 100%;
  height: 450px;
  margin-top: 16px;
}
</style>
