<template>
  <div class="dashboard">
    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :sm="12" :lg="6" v-for="stat in stats" :key="stat.label">
        <div class="stat-card">
          <a-row align="middle">
            <a-col :span="18">
              <div class="stat-value">{{ stat.value }}</div>
              <div class="stat-label">{{ stat.label }}</div>
            </a-col>
            <a-col :span="6" class="text-right">
              <component :is="stat.icon" class="stat-icon" :style="{ color: stat.color }" />
            </a-col>
          </a-row>
        </div>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]" style="margin-top: 16px">
      <a-col :xs="24" :lg="16">
        <div class="card-container">
          <div class="page-title">实时数据趋势</div>
          <div class="large-chart-container">
            <v-chart :option="chartOption" autoresize />
          </div>
        </div>
      </a-col>
      
      <a-col :xs="24" :lg="8">
        <div class="card-container">
          <div class="page-title">设备状态分布</div>
          <div class="chart-container">
            <v-chart :option="pieOption" autoresize />
          </div>
        </div>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]" style="margin-top: 16px">
      <a-col :xs="24" :lg="12">
        <div class="card-container">
          <div class="page-header">
            <span class="page-title">最新告警</span>
            <a type="link" @click="$router.push('/alerts')">查看全部</a>
          </div>
          <a-list
            :data-source="recentAlerts"
            :locale="{ emptyText: '暂无告警' }"
          >
            <template #renderItem="{ item }">
              <a-list-item>
                <div :class="['alert-item', item.level]">
                  <div class="alert-title">{{ item.alertType }}</div>
                  <div class="alert-message">{{ item.message }}</div>
                  <div class="alert-time">{{ formatTime(item.createdAt) }}</div>
                </div>
              </a-list-item>
            </template>
          </a-list>
        </div>
      </a-col>
      
      <a-col :xs="24" :lg="12">
        <div class="card-container">
          <div class="page-header">
            <span class="page-title">灌溉决策</span>
          </div>
          <a-spin :spinning="loading">
            <div v-for="decision in irrigationDecisions" :key="decision.zoneId" class="valve-control">
              <div class="valve-info">
                <div class="valve-name">{{ decision.zoneName }}</div>
                <div class="valve-status" :class="decision.needIrrigation ? 'open' : 'closed'">
                  {{ decision.needIrrigation ? '需要灌溉' : '无需灌溉' }}
                </div>
                <div style="font-size: 12px; color: #8c8c8c; margin-top: 4px">
                  当前湿度: {{ decision.currentHumidity?.toFixed(1) || '--' }}% / 
                  阈值: {{ decision.minHumidity }}% - {{ decision.maxHumidity }}%
                </div>
                <div style="font-size: 12px; color: #595959; margin-top: 4px">
                  {{ decision.reason }}
                </div>
              </div>
              <a-tag :color="decision.needIrrigation ? 'warning' : 'success'">
                {{ decision.needIrrigation ? '待执行' : '正常' }}
              </a-tag>
            </div>
          </a-spin>
        </div>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAppStore } from '@/stores'
import { storeToRefs } from 'pinia'
import { irrigationApi } from '@/api'
import type { IrrigationDecision, Alert } from '@/types'
import dayjs from 'dayjs'
import {
  DashboardOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  BulbOutlined,
  ThunderboltOutlined
} from '@ant-design/icons-vue'

const appStore = useAppStore()
const { overviewData, unacknowledgedAlerts, loading } = storeToRefs(appStore)

const irrigationDecisions = ref<IrrigationDecision[]>([])

const stats = computed(() => [
  {
    label: '设备总数',
    value: overviewData.value?.totalDevices || 0,
    icon: BulbOutlined,
    color: '#1890ff'
  },
  {
    label: '在线设备',
    value: overviewData.value?.onlineDevices || 0,
    icon: CheckCircleOutlined,
    color: '#52c41a'
  },
  {
    label: '开启阀门',
    value: overviewData.value?.openValves || 0,
    icon: ThunderboltOutlined,
    color: '#faad14'
  },
  {
    label: '未处理告警',
    value: unacknowledgedAlerts.value.length,
    icon: WarningOutlined,
    color: unacknowledgedAlerts.value.length > 0 ? '#ff4d4f' : '#52c41a'
  }
])

const recentAlerts = computed(() => {
  return unacknowledgedAlerts.value.slice(0, 5)
})

const chartOption = computed(() => {
  const hours = Array.from({ length: 24 }, (_, i) => `${i}:00`)
  const humidityData = Array.from({ length: 24 }, () => 50 + Math.random() * 30)
  const temperatureData = Array.from({ length: 24 }, () => 18 + Math.random() * 15)
  
  return {
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: ['土壤湿度', '空气温度']
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
      data: hours
    },
    yAxis: [
      {
        type: 'value',
        name: '湿度(%)',
        min: 0,
        max: 100
      },
      {
        type: 'value',
        name: '温度(°C)',
        min: 0,
        max: 50
      }
    ],
    series: [
      {
        name: '土壤湿度',
        type: 'line',
        smooth: true,
        areaStyle: {
          opacity: 0.3
        },
        data: humidityData,
        itemStyle: {
          color: '#1890ff'
        }
      },
      {
        name: '空气温度',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: temperatureData,
        itemStyle: {
          color: '#fa8c16'
        }
      }
    ]
  }
})

const pieOption = computed(() => {
  const online = overviewData.value?.onlineDevices || 0
  const offline = (overviewData.value?.totalDevices || 0) - online
  
  return {
    tooltip: {
      trigger: 'item'
    },
    legend: {
      bottom: '0',
      left: 'center'
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        labelLine: {
          show: false
        },
        data: [
          { value: online, name: '在线', itemStyle: { color: '#52c41a' } },
          { value: offline, name: '离线', itemStyle: { color: '#ff4d4f' } }
        ]
      }
    ]
  }
})

function formatTime(time: string) {
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

async function fetchDecisions() {
  try {
    irrigationDecisions.value = await irrigationApi.getAllDecisions()
  } catch (e) {
    console.error('Failed to fetch irrigation decisions:', e)
  }
}

onMounted(async () => {
  await appStore.fetchAll()
  await fetchDecisions()
  
  setInterval(async () => {
    await Promise.all([
      appStore.fetchOverview(),
      appStore.fetchAlerts(),
      fetchDecisions()
    ])
  }, 30000)
})
</script>
