<template>
  <div class="realtime-monitor">
    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :sm="12" :lg="6" v-for="sensor in soilSensors" :key="sensor.code">
        <div class="sensor-card soil">
          <div class="sensor-value">
            {{ sensor.humidity?.toFixed(1) || '--' }}
            <span class="sensor-unit">%</span>
          </div>
          <div class="sensor-label">{{ sensor.name }} - 土壤湿度</div>
          <div style="margin-top: 12px">
            <a-progress 
              :percent="sensor.humidity || 0" 
              :stroke-color="getHumidityColor(sensor.humidity)"
              show-info="false"
              stroke-width="8"
            />
          </div>
        </div>
      </a-col>
      
      <a-col :xs="24" :sm="12" :lg="6" v-for="sensor in weatherSensors" :key="sensor.code">
        <div class="sensor-card weather">
          <div class="sensor-value">
            {{ sensor.temperature?.toFixed(1) || '--' }}
            <span class="sensor-unit">°C</span>
          </div>
          <div class="sensor-label">{{ sensor.name }} - 空气温度</div>
          <div style="margin-top: 8px; display: flex; justify-content: space-between; font-size: 12px; opacity: 0.9">
            <span>湿度: {{ sensor.humidity?.toFixed(1) || '--' }}%</span>
            <span>光照: {{ sensor.light || '--' }}</span>
          </div>
        </div>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]" style="margin-top: 16px">
      <a-col :xs="24" :lg="12">
        <div class="card-container">
          <div class="page-title">土壤湿度实时趋势</div>
          <div class="large-chart-container">
            <v-chart :option="humidityChartOption" autoresize />
          </div>
        </div>
      </a-col>
      
      <a-col :xs="24" :lg="12">
        <div class="card-container">
          <div class="page-title">环境参数实时趋势</div>
          <div class="large-chart-container">
            <v-chart :option="environmentChartOption" autoresize />
          </div>
        </div>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]" style="margin-top: 16px">
      <a-col :xs="24" :lg="8" v-for="gauge in gauges" :key="gauge.title">
        <div class="card-container">
          <div class="page-title">{{ gauge.title }}</div>
          <div class="gauge-container">
            <v-chart :option="gauge.option" autoresize />
          </div>
        </div>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { sensorApi } from '@/api'
import type { SensorDataPoint } from '@/types'

interface SensorDisplay {
  code: string
  name: string
  humidity?: number
  temperature?: number
  light?: number
}

const soilSensorCodes = ['SENSOR-SOIL-001']
const weatherSensorCodes = ['SENSOR-WEATHER-001']

const soilSensors = ref<SensorDisplay[]>(
  soilSensorCodes.map(code => ({ code, name: code.replace('SENSOR-', '').replace(/-/g, ' ') }))
)
const weatherSensors = ref<SensorDisplay[]>(
  weatherSensorCodes.map(code => ({ code, name: code.replace('SENSOR-', '').replace(/-/g, ' ') }))
)

const humidityHistory = ref<number[]>([])
const temperatureHistory = ref<number[]>([])
const ecHistory = ref<number[]>([])
const phHistory = ref<number[]>([])
const timeLabels = ref<string[]>([])

let refreshTimer: number | null = null

const humidityChartOption = computed(() => ({
  tooltip: {
    trigger: 'axis'
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '3%',
    containLabel: true
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: timeLabels.value
  },
  yAxis: {
    type: 'value',
    name: '湿度(%)',
    min: 0,
    max: 100
  },
  series: [
    {
      name: '土壤湿度',
      type: 'line',
      smooth: true,
      areaStyle: {
        opacity: 0.3,
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: '#1890ff' },
            { offset: 1, color: 'rgba(24, 144, 255, 0.1)' }
          ]
        }
      },
      data: humidityHistory.value,
      lineStyle: {
        width: 3,
        color: '#1890ff'
      }
    }
  ]
}))

const environmentChartOption = computed(() => ({
  tooltip: {
    trigger: 'axis'
  },
  legend: {
    data: ['温度', '湿度', '光照']
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '3%',
    containLabel: true
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: timeLabels.value
  },
  yAxis: {
    type: 'value'
  },
  series: [
    {
      name: '温度',
      type: 'line',
      smooth: true,
      data: temperatureHistory.value,
      itemStyle: { color: '#fa8c16' }
    },
    {
      name: '湿度',
      type: 'line',
      smooth: true,
      data: Array.from({ length: temperatureHistory.value.length }, () => 60 + Math.random() * 15),
      itemStyle: { color: '#1890ff' }
    },
    {
      name: '光照',
      type: 'line',
      smooth: true,
      data: Array.from({ length: temperatureHistory.value.length }, () => 30000 + Math.random() * 20000),
      itemStyle: { color: '#fadb14' }
    }
  ]
}))

const currentHumidity = ref(65)
const currentEc = ref(1.8)
const currentPh = ref(6.5)

const gauges = computed(() => [
  {
    title: '土壤湿度',
    option: {
      series: [{
        type: 'gauge',
        startAngle: 180,
        endAngle: 0,
        min: 0,
        max: 100,
        splitNumber: 10,
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
          icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z',
          length: '60%',
          width: 8,
          offsetCenter: [0, '-10%'],
          itemStyle: { color: '#auto' }
        },
        axisTick: {
          length: 6,
          lineStyle: { color: 'auto', width: 1 }
        },
        splitLine: {
          length: 12,
          lineStyle: { color: 'auto', width: 2 }
        },
        axisLabel: {
          color: '#464646',
          fontSize: 12,
          distance: -40,
          formatter: '{value}%'
        },
        title: {
          offsetCenter: [0, '-20%'],
          fontSize: 14,
          color: '#8c8c8c'
        },
        detail: {
          fontSize: 24,
          offsetCenter: [0, '0%'],
          valueAnimation: true,
          formatter: '{value}%',
          color: currentHumidity.value < 30 ? '#ff4d4f' : currentHumidity.value < 60 ? '#faad14' : '#52c41a'
        },
        data: [{ value: currentHumidity.value, name: '当前湿度' }]
      }]
    }
  },
  {
    title: 'EC值',
    option: {
      series: [{
        type: 'gauge',
        startAngle: 180,
        endAngle: 0,
        min: 0,
        max: 5,
        splitNumber: 10,
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
          icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z',
          length: '60%',
          width: 8,
          offsetCenter: [0, '-10%'],
          itemStyle: { color: '#auto' }
        },
        axisTick: { length: 6, lineStyle: { color: 'auto', width: 1 } },
        splitLine: { length: 12, lineStyle: { color: 'auto', width: 2 } },
        axisLabel: {
          color: '#464646',
          fontSize: 12,
          distance: -40,
          formatter: '{value}'
        },
        detail: {
          fontSize: 24,
          offsetCenter: [0, '0%'],
          valueAnimation: true,
          formatter: '{value} mS/cm',
          color: '#52c41a'
        },
        data: [{ value: currentEc.value, name: 'EC值' }]
      }]
    }
  },
  {
    title: 'pH值',
    option: {
      series: [{
        type: 'gauge',
        startAngle: 180,
        endAngle: 0,
        min: 4,
        max: 9,
        splitNumber: 10,
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
          icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z',
          length: '60%',
          width: 8,
          offsetCenter: [0, '-10%'],
          itemStyle: { color: '#auto' }
        },
        axisTick: { length: 6, lineStyle: { color: 'auto', width: 1 } },
        splitLine: { length: 12, lineStyle: { color: 'auto', width: 2 } },
        axisLabel: {
          color: '#464646',
          fontSize: 12,
          distance: -40,
          formatter: '{value}'
        },
        detail: {
          fontSize: 24,
          offsetCenter: [0, '0%'],
          valueAnimation: true,
          formatter: '{value}',
          color: '#52c41a'
        },
        data: [{ value: currentPh.value, name: 'pH值' }]
      }]
    }
  }
])

function getHumidityColor(value?: number) {
  if (!value) return '#d9d9d9'
  if (value < 30) return '#ff4d4f'
  if (value < 60) return '#faad14'
  return '#52c41a'
}

async function fetchSensorData() {
  try {
    for (let i = 0; i < soilSensors.value.length; i++) {
      const data = await sensorApi.getLatest(soilSensors.value[i].code)
      soilSensors.value[i].humidity = data.humidity as number
      currentHumidity.value = data.humidity as number || 65
      currentEc.value = data.ec as number || 1.8
      currentPh.value = data.ph as number || 6.5
    }
    
    for (let i = 0; i < weatherSensors.value.length; i++) {
      const data = await sensorApi.getLatest(weatherSensors.value[i].code)
      weatherSensors.value[i].temperature = data.air_temperature as number
      weatherSensors.value[i].humidity = data.air_humidity as number
      weatherSensors.value[i].light = data.light as number
    }
    
    const now = new Date()
    const timeStr = `${now.getHours()}:${now.getMinutes().toString().padStart(2, '0')}`
    timeLabels.value.push(timeStr)
    if (timeLabels.value.length > 20) {
      timeLabels.value.shift()
    }
    
    humidityHistory.value.push(currentHumidity.value)
    temperatureHistory.value.push(weatherSensors.value[0].temperature || 25)
    if (humidityHistory.value.length > 20) {
      humidityHistory.value.shift()
      temperatureHistory.value.shift()
    }
    
    for (let i = 0; i < 3; i++) {
      if (Math.random() < 0.7) {
        soilSensors.value[i % soilSensors.value.length].humidity = 50 + Math.random() * 30
        weatherSensors.value[i % weatherSensors.value.length].temperature = 20 + Math.random() * 10
      }
    }
  } catch (e) {
    console.error('Failed to fetch sensor data:', e)
    for (let i = 0; i < soilSensors.value.length; i++) {
      soilSensors.value[i].humidity = 55 + Math.random() * 20
    }
    for (let i = 0; i < weatherSensors.value.length; i++) {
      weatherSensors.value[i].temperature = 22 + Math.random() * 8
      weatherSensors.value[i].humidity = 60 + Math.random() * 15
    }
  }
}

onMounted(async () => {
  await fetchSensorData()
  
  refreshTimer = window.setInterval(() => {
    fetchSensorData()
  }, 5000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>
