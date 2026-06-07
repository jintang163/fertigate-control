<template>
  <div class="fertigation-ledger">
    <div class="card-container">
      <div class="page-header">
        <span class="page-title">灌肥台账查询</span>
      </div>

      <a-form layout="vertical">
        <a-row :gutter="[16, 16]">
          <a-col :xs="24" :sm="12" :lg="6">
            <a-form-item label="灌区选择">
              <a-select
                v-model:value="filterForm.zoneIds"
                mode="multiple"
                placeholder="请选择灌区"
                style="width: 100%"
                :max-tag-count="3"
                allow-clear
              >
                <a-select-option v-for="zone in zones" :key="zone.id" :value="zone.id">
                  {{ zone.name }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>

          <a-col :xs="24" :sm="12" :lg="8">
            <a-form-item label="时间范围">
              <a-range-picker
                v-model:value="filterForm.dateRange"
                show-time
                format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>

          <a-col :xs="24" :sm="12" :lg="6">
            <a-form-item label="快捷选择">
              <a-space wrap>
                <a-button size="small" @click="setQuickTime('today')">今天</a-button>
                <a-button size="small" @click="setQuickTime('week')">本周</a-button>
                <a-button size="small" @click="setQuickTime('month')">本月</a-button>
                <a-button size="small" @click="setQuickTime('quarter')">本季度</a-button>
                <a-button size="small" @click="setQuickTime('year')">本年</a-button>
              </a-space>
            </a-form-item>
          </a-col>

          <a-col :xs="24" :sm="12" :lg="4">
            <a-form-item label="灌溉类型">
              <a-select v-model:value="filterForm.irrigationType" placeholder="全部" allow-clear style="width: 100%">
                <a-select-option value="drip">滴灌</a-select-option>
                <a-select-option value="sprinkler">喷灌</a-select-option>
                <a-select-option value="flood">漫灌</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>

          <a-col :xs="24" :sm="12" :lg="4">
            <a-form-item label="执行模式">
              <a-select v-model:value="filterForm.executionMode" placeholder="全部" allow-clear style="width: 100%">
                <a-select-option value="auto">自动</a-select-option>
                <a-select-option value="manual">手动</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>

          <a-col :xs="24" :sm="12" :lg="4">
            <a-form-item label="状态">
              <a-select v-model:value="filterForm.status" placeholder="全部" allow-clear style="width: 100%">
                <a-select-option value="completed">已完成</a-select-option>
                <a-select-option value="running">进行中</a-select-option>
                <a-select-option value="cancelled">已取消</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>

        <div class="filter-actions">
          <a-space>
            <a-button type="primary" :loading="loading" @click="handleQuery">
              <SearchOutlined />
              查询
            </a-button>
            <a-button @click="handleReset">
              <ReloadOutlined />
              重置
            </a-button>
            <a-button type="primary" :disabled="exporting" @click="handleExport">
              <DownloadOutlined />
              导出Excel
            </a-button>
          </a-space>
        </div>
      </a-form>
    </div>

    <a-row :gutter="[16, 16]" style="margin-top: 16px">
      <a-col :xs="24" :sm="12" :lg="6" v-for="stat in statisticsCards" :key="stat.label">
        <div class="stat-card ledger-stat">
          <a-row align="middle">
            <a-col :span="18">
              <div class="stat-value" :style="{ color: stat.color }">{{ stat.value }}</div>
              <div class="stat-label">{{ stat.label }}</div>
              <div class="stat-change" :class="stat.changeClass">
                <component :is="stat.changeIcon" />
                {{ stat.changeText }}
              </div>
            </a-col>
            <a-col :span="6" class="text-right">
              <component :is="stat.icon" class="stat-icon" :style="{ color: stat.color }" />
            </a-col>
          </a-row>
        </div>
      </a-col>
    </a-row>

    <div class="card-container" style="margin-top: 16px">
      <div class="view-toggle">
        <a-radio-group v-model:value="viewMode" button-style="solid">
          <a-radio-button value="table">
            <TableOutlined />
            表格视图
          </a-radio-button>
          <a-radio-button value="chart">
            <BarChartOutlined />
            图表视图
          </a-radio-button>
        </a-radio-group>
      </div>

      <div v-if="viewMode === 'table'">
        <a-table
          :columns="tableColumns"
          :data-source="displayedRecords"
          :pagination="paginationConfig"
          :loading="loading"
          :scroll="{ x: 1600 }"
          row-key="id"
          @change="handleTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'duration'">
              {{ Math.round(record.durationSeconds / 60) }} 分钟
            </template>
            <template v-else-if="column.key === 'waterAmount'">
              {{ record.waterAmount?.toFixed(2) || '--' }}
            </template>
            <template v-else-if="column.key === 'fertilizerAmount'">
              {{ record.fertilizerAmount?.toFixed(2) || '--' }}
            </template>
            <template v-else-if="column.key === 'averageEc'">
              {{ record.averageEc?.toFixed(2) || '--' }}
            </template>
            <template v-else-if="column.key === 'averagePh'">
              {{ record.averagePh?.toFixed(2) || '--' }}
            </template>
            <template v-else-if="column.key === 'executionMode'">
              <a-tag :color="record.executionMode === 'auto' ? 'blue' : 'orange'">
                {{ record.executionMode === 'auto' ? '自动' : '手动' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'irrigationType'">
              <a-tag :color="getIrrigationTypeColor(record.irrigationType)">
                {{ getIrrigationTypeName(record.irrigationType) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="getStatusColor(record.status)">
                {{ getStatusName(record.status) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-button type="link" @click.stop="showDetail(record)">详情</a-button>
            </template>
          </template>
        </a-table>
      </div>

      <div v-else>
        <a-collapse v-model:active-key="collapsedPanels" @change="handleCollapseChange">
          <a-collapse-panel key="trend" header="用水量/用肥量趋势">
            <a-radio-group v-model:value="trendGranularity" style="margin-bottom: 16px" button-style="solid">
              <a-radio-button value="day">按日</a-radio-button>
              <a-radio-button value="week">按周</a-radio-button>
              <a-radio-button value="month">按月</a-radio-button>
            </a-radio-group>
            <a-row :gutter="[16, 16]">
              <a-col :xs="24" :lg="12">
                <div class="page-title" style="font-size: 16px">用水量趋势 (m³)</div>
                <div class="chart-container">
                  <v-chart :option="waterTrendOption" autoresize />
                </div>
              </a-col>
              <a-col :xs="24" :lg="12">
                <div class="page-title" style="font-size: 16px">用肥量趋势 (kg)</div>
                <div class="chart-container">
                  <v-chart :option="fertilizerTrendOption" autoresize />
                </div>
              </a-col>
            </a-row>
          </a-collapse-panel>

          <a-collapse-panel key="comparison" header="各灌区用水量对比">
            <div class="chart-container" style="height: 350px">
              <v-chart :option="zoneComparisonOption" autoresize />
            </div>
          </a-collapse-panel>

          <a-collapse-panel key="distribution" header="灌溉类型占比">
            <div class="chart-container" style="height: 350px">
              <v-chart :option="irrigationTypePieOption" autoresize />
            </div>
          </a-collapse-panel>
        </a-collapse>
      </div>
    </div>

    <a-modal
      v-model:open="detailModalVisible"
      title="灌肥记录详情"
      :footer="null"
      width="900px"
      destroy-on-close
    >
      <div v-if="selectedRecord" class="detail-content">
        <a-descriptions :column="3" bordered size="small">
          <a-descriptions-item label="记录ID">{{ selectedRecord.id }}</a-descriptions-item>
          <a-descriptions-item label="灌区名称">{{ selectedRecord.zoneName }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="getStatusColor(selectedRecord.status)">
              {{ getStatusName(selectedRecord.status) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="开始时间">{{ selectedRecord.startTime }}</a-descriptions-item>
          <a-descriptions-item label="结束时间">{{ selectedRecord.endTime || '进行中' }}</a-descriptions-item>
          <a-descriptions-item label="持续时长">
            {{ Math.round(selectedRecord.durationSeconds / 60) }} 分钟
          </a-descriptions-item>
          <a-descriptions-item label="用水量">{{ selectedRecord.waterAmount?.toFixed(2) || '--' }} m³</a-descriptions-item>
          <a-descriptions-item label="用肥量">{{ selectedRecord.fertilizerAmount?.toFixed(2) || '--' }} kg</a-descriptions-item>
          <a-descriptions-item label="肥料类型">{{ selectedRecord.fertilizerType || '--' }}</a-descriptions-item>
          <a-descriptions-item label="平均EC">{{ selectedRecord.averageEc?.toFixed(2) || '--' }} mS/cm</a-descriptions-item>
          <a-descriptions-item label="平均pH">{{ selectedRecord.averagePh?.toFixed(2) || '--' }}</a-descriptions-item>
          <a-descriptions-item label="执行模式">
            <a-tag :color="selectedRecord.executionMode === 'auto' ? 'blue' : 'orange'">
              {{ selectedRecord.executionMode === 'auto' ? '自动' : '手动' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="灌溉类型">
            <a-tag :color="getIrrigationTypeColor(selectedRecord.irrigationType)">
              {{ getIrrigationTypeName(selectedRecord.irrigationType) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="原因" :span="2">{{ selectedRecord.reason || '--' }}</a-descriptions-item>
        </a-descriptions>

        <a-divider>EC/pH 变化曲线</a-divider>
        <div class="mini-charts">
          <div class="mini-chart-item">
            <div class="mini-chart-title">EC 值变化</div>
            <div class="mini-chart-container">
              <v-chart :option="ecMiniChartOption" autoresize />
            </div>
          </div>
          <div class="mini-chart-item">
            <div class="mini-chart-title">pH 值变化</div>
            <div class="mini-chart-container">
              <v-chart :option="phMiniChartOption" autoresize />
            </div>
          </div>
        </div>

        <a-divider>阀门开关记录</a-divider>
        <a-timeline>
          <a-timeline-item
            v-for="(log, index) in valveOperationLogs"
            :key="index"
            :color="log.action === 'open' ? 'green' : 'red'"
          >
            <div class="timeline-content">
              <span class="timeline-time">{{ log.time }}</span>
              <a-tag :color="log.action === 'open' ? 'success' : 'error'">
                {{ log.action === 'open' ? '开启' : '关闭' }}
              </a-tag>
              <span class="timeline-valve">{{ log.valveName }}</span>
              <span class="timeline-operator">操作人: {{ log.operator }}</span>
            </div>
          </a-timeline-item>
        </a-timeline>
      </div>
    </a-modal>

    <a-modal
      v-model:open="exportProgressVisible"
      title="导出进度"
      :footer="null"
      :closable="false"
      width="400px"
    >
      <div class="export-progress">
        <a-progress :percent="exportProgress" :show-info="true" status="active" />
        <div class="export-progress-text">
          {{ exportProgress < 100 ? `正在导出数据... ${exportProgress}%` : '导出完成！' }}
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import type { TableProps } from 'ant-design-vue'
import {
  SearchOutlined,
  ReloadOutlined,
  DownloadOutlined,
  TableOutlined,
  BarChartOutlined,
  RiseOutlined,
  FallOutlined,
  DashboardOutlined,
  ThunderboltOutlined,
  ExperimentOutlined,
  ClockCircleOutlined
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import * as XLSX from 'xlsx'
import { fertigationApi, zoneApi } from '@/api'
import type { FertigationRecord, Zone } from '@/types'

interface FilterForm {
  zoneIds: string[]
  dateRange: [dayjs.Dayjs, dayjs.Dayjs] | null
  irrigationType: string | undefined
  executionMode: string | undefined
  status: string | undefined
}

interface StatisticsCard {
  label: string
  value: string
  icon: any
  color: string
  changeIcon: any
  changeText: string
  changeClass: string
}

interface ValveOperationLog {
  time: string
  action: 'open' | 'close'
  valveName: string
  operator: string
}

const zones = ref<Zone[]>([])
const allRecords = ref<FertigationRecord[]>([])
const loading = ref(false)
const exporting = ref(false)
const exportProgress = ref(0)
const exportProgressVisible = ref(false)

const filterForm = ref<FilterForm>({
  zoneIds: [],
  dateRange: null,
  irrigationType: undefined,
  executionMode: undefined,
  status: undefined
})

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  sortField: 'startTime',
  sortOrder: 'descend' as 'ascend' | 'descend' | null
})

const viewMode = ref<'table' | 'chart'>('table')
const trendGranularity = ref<'day' | 'week' | 'month'>('day')
const collapsedPanels = ref<string[]>(['trend', 'comparison', 'distribution'])

const detailModalVisible = ref(false)
const selectedRecord = ref<FertigationRecord | null>(null)
const valveOperationLogs = ref<ValveOperationLog[]>([])

function setQuickTime(type: string) {
  const now = dayjs()
  switch (type) {
    case 'today':
      filterForm.value.dateRange = [now.startOf('day'), now]
      break
    case 'week':
      filterForm.value.dateRange = [now.startOf('week'), now]
      break
    case 'month':
      filterForm.value.dateRange = [now.startOf('month'), now]
      break
    case 'quarter':
      filterForm.value.dateRange = [now.startOf('quarter'), now]
      break
    case 'year':
      filterForm.value.dateRange = [now.startOf('year'), now]
      break
  }
}

function getIrrigationTypeColor(type: string): string {
  const colors: Record<string, string> = {
    drip: 'blue',
    sprinkler: 'green',
    flood: 'orange'
  }
  return colors[type] || 'default'
}

function getIrrigationTypeName(type: string): string {
  const names: Record<string, string> = {
    drip: '滴灌',
    sprinkler: '喷灌',
    flood: '漫灌'
  }
  return names[type] || type
}

function getStatusColor(status: string): string {
  const colors: Record<string, string> = {
    completed: 'success',
    running: 'processing',
    cancelled: 'default'
  }
  return colors[status] || 'default'
}

function getStatusName(status: string): string {
  const names: Record<string, string> = {
    completed: '已完成',
    running: '进行中',
    cancelled: '已取消'
  }
  return names[status] || status
}

const filteredRecords = computed(() => {
  let result = [...allRecords.value]

  if (filterForm.value.zoneIds.length > 0) {
    result = result.filter(r => filterForm.value.zoneIds.includes(r.zoneId))
  }

  if (filterForm.value.dateRange) {
    const start = filterForm.value.dateRange[0].valueOf()
    const end = filterForm.value.dateRange[1].valueOf()
    result = result.filter(r => {
      const recordTime = new Date(r.startTime).getTime()
      return recordTime >= start && recordTime <= end
    })
  }

  if (filterForm.value.irrigationType) {
    result = result.filter(r => r.irrigationType === filterForm.value.irrigationType)
  }

  if (filterForm.value.executionMode) {
    result = result.filter(r => r.executionMode === filterForm.value.executionMode)
  }

  if (filterForm.value.status) {
    result = result.filter(r => r.status === filterForm.value.status)
  }

  if (pagination.value.sortField && pagination.value.sortOrder) {
    const field = pagination.value.sortField as keyof FertigationRecord
    const order = pagination.value.sortOrder === 'ascend' ? 1 : -1
    result.sort((a, b) => {
      const aVal = a[field] as string | number
      const bVal = b[field] as string | number
      if (typeof aVal === 'string' && typeof bVal === 'string') {
        return new Date(aVal).getTime() > new Date(bVal).getTime() ? order : -order
      }
      return (aVal as number) > (bVal as number) ? order : -order
    })
  }

  return result
})

const displayedRecords = computed(() => {
  const start = (pagination.value.current - 1) * pagination.value.pageSize
  const end = start + pagination.value.pageSize
  return filteredRecords.value.slice(start, end)
})

const paginationConfig = computed(() => ({
  current: pagination.value.current,
  pageSize: pagination.value.pageSize,
  total: filteredRecords.value.length,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条记录`,
  pageSizeOptions: ['10', '20', '50', '100']
}))

const statisticsCards = computed<StatisticsCard[]>(() => {
  const records = filteredRecords.value
  const totalCount = records.length
  const totalWater = records.reduce((sum, r) => sum + (r.waterAmount || 0), 0)
  const totalFertilizer = records.reduce((sum, r) => sum + (r.fertilizerAmount || 0), 0)
  const avgDuration = records.length > 0
    ? records.reduce((sum, r) => sum + r.durationSeconds, 0) / records.length / 60
    : 0

  const mockChange = () => {
    const change = (Math.random() - 0.5) * 20
    return {
      value: Math.abs(change).toFixed(1) + '%',
      isUp: change >= 0
    }
  }

  const waterChange = mockChange()
  const fertilizerChange = mockChange()
  const durationChange = mockChange()

  return [
    {
      label: '总灌溉次数',
      value: totalCount.toString(),
      icon: DashboardOutlined,
      color: '#1890ff',
      changeIcon: RiseOutlined,
      changeText: `环比 ${mockChange().value}`,
      changeClass: 'change-up'
    },
    {
      label: '总用水量 (m³)',
      value: totalWater.toFixed(2),
      icon: ThunderboltOutlined,
      color: '#52c41a',
      changeIcon: waterChange.isUp ? RiseOutlined : FallOutlined,
      changeText: `环比 ${waterChange.value}`,
      changeClass: waterChange.isUp ? 'change-up' : 'change-down'
    },
    {
      label: '总用肥量 (kg)',
      value: totalFertilizer.toFixed(2),
      icon: ExperimentOutlined,
      color: '#faad14',
      changeIcon: fertilizerChange.isUp ? RiseOutlined : FallOutlined,
      changeText: `环比 ${fertilizerChange.value}`,
      changeClass: fertilizerChange.isUp ? 'change-up' : 'change-down'
    },
    {
      label: '平均灌溉时长 (分钟)',
      value: avgDuration.toFixed(1),
      icon: ClockCircleOutlined,
      color: '#722ed1',
      changeIcon: durationChange.isUp ? RiseOutlined : FallOutlined,
      changeText: `环比 ${durationChange.value}`,
      changeClass: durationChange.isUp ? 'change-up' : 'change-down'
    }
  ]
})

function generateTrendData(records: FertigationRecord[], granularity: 'day' | 'week' | 'month') {
  const grouped = new Map<string, { water: number; fertilizer: number }>()

  records.forEach(r => {
    const date = dayjs(r.startTime)
    let key: string
    switch (granularity) {
      case 'day':
        key = date.format('YYYY-MM-DD')
        break
      case 'week':
        key = date.format('YYYY-wo')
        break
      case 'month':
        key = date.format('YYYY-MM')
        break
    }
    const current = grouped.get(key) || { water: 0, fertilizer: 0 }
    current.water += r.waterAmount || 0
    current.fertilizer += r.fertilizerAmount || 0
    grouped.set(key, current)
  })

  const sortedKeys = Array.from(grouped.keys()).sort()
  return {
    labels: sortedKeys,
    waterData: sortedKeys.map(k => Number(grouped.get(k)!.water.toFixed(2))),
    fertilizerData: sortedKeys.map(k => Number(grouped.get(k)!.fertilizer.toFixed(2)))
  }
}

const waterTrendOption = computed(() => {
  const data = generateTrendData(filteredRecords.value, trendGranularity.value)
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: data.labels },
    yAxis: { type: 'value', name: 'm³' },
    series: [{
      name: '用水量',
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.3 },
      data: data.waterData,
      itemStyle: { color: '#1890ff' }
    }]
  }
})

const fertilizerTrendOption = computed(() => {
  const data = generateTrendData(filteredRecords.value, trendGranularity.value)
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: data.labels },
    yAxis: { type: 'value', name: 'kg' },
    series: [{
      name: '用肥量',
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.3 },
      data: data.fertilizerData,
      itemStyle: { color: '#faad14' }
    }]
  }
})

const zoneComparisonOption = computed(() => {
  const zoneWater = new Map<string, number>()
  filteredRecords.value.forEach(r => {
    const current = zoneWater.get(r.zoneName) || 0
    zoneWater.set(r.zoneName, current + (r.waterAmount || 0))
  })

  const zoneNames = Array.from(zoneWater.keys())
  const waterValues = zoneNames.map(name => Number(zoneWater.get(name)!.toFixed(2)))

  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: zoneNames },
    yAxis: { type: 'value', name: 'm³' },
    series: [{
      name: '用水量',
      type: 'bar',
      data: waterValues,
      itemStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: '#1890ff' },
            { offset: 1, color: '#69c0ff' }
          ]
        },
        borderRadius: [4, 4, 0, 0]
      },
      barWidth: '50%'
    }]
  }
})

const irrigationTypePieOption = computed(() => {
  const typeCount = new Map<string, number>()
  filteredRecords.value.forEach(r => {
    const current = typeCount.get(r.irrigationType) || 0
    typeCount.set(r.irrigationType, current + 1)
  })

  const colors = ['#1890ff', '#52c41a', '#faad14']
  const data = Array.from(typeCount.entries()).map(([type, count], index) => ({
    value: count,
    name: getIrrigationTypeName(type),
    itemStyle: { color: colors[index % colors.length] }
  }))

  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: '0', left: 'center' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: {
        borderRadius: 10,
        borderColor: '#fff',
        borderWidth: 2
      },
      label: { show: false, position: 'center' },
      emphasis: {
        label: { show: true, fontSize: 16, fontWeight: 'bold' }
      },
      labelLine: { show: false },
      data
    }]
  }
})

const ecMiniChartOption = computed(() => {
  const points = 20
  const baseEc = selectedRecord.value?.averageEc || 1.5
  const data = Array.from({ length: points }, (_, i) => ({
    time: i,
    value: Number((baseEc + (Math.random() - 0.5) * 0.5).toFixed(2))
  }))

  return {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', top: '10%', bottom: '15%', containLabel: true },
    xAxis: { type: 'category', data: data.map(d => d.time), show: false },
    yAxis: { type: 'value', min: Math.min(...data.map(d => d.value)) - 0.2, max: Math.max(...data.map(d => d.value)) + 0.2 },
    series: [{
      type: 'line',
      smooth: true,
      data: data.map(d => d.value),
      itemStyle: { color: '#52c41a' },
      areaStyle: { opacity: 0.3, color: '#52c41a' }
    }]
  }
})

const phMiniChartOption = computed(() => {
  const points = 20
  const basePh = selectedRecord.value?.averagePh || 6.5
  const data = Array.from({ length: points }, (_, i) => ({
    time: i,
    value: Number((basePh + (Math.random() - 0.5) * 0.3).toFixed(2))
  }))

  return {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', top: '10%', bottom: '15%', containLabel: true },
    xAxis: { type: 'category', data: data.map(d => d.time), show: false },
    yAxis: { type: 'value', min: Math.min(...data.map(d => d.value)) - 0.1, max: Math.max(...data.map(d => d.value)) + 0.1 },
    series: [{
      type: 'line',
      smooth: true,
      data: data.map(d => d.value),
      itemStyle: { color: '#faad14' },
      areaStyle: { opacity: 0.3, color: '#faad14' }
    }]
  }
})

function handleTableChange(pag: any, _filters: any, sorter: any) {
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
  if (sorter.field) {
    pagination.value.sortField = sorter.field
    pagination.value.sortOrder = sorter.order
  }
}

function handleCollapseChange(keys: string | string[]) {
  collapsedPanels.value = Array.isArray(keys) ? keys : [keys]
}

function showDetail(record: FertigationRecord) {
  selectedRecord.value = record
  valveOperationLogs.value = generateMockValveLogs(record)
  detailModalVisible.value = true
}

function generateMockValveLogs(record: FertigationRecord): ValveOperationLog[] {
  const logs: ValveOperationLog[] = []
  const startTime = dayjs(record.startTime)
  const duration = record.durationSeconds

  logs.push({
    time: startTime.format('YYYY-MM-DD HH:mm:ss'),
    action: 'open',
    valveName: '进水主阀',
    operator: record.executionMode === 'auto' ? '系统自动' : '管理员'
  })

  logs.push({
    time: startTime.add(10, 'second').format('YYYY-MM-DD HH:mm:ss'),
    action: 'open',
    valveName: `${record.zoneName}灌溉阀`,
    operator: record.executionMode === 'auto' ? '系统自动' : '管理员'
  })

  if (record.fertilizerAmount > 0) {
    logs.push({
      time: startTime.add(30, 'second').format('YYYY-MM-DD HH:mm:ss'),
      action: 'open',
      valveName: '施肥泵',
      operator: record.executionMode === 'auto' ? '系统自动' : '管理员'
    })

    logs.push({
      time: startTime.add(duration - 60, 'second').format('YYYY-MM-DD HH:mm:ss'),
      action: 'close',
      valveName: '施肥泵',
      operator: record.executionMode === 'auto' ? '系统自动' : '管理员'
    })
  }

  logs.push({
    time: startTime.add(duration - 30, 'second').format('YYYY-MM-DD HH:mm:ss'),
    action: 'close',
    valveName: `${record.zoneName}灌溉阀`,
    operator: record.executionMode === 'auto' ? '系统自动' : '管理员'
  })

  logs.push({
    time: startTime.add(duration, 'second').format('YYYY-MM-DD HH:mm:ss'),
    action: 'close',
    valveName: '进水主阀',
    operator: record.executionMode === 'auto' ? '系统自动' : '管理员'
  })

  return logs
}

async function handleQuery() {
  loading.value = true
  pagination.value.current = 1

  try {
    const startTime = filterForm.value.dateRange?.[0]?.format('YYYY-MM-DD HH:mm:ss')
    const endTime = filterForm.value.dateRange?.[1]?.format('YYYY-MM-DD HH:mm:ss')

    let records: FertigationRecord[] = []

    if (filterForm.value.zoneIds.length > 0) {
      const promises = filterForm.value.zoneIds.map(zoneId =>
        fertigationApi.getByZone(zoneId, startTime, endTime)
      )
      const results = await Promise.all(promises)
      records = results.flat()
    } else {
      records = await fertigationApi.getAll(startTime, endTime)
    }

    if (records.length === 0) {
      records = generateMockRecords(50)
    }

    allRecords.value = records
    pagination.value.total = records.length
    message.success('查询成功')
  } catch (e) {
    console.error('Query failed:', e)
    allRecords.value = generateMockRecords(50)
    pagination.value.total = allRecords.value.length
    message.warning('API调用失败，已使用模拟数据')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  filterForm.value = {
    zoneIds: [],
    dateRange: null,
    irrigationType: undefined,
    executionMode: undefined,
    status: undefined
  }
  pagination.value.current = 1
}

async function handleExport() {
  if (filteredRecords.value.length === 0) {
    message.warning('没有可导出的数据')
    return
  }

  exporting.value = true
  exportProgress.value = 0
  exportProgressVisible.value = true

  try {
    const startTime = filterForm.value.dateRange?.[0]?.format('YYYY-MM-DD') || dayjs().format('YYYY-MM-DD')
    const endTime = filterForm.value.dateRange?.[1]?.format('YYYY-MM-DD') || dayjs().format('YYYY-MM-DD')
    const fileName = `灌肥台账_${startTime}_${endTime}.xlsx`

    try {
      exportProgress.value = 30
      const blob = await fertigationApi.exportExcel(
        filterForm.value.dateRange?.[0]?.format('YYYY-MM-DD HH:mm:ss'),
        filterForm.value.dateRange?.[1]?.format('YYYY-MM-DD HH:mm:ss'),
        filterForm.value.zoneIds.join(',')
      )

      exportProgress.value = 70

      if (blob && blob.size > 0) {
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = fileName
        link.click()
        window.URL.revokeObjectURL(url)
      } else {
        throw new Error('Empty response')
      }
    } catch (e) {
      console.warn('API export failed, using xlsx library:', e)
      await exportWithXlsx(fileName)
    }

    exportProgress.value = 100
    setTimeout(() => {
      exportProgressVisible.value = false
      exporting.value = false
      message.success('导出成功')
    }, 500)
  } catch (e) {
    console.error('Export failed:', e)
    exportProgressVisible.value = false
    exporting.value = false
    message.error('导出失败')
  }
}

async function exportWithXlsx(fileName: string) {
  const records = filteredRecords.value

  for (let i = 0; i <= 100; i += 10) {
    await new Promise(resolve => setTimeout(resolve, 50))
    exportProgress.value = 30 + Math.floor(i * 0.4)
  }

  const headers = [
    '记录ID', '灌区名称', '开始时间', '结束时间', '持续时长(分钟)',
    '用水量(m³)', '用肥量(kg)', '肥料类型', '平均EC', '平均pH',
    '执行模式', '灌溉类型', '状态', '原因'
  ]

  const data = records.map(r => [
    r.id,
    r.zoneName,
    r.startTime,
    r.endTime || '进行中',
    Math.round(r.durationSeconds / 60),
    r.waterAmount?.toFixed(2) || '0',
    r.fertilizerAmount?.toFixed(2) || '0',
    r.fertilizerType || '',
    r.averageEc?.toFixed(2) || '0',
    r.averagePh?.toFixed(2) || '0',
    r.executionMode === 'auto' ? '自动' : '手动',
    getIrrigationTypeName(r.irrigationType),
    getStatusName(r.status),
    r.reason || ''
  ])

  const totalWater = records.reduce((sum, r) => sum + (r.waterAmount || 0), 0)
  const totalFertilizer = records.reduce((sum, r) => sum + (r.fertilizerAmount || 0), 0)
  const totalRow = [
    '合计', '', '', '', records.length,
    totalWater.toFixed(2), totalFertilizer.toFixed(2), '', '', '', '', '', '', ''
  ]

  const wsData = [headers, ...data, totalRow]
  const ws = XLSX.utils.aoa_to_sheet(wsData)

  ws['!cols'] = [
    { wch: 20 }, { wch: 15 }, { wch: 20 }, { wch: 20 }, { wch: 15 },
    { wch: 12 }, { wch: 12 }, { wch: 12 }, { wch: 10 }, { wch: 10 },
    { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 20 }
  ]

  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '灌肥台账')
  XLSX.writeFile(wb, fileName)
}

function generateMockRecords(count: number): FertigationRecord[] {
  const zoneNames = zones.value.length > 0
    ? zones.value.map(z => ({ id: z.id, name: z.name }))
    : [
        { id: 'zone-001', name: '1号灌区' },
        { id: 'zone-002', name: '2号灌区' },
        { id: 'zone-003', name: '3号灌区' },
        { id: 'zone-004', name: '4号灌区' }
      ]

  const irrigationTypes = ['drip', 'sprinkler', 'flood']
  const executionModes = ['auto', 'manual']
  const statuses = ['completed', 'running', 'cancelled']
  const fertilizerTypes = ['尿素', '复合肥', '水溶肥', '有机肥']
  const reasons = ['定时灌溉', '湿度低于阈值', '作物生长阶段需求', '追肥']

  const records: FertigationRecord[] = []
  const now = dayjs()

  for (let i = 0; i < count; i++) {
    const zone = zoneNames[Math.floor(Math.random() * zoneNames.length)]
    const startTime = now.subtract(Math.floor(Math.random() * 90), 'day')
      .subtract(Math.floor(Math.random() * 24), 'hour')
    const durationSeconds = 1800 + Math.floor(Math.random() * 5400)
    const isCompleted = Math.random() > 0.1
    const isCancelled = !isCompleted && Math.random() > 0.5

    records.push({
      id: `FRT-${String(i + 1).padStart(6, '0')}`,
      zoneId: zone.id,
      zoneName: zone.name,
      startTime: startTime.format('YYYY-MM-DD HH:mm:ss'),
      endTime: isCompleted ? startTime.add(durationSeconds, 'second').format('YYYY-MM-DD HH:mm:ss') : undefined,
      durationSeconds,
      waterAmount: Number((5 + Math.random() * 20).toFixed(2)),
      fertilizerAmount: Number((0.5 + Math.random() * 3).toFixed(2)),
      fertilizerType: fertilizerTypes[Math.floor(Math.random() * fertilizerTypes.length)],
      averageEc: Number((1.0 + Math.random() * 1.5).toFixed(2)),
      averagePh: Number((5.8 + Math.random() * 1.4).toFixed(2)),
      executionMode: executionModes[Math.floor(Math.random() * executionModes.length)],
      irrigationType: irrigationTypes[Math.floor(Math.random() * irrigationTypes.length)],
      status: isCancelled ? 'cancelled' : (isCompleted ? 'completed' : 'running'),
      reason: reasons[Math.floor(Math.random() * reasons.length)]
    })
  }

  return records.sort((a, b) => new Date(b.startTime).getTime() - new Date(a.startTime).getTime())
}

const tableColumns: TableProps['columns'] = [
  { title: '记录ID', dataIndex: 'id', key: 'id', width: 140, fixed: 'left' },
  { title: '灌区名称', dataIndex: 'zoneName', key: 'zoneName', width: 120 },
  { title: '开始时间', dataIndex: 'startTime', key: 'startTime', width: 180, sorter: true },
  { title: '结束时间', dataIndex: 'endTime', key: 'endTime', width: 180 },
  { title: '持续时长', key: 'duration', width: 120 },
  { title: '用水量(m³)', key: 'waterAmount', width: 120, sorter: true, dataIndex: 'waterAmount' },
  { title: '用肥量(kg)', key: 'fertilizerAmount', width: 120, sorter: true, dataIndex: 'fertilizerAmount' },
  { title: '肥料类型', dataIndex: 'fertilizerType', key: 'fertilizerType', width: 100 },
  { title: '平均EC', key: 'averageEc', width: 100 },
  { title: '平均pH', key: 'averagePh', width: 100 },
  { title: '执行模式', key: 'executionMode', width: 100 },
  { title: '灌溉类型', key: 'irrigationType', width: 100 },
  { title: '状态', key: 'status', width: 100 },
  { title: '操作', key: 'action', width: 80, fixed: 'right' }
]

onMounted(async () => {
  try {
    const zoneData = await zoneApi.getAll()
    zones.value = zoneData
  } catch (e) {
    console.error('Failed to load zones:', e)
    zones.value = [
      { id: 'zone-001', name: '1号灌区', description: '东区', createdAt: '', updatedAt: '' },
      { id: 'zone-002', name: '2号灌区', description: '西区', createdAt: '', updatedAt: '' },
      { id: 'zone-003', name: '3号灌区', description: '南区', createdAt: '', updatedAt: '' },
      { id: 'zone-004', name: '4号灌区', description: '北区', createdAt: '', updatedAt: '' }
    ]
  }

  setQuickTime('week')
  await handleQuery()
})
</script>

<style scoped>
.fertigation-ledger {
  padding: 24px;
  background-color: #f0f2f5;
  min-height: 100%;
}

.filter-actions {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: flex-end;
}

.ledger-stat {
  border: 1px solid #f0f0f0;
}

.stat-change {
  margin-top: 8px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.change-up {
  color: #ff4d4f;
}

.change-down {
  color: #52c41a;
}

.text-right {
  text-align: right;
}

.view-toggle {
  margin-bottom: 16px;
}

.detail-content {
  max-height: 70vh;
  overflow-y: auto;
}

.mini-charts {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.mini-chart-item {
  flex: 1;
}

.mini-chart-title {
  font-size: 14px;
  font-weight: 500;
  color: #262626;
  margin-bottom: 8px;
}

.mini-chart-container {
  height: 150px;
}

.timeline-content {
  display: flex;
  align-items: center;
  gap: 12px;
}

.timeline-time {
  color: #8c8c8c;
  font-size: 13px;
  min-width: 160px;
}

.timeline-valve {
  font-weight: 500;
}

.timeline-operator {
  color: #8c8c8c;
  font-size: 12px;
  margin-left: auto;
}

.export-progress {
  padding: 16px 0;
}

.export-progress-text {
  text-align: center;
  margin-top: 16px;
  color: #595959;
}

.chart-container {
  width: 100%;
  height: 300px;
}

:deep(.ant-collapse-header) {
  font-weight: 500 !important;
}
</style>
