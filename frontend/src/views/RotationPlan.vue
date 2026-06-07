<template>
  <div class="rotation-plan">
    <div class="card-container">
      <div class="page-header">
        <span class="page-title">轮灌计划管理</span>
        <a-space>
          <a-button @click="handleGeneratePlan">
            <MagicOutlined />
            智能生成
          </a-button>
          <a-button type="primary" @click="handleAdd">
            <PlusOutlined />
            新增计划
          </a-button>
        </a-space>
      </div>

      <div class="filter-section">
        <a-row :gutter="[16, 16]">
          <a-col :xs="24" :sm="12" :lg="6">
            <div class="filter-label">灌区筛选</div>
            <a-select
              v-model:value="filterZoneId"
              placeholder="全部灌区"
              allow-clear
              style="width: 100%"
            >
              <a-select-option v-for="zone in zones" :key="zone.id" :value="zone.id">
                {{ zone.name }}
              </a-select-option>
            </a-select>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="6">
            <div class="filter-label">状态筛选</div>
            <a-select
              v-model:value="filterStatus"
              placeholder="全部状态"
              allow-clear
              style="width: 100%"
            >
              <a-select-option value="active">已启用</a-select-option>
              <a-select-option value="inactive">已禁用</a-select-option>
            </a-select>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="6">
            <div class="filter-label">灌溉类型</div>
            <a-select
              v-model:value="filterIrrigationType"
              placeholder="全部类型"
              allow-clear
              style="width: 100%"
            >
              <a-select-option value="drip">滴灌</a-select-option>
              <a-select-option value="sprinkler">喷灌</a-select-option>
              <a-select-option value="flood">漫灌</a-select-option>
            </a-select>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="6">
            <div class="filter-label">&nbsp;</div>
            <a-space>
              <a-button type="primary" :loading="loading" @click="fetchRotationPlans">
                <SearchOutlined />
                查询
              </a-button>
              <a-button @click="resetFilters">
                <ReloadOutlined />
                重置
              </a-button>
            </a-space>
          </a-col>
        </a-row>
      </div>
    </div>

    <a-row :gutter="[16, 16]" style="margin-top: 16px">
      <a-col :xs="24" :xl="12">
        <div class="card-container">
          <div class="page-header">
            <span class="page-title">计划列表</span>
            <a-tag color="blue">共 {{ filteredSchedules.length }} 条</a-tag>
          </div>
          <a-table
            :columns="tableColumns"
            :data-source="filteredSchedules"
            :pagination="{ pageSize: 8, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }"
            :loading="loading"
            row-key="id"
            :scroll="{ x: 1200 }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'zoneIds'">
                <a-space wrap>
                  <a-tag v-for="zoneId in record.zoneIds" :key="zoneId" color="blue">
                    {{ getZoneName(zoneId) }}
                  </a-tag>
                </a-space>
              </template>
              <template v-else-if="column.key === 'irrigationType'">
                <a-tag :color="getIrrigationTypeColor(record.irrigationType)">
                  {{ getIrrigationTypeName(record.irrigationType) }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'timeRange'">
                <div>{{ record.startTime }} - {{ record.endTime }}</div>
              </template>
              <template v-else-if="column.key === 'duration'">
                {{ record.duration }} 分钟
              </template>
              <template v-else-if="column.key === 'intervalHours'">
                {{ record.intervalHours }} 小时
              </template>
              <template v-else-if="column.key === 'waterAmount'">
                {{ record.waterAmount }} m³
              </template>
              <template v-else-if="column.key === 'fertilizerAmount'">
                {{ record.fertilizerAmount }} kg
              </template>
              <template v-else-if="column.key === 'isActive'">
                <a-switch
                  :checked="record.isActive"
                  :loading="loadingMap[record.id]"
                  @change="(checked) => handleToggleActive(record, checked)"
                />
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space>
                  <a-button type="link" size="small" @click="handleEdit(record)">
                    编辑
                  </a-button>
                  <a-button type="link" size="small" @click="handleExecuteNow(record)">
                    立即执行
                  </a-button>
                  <a-popconfirm
                    title="确认删除此计划？"
                    @confirm="handleDelete(record.id)"
                  >
                    <a-button type="link" danger size="small">删除</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </a-table>
        </div>
      </a-col>

      <a-col :xs="24" :xl="12">
        <div class="card-container">
          <div class="page-header">
            <span class="page-title">甘特图视图</span>
            <a-space>
              <a-radio-group v-model:value="ganttViewMode" size="small">
                <a-radio-button value="time">按时间</a-radio-button>
                <a-radio-button value="zone">按灌区</a-radio-button>
              </a-radio-group>
              <a-button size="small" @click="refreshGantt">
                <ReloadOutlined />
                刷新
              </a-button>
            </a-space>
          </div>
          <div class="gantt-chart-container">
            <v-chart :option="ganttOption" autoresize theme="light" />
          </div>
          <div class="gantt-legend">
            <a-space wrap>
              <div v-for="item in legendItems" :key="item.type" class="legend-item">
                <span class="legend-color" :style="{ backgroundColor: item.color }"></span>
                <span class="legend-text">{{ item.name }}</span>
              </div>
            </a-space>
          </div>
        </div>
      </a-col>
    </a-row>

    <a-modal
      v-model:open="modalVisible"
      :title="isEdit ? '编辑轮灌计划' : '新增轮灌计划'"
      @ok="handleSubmit"
      @cancel="handleModalCancel"
      :confirm-loading="submitting"
      width="800px"
      :mask-closable="false"
    >
      <a-form
        ref="formRef"
        :model="planForm"
        layout="vertical"
        :label-col="{ style: { width: '120px' } }"
      >
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item
              label="计划名称"
              name="name"
              :rules="[{ required: true, message: '请输入计划名称' }]"
            >
              <a-input v-model:value="planForm.name" placeholder="请输入计划名称" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item
              label="关联阈值策略"
              name="strategyId"
            >
              <a-select
                v-model:value="planForm.strategyId"
                placeholder="请选择阈值策略"
                allow-clear
                style="width: 100%"
              >
                <a-select-option v-for="strategy in activeStrategies" :key="strategy.id" :value="strategy.id">
                  {{ strategy.name }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="计划描述" name="description">
          <a-textarea v-model:value="planForm.description" :rows="2" placeholder="请输入计划描述" />
        </a-form-item>

        <a-form-item
          label="关联灌区"
          name="zoneIds"
          :rules="[{ required: true, message: '请选择至少一个灌区' }]"
        >
          <a-select
            v-model:value="planForm.zoneIds"
            mode="multiple"
            placeholder="请选择灌区"
            style="width: 100%"
            :max-tag-count="5"
          >
            <a-select-option v-for="zone in zones" :key="zone.id" :value="zone.id">
              {{ zone.name }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item
              label="开始时间"
              name="startTime"
              :rules="[{ required: true, message: '请选择开始时间' }]"
            >
              <a-time-picker
                v-model:value="startTimeValue"
                format="HH:mm"
                style="width: 100%"
                placeholder="选择开始时间"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item
              label="结束时间"
              name="endTime"
              :rules="[{ required: true, message: '请选择结束时间' }]"
            >
              <a-time-picker
                v-model:value="endTimeValue"
                format="HH:mm"
                style="width: 100%"
                placeholder="选择结束时间"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item
              label="持续时长(分钟)"
              name="duration"
              :rules="[{ required: true, message: '请输入持续时长' }]"
            >
              <a-input-number
                v-model:value="planForm.duration"
                :min="1"
                :max="1440"
                style="width: 100%"
                placeholder="分钟"
              />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item
              label="间隔小时数"
              name="intervalHours"
              :rules="[{ required: true, message: '请输入间隔小时数' }]"
            >
              <a-input-number
                v-model:value="planForm.intervalHours"
                :min="0"
                :max="168"
                style="width: 100%"
                placeholder="小时"
              />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item
              label="优先级"
              name="priority"
              :rules="[{ required: true, message: '请输入优先级' }]"
            >
              <a-input-number
                v-model:value="planForm.priority"
                :min="1"
                :max="10"
                style="width: 100%"
                placeholder="1-10，数字越大优先级越高"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item
              label="灌溉用水量(m³)"
              name="waterAmount"
              :rules="[{ required: true, message: '请输入灌溉用水量' }]"
            >
              <a-input-number
                v-model:value="planForm.waterAmount"
                :min="0"
                :step="0.1"
                style="width: 100%"
                placeholder="立方米"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item
              label="施肥量(kg)"
              name="fertilizerAmount"
              :rules="[{ required: true, message: '请输入施肥量' }]"
            >
              <a-input-number
                v-model:value="planForm.fertilizerAmount"
                :min="0"
                :step="0.1"
                style="width: 100%"
                placeholder="千克"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item
              label="灌溉类型"
              name="irrigationType"
              :rules="[{ required: true, message: '请选择灌溉类型' }]"
            >
              <a-select v-model:value="planForm.irrigationType" style="width: 100%">
                <a-select-option value="drip">滴灌</a-select-option>
                <a-select-option value="sprinkler">喷灌</a-select-option>
                <a-select-option value="flood">漫灌</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="启用状态" name="isActive">
              <a-space>
                <a-switch v-model:checked="planForm.isActive" checked-children="启用" un-checked-children="禁用" />
                <span v-if="planForm.isActive" style="color: #52c41a">计划已启用</span>
                <span v-else style="color: #8c8c8c">计划已禁用</span>
              </a-space>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="generateModalVisible"
      title="智能生成轮灌计划"
      @ok="handleGenerateConfirm"
      @cancel="generateModalVisible = false"
      :confirm-loading="generating"
      width="500px"
    >
      <a-form layout="vertical">
        <a-form-item label="选择灌区" :rules="[{ required: true, message: '请选择灌区' }]">
          <a-select v-model:value="generateZoneId" placeholder="请选择要生成计划的灌区" style="width: 100%">
            <a-select-option v-for="zone in zones" :key="zone.id" :value="zone.id">
              {{ zone.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="灌溉类型">
          <a-select v-model:value="generateIrrigationType" style="width: 100%">
            <a-select-option value="drip">滴灌</a-select-option>
            <a-select-option value="sprinkler">喷灌</a-select-option>
            <a-select-option value="flood">漫灌</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="优先级">
          <a-input-number v-model:value="generatePriority" :min="1" :max="10" style="width: 100%" />
        </a-form-item>
        <a-alert
          type="info"
          show-icon
          message="智能生成说明"
          description="系统将根据灌区的作物需水量、土壤墒情和历史灌溉数据，自动生成最优的轮灌计划。"
        />
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, shallowRef } from 'vue'
import { message, Modal } from 'ant-design-vue'
import type { EChartsOption } from 'echarts'
import dayjs from 'dayjs'
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  MagicOutlined
} from '@ant-design/icons-vue'
import { rotationApi, thresholdApi, zoneApi } from '@/api'
import type { RotationSchedule, ThresholdStrategy, Zone, GanttTask } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const generating = ref(false)
const loadingMap = reactive<Record<string, boolean>>({})

const rotationSchedules = ref<RotationSchedule[]>([])
const zones = ref<Zone[]>([])
const activeStrategies = ref<ThresholdStrategy[]>([])

const filterZoneId = ref<string>('')
const filterStatus = ref<string>('')
const filterIrrigationType = ref<string>('')

const modalVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const startTimeValue = ref<dayjs.Dayjs | null>(null)
const endTimeValue = ref<dayjs.Dayjs | null>(null)

const generateModalVisible = ref(false)
const generateZoneId = ref<string>('')
const generateIrrigationType = ref('drip')
const generatePriority = ref(5)

const ganttViewMode = ref('time')

const planForm = reactive<Partial<RotationSchedule>>({
  id: '',
  name: '',
  description: '',
  strategyId: '',
  zoneIds: [],
  startTime: '08:00',
  endTime: '18:00',
  duration: 30,
  intervalHours: 4,
  priority: 5,
  waterAmount: 10,
  fertilizerAmount: 5,
  irrigationType: 'drip',
  isActive: true
})

const tableColumns = [
  { title: '计划名称', dataIndex: 'name', key: 'name', width: 140, fixed: 'left' },
  { title: '关联灌区', key: 'zoneIds', width: 180 },
  { title: '灌溉类型', key: 'irrigationType', width: 100 },
  { title: '时间范围', key: 'timeRange', width: 160 },
  { title: '持续时长', key: 'duration', width: 100 },
  { title: '间隔小时', key: 'intervalHours', width: 100 },
  { title: '水量(m³)', key: 'waterAmount', width: 100 },
  { title: '肥量(kg)', key: 'fertilizerAmount', width: 100 },
  { title: '状态', key: 'isActive', width: 80 },
  { title: '操作', key: 'action', width: 180, fixed: 'right' }
]

const irrigationTypeColors: Record<string, string> = {
  drip: '#1890ff',
  sprinkler: '#52c41a',
  flood: '#faad14'
}

const irrigationTypeNames: Record<string, string> = {
  drip: '滴灌',
  sprinkler: '喷灌',
  flood: '漫灌'
}

const legendItems = [
  { type: 'drip', name: '滴灌', color: irrigationTypeColors.drip },
  { type: 'sprinkler', name: '喷灌', color: irrigationTypeColors.sprinkler },
  { type: 'flood', name: '漫灌', color: irrigationTypeColors.flood }
]

const filteredSchedules = computed(() => {
  return rotationSchedules.value.filter(schedule => {
    if (filterZoneId.value && !schedule.zoneIds.includes(filterZoneId.value)) {
      return false
    }
    if (filterStatus.value) {
      const isActive = filterStatus.value === 'active'
      if (schedule.isActive !== isActive) return false
    }
    if (filterIrrigationType.value && schedule.irrigationType !== filterIrrigationType.value) {
      return false
    }
    return true
  })
})

const ganttOption = computed<EChartsOption>(() => {
  const schedules = filteredSchedules.value
  const zoneList = zones.value

  if (schedules.length === 0 || zoneList.length === 0) {
    return {
      title: {
        text: '暂无计划数据',
        left: 'center',
        top: 'center',
        textStyle: { color: '#8c8c8c', fontSize: 16, fontWeight: 'normal' }
      },
      tooltip: { trigger: 'axis' },
      grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
      xAxis: { type: 'category', data: [] },
      yAxis: { type: 'value' },
      series: []
    }
  }

  if (ganttViewMode.value === 'zone') {
    return buildGanttByZone(schedules, zoneList)
  } else {
    return buildGanttByTime(schedules, zoneList)
  }
})

function buildGanttByZone(schedules: RotationSchedule[], zoneList: Zone[]): EChartsOption {
  const yAxisData = zoneList.map(z => z.name)
  const hours = Array.from({ length: 25 }, (_, i) => `${String(i).padStart(2, '0')}:00`)

  const series: any[] = []

  zoneList.forEach((zone, zoneIndex) => {
    const zoneSchedules = schedules.filter(s => s.zoneIds.includes(zone.id))
    const data: any[] = []

    zoneSchedules.forEach(schedule => {
      const startMinutes = timeToMinutes(schedule.startTime)
      const endMinutes = timeToMinutes(schedule.endTime)
      const duration = endMinutes - startMinutes

      if (duration > 0) {
        data.push({
          value: [
            zoneIndex,
            startMinutes,
            startMinutes + duration,
            schedule.name,
            schedule.irrigationType
          ],
          itemStyle: {
            color: irrigationTypeColors[schedule.irrigationType] || '#1890ff'
          }
        })
      }
    })

    series.push({
      type: 'custom',
      name: zone.name,
      renderItem: renderGanttBar,
      data: data,
      encode: {
        x: [1, 2],
        y: 0
      }
    })
  })

  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        if (!Array.isArray(params) || params.length === 0) return ''
        const param = params[0]
        if (!param.data) return ''
        const [zoneIndex, start, end, name, type] = param.data.value
        const zoneName = yAxisData[zoneIndex]
        return `
          <div style="font-weight: 500; margin-bottom: 8px">${name}</div>
          <div>灌区: ${zoneName}</div>
          <div>类型: ${irrigationTypeNames[type] || type}</div>
          <div>时间: ${minutesToTime(start)} - ${minutesToTime(end)}</div>
          <div>时长: ${Math.round((end - start) / 60)} 分钟</div>
        `
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '10%',
      top: '8%',
      containLabel: true
    },
    xAxis: {
      type: 'value',
      min: 0,
      max: 24 * 60,
      interval: 60,
      axisLabel: {
        formatter: (value: number) => minutesToTime(value)
      },
      axisLine: { lineStyle: { color: '#e8e8e8' } },
      splitLine: { lineStyle: { type: 'dashed', color: '#f0f0f0' } }
    },
    yAxis: {
      type: 'category',
      data: yAxisData,
      axisLine: { lineStyle: { color: '#e8e8e8' } },
      splitLine: { show: false }
    },
    series
  }
}

function buildGanttByTime(schedules: RotationSchedule[], zoneList: Zone[]): EChartsOption {
  const hours = Array.from({ length: 24 }, (_, i) => `${String(i).padStart(2, '0')}:00`)
  const seriesData: any[] = []

  zoneList.forEach((zone, zoneIndex) => {
    const zoneSchedules = schedules.filter(s => s.zoneIds.includes(zone.id))
    const data: number[] = Array(24).fill(0)

    zoneSchedules.forEach(schedule => {
      const startHour = parseInt(schedule.startTime.split(':')[0])
      const endHour = parseInt(schedule.endTime.split(':')[0])
      for (let h = startHour; h < endHour && h < 24; h++) {
        data[h] = zoneIndex + 1
      }
    })

    seriesData.push({
      name: zone.name,
      type: 'bar',
      stack: 'total',
      data: data.map((v, i) => ({
        value: v > 0 ? 1 : 0,
        itemStyle: {
          color: v > 0 ? getZoneColor(zoneIndex) : 'transparent'
        }
      })),
      barWidth: '80%'
    })
  })

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        if (!Array.isArray(params)) return ''
        const hour = params[0]?.axisValue
        const activeZones = params
          .filter((p: any) => p.data?.value > 0)
          .map((p: any) => p.seriesName)
        return `
          <div style="font-weight: 500; margin-bottom: 8px">${hour}:00 - ${hour}:59</div>
          <div>灌溉灌区: ${activeZones.length > 0 ? activeZones.join('、') : '无'}</div>
        `
      }
    },
    legend: {
      data: zoneList.map(z => z.name),
      top: 0,
      type: 'scroll'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '10%',
      top: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: hours,
      axisLine: { lineStyle: { color: '#e8e8e8' } },
      axisLabel: { interval: 2 }
    },
    yAxis: {
      type: 'value',
      show: false,
      max: zoneList.length
    },
    series: seriesData
  }
}

function renderGanttBar(params: any, api: any) {
  const categoryIndex = api.value(0)
  const start = api.coord([api.value(1), categoryIndex])
  const end = api.coord([api.value(2), categoryIndex])
  const height = api.size([0, 1])[1] * 0.6

  const rectShape = {
    x: start[0],
    y: start[1] - height / 2,
    width: Math.max(end[0] - start[0], 5),
    height: height
  }

  return {
    type: 'rect',
    shape: rectShape,
    style: {
      fill: api.style().fill,
      opacity: 0.8,
      borderRadius: 4
    },
    emphasis: {
      style: {
        opacity: 1,
        shadowBlur: 10,
        shadowColor: 'rgba(0, 0, 0, 0.3)'
      }
    }
  }
}

function timeToMinutes(timeStr: string): number {
  const [hours, minutes] = timeStr.split(':').map(Number)
  return hours * 60 + minutes
}

function minutesToTime(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = Math.round(minutes % 60)
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

function getZoneColor(index: number): string {
  const colors = [
    '#1890ff', '#52c41a', '#faad14', '#f5222d', '#722ed1',
    '#13c2c2', '#eb2f96', '#fa8c16', '#a0d911', '#2f54eb'
  ]
  return colors[index % colors.length]
}

function getZoneName(zoneId: string): string {
  const zone = zones.value.find(z => z.id === zoneId)
  return zone?.name || zoneId
}

function getIrrigationTypeColor(type: string): string {
  return irrigationTypeColors[type] || 'default'
}

function getIrrigationTypeName(type: string): string {
  return irrigationTypeNames[type] || type
}

async function fetchZones() {
  try {
    const data = await zoneApi.getAll()
    zones.value = data
  } catch (e) {
    console.error('Failed to load zones:', e)
    zones.value = [
      { id: 'zone-001', name: '1号灌区', description: '东区', createdAt: '', updatedAt: '' },
      { id: 'zone-002', name: '2号灌区', description: '西区', createdAt: '', updatedAt: '' },
      { id: 'zone-003', name: '3号灌区', description: '南区', createdAt: '', updatedAt: '' },
      { id: 'zone-004', name: '4号灌区', description: '北区', createdAt: '', updatedAt: '' }
    ]
  }
}

async function fetchActiveStrategies() {
  try {
    const data = await thresholdApi.getActive()
    activeStrategies.value = data
  } catch (e) {
    console.error('Failed to load active strategies:', e)
    activeStrategies.value = [
      { id: 'strat-001', name: '番茄生长期策略', minHumidity: 60, maxHumidity: 80, minEc: 1.5, maxEc: 2.5, minPh: 5.5, maxPh: 7.0, minTemperature: 15, maxTemperature: 35, maxWindSpeed: 10, minRainfall: 5, weatherLinkEnabled: true, avoidRainIrrigation: true, highTempIrrigation: false, isActive: true, priority: 1, createdAt: '', updatedAt: '' },
      { id: 'strat-002', name: '黄瓜生长期策略', minHumidity: 70, maxHumidity: 90, minEc: 1.8, maxEc: 2.8, minPh: 5.8, maxPh: 7.2, minTemperature: 18, maxTemperature: 32, maxWindSpeed: 8, minRainfall: 3, weatherLinkEnabled: true, avoidRainIrrigation: true, highTempIrrigation: true, isActive: true, priority: 2, createdAt: '', updatedAt: '' }
    ]
  }
}

function generateMockSchedules(): RotationSchedule[] {
  const mockZones = zones.value.length > 0 ? zones.value : [
    { id: 'zone-001', name: '1号灌区', createdAt: '', updatedAt: '' },
    { id: 'zone-002', name: '2号灌区', createdAt: '', updatedAt: '' },
    { id: 'zone-003', name: '3号灌区', createdAt: '', updatedAt: '' },
    { id: 'zone-004', name: '4号灌区', createdAt: '', updatedAt: '' }
  ]

  const types = ['drip', 'sprinkler', 'flood']
  const names = ['早间轮灌计划', '午间补水计划', '晚间追肥计划', '凌晨灌溉计划', '应急灌溉计划']

  return names.map((name, index) => ({
    id: `plan-${String(index + 1).padStart(3, '0')}`,
    name,
    description: `自动生成的${name}`,
    strategyId: `strat-00${(index % 2) + 1}`,
    zoneIds: mockZones.slice(0, (index % 3) + 1).map(z => z.id),
    startTime: `${String((index * 4) % 24).padStart(2, '0')}:00`,
    endTime: `${String(((index + 1) * 4) % 24).padStart(2, '0')}:00`,
    duration: 30 + index * 10,
    intervalHours: 4 + index,
    priority: 10 - index,
    waterAmount: 8 + index * 2,
    fertilizerAmount: 3 + index,
    irrigationType: types[index % 3],
    isActive: index % 2 === 0,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  }))
}

async function fetchRotationPlans() {
  loading.value = true
  try {
    let data: RotationSchedule[]
    if (filterZoneId.value) {
      data = await rotationApi.getByZone(filterZoneId.value)
    } else {
      data = await rotationApi.getAll()
    }
    rotationSchedules.value = data
  } catch (e) {
    console.error('Failed to load rotation plans:', e)
    rotationSchedules.value = generateMockSchedules()
    message.warning('加载计划失败，已使用模拟数据')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filterZoneId.value = ''
  filterStatus.value = ''
  filterIrrigationType.value = ''
  fetchRotationPlans()
}

function handleAdd() {
  isEdit.value = false
  resetPlanForm()
  modalVisible.value = true
}

function handleEdit(record: RotationSchedule) {
  isEdit.value = true
  Object.assign(planForm, {
    id: record.id,
    name: record.name,
    description: record.description || '',
    strategyId: record.strategyId || '',
    zoneIds: [...record.zoneIds],
    startTime: record.startTime,
    endTime: record.endTime,
    duration: record.duration,
    intervalHours: record.intervalHours,
    priority: record.priority,
    waterAmount: record.waterAmount,
    fertilizerAmount: record.fertilizerAmount,
    irrigationType: record.irrigationType,
    isActive: record.isActive
  })
  startTimeValue.value = dayjs(record.startTime, 'HH:mm')
  endTimeValue.value = dayjs(record.endTime, 'HH:mm')
  modalVisible.value = true
}

function resetPlanForm() {
  Object.assign(planForm, {
    id: '',
    name: '',
    description: '',
    strategyId: '',
    zoneIds: [],
    startTime: '08:00',
    endTime: '18:00',
    duration: 30,
    intervalHours: 4,
    priority: 5,
    waterAmount: 10,
    fertilizerAmount: 5,
    irrigationType: 'drip',
    isActive: true
  })
  startTimeValue.value = dayjs('08:00', 'HH:mm')
  endTimeValue.value = dayjs('18:00', 'HH:mm')
  formRef.value?.clearValidate?.()
}

async function handleSubmit() {
  try {
    await formRef.value?.validate?.()
  } catch (e) {
    message.warning('请填写完整的表单信息')
    return
  }

  if (startTimeValue.value && endTimeValue.value) {
    const start = startTimeValue.value.format('HH:mm')
    const end = endTimeValue.value.format('HH:mm')
    if (timeToMinutes(end) <= timeToMinutes(start)) {
      message.error('结束时间必须大于开始时间')
      return
    }
    planForm.startTime = start
    planForm.endTime = end
  }

  submitting.value = true
  try {
    if (isEdit.value && planForm.id) {
      await rotationApi.update(planForm.id, planForm)
      message.success('计划更新成功')
    } else {
      await rotationApi.create(planForm)
      message.success('计划创建成功')
    }
    modalVisible.value = false
    await fetchRotationPlans()
  } catch (e) {
    console.error('Submit failed:', e)
    message.error(isEdit.value ? '更新计划失败' : '创建计划失败')
  } finally {
    submitting.value = false
  }
}

function handleModalCancel() {
  modalVisible.value = false
}

async function handleToggleActive(record: RotationSchedule, active: boolean) {
  loadingMap[record.id] = true
  try {
    await rotationApi.setActive(record.id, active)
    message.success(`计划已${active ? '启用' : '禁用'}`)
    record.isActive = active
  } catch (e) {
    console.error('Toggle active failed:', e)
    message.error('操作失败')
  } finally {
    loadingMap[record.id] = false
  }
}

async function handleExecuteNow(record: RotationSchedule) {
  Modal.confirm({
    title: '确认立即执行',
    content: `确定要立即执行计划【${record.name}】吗？`,
    onOk: async () => {
      try {
        await rotationApi.executeNow(record.id)
        message.success('计划已开始执行')
      } catch (e) {
        console.error('Execute now failed:', e)
        message.error('执行失败')
      }
    }
  })
}

async function handleDelete(id: string) {
  try {
    await rotationApi.delete(id)
    message.success('删除成功')
    await fetchRotationPlans()
  } catch (e) {
    console.error('Delete failed:', e)
    message.error('删除失败')
  }
}

function handleGeneratePlan() {
  if (zones.value.length > 0) {
    generateZoneId.value = zones.value[0].id
  }
  generateModalVisible.value = true
}

async function handleGenerateConfirm() {
  if (!generateZoneId.value) {
    message.warning('请选择灌区')
    return
  }

  generating.value = true
  try {
    const data = await rotationApi.generatePlan(
      generateZoneId.value,
      generateIrrigationType.value,
      generatePriority.value
    )
    message.success(`成功生成 ${data.length} 个轮灌计划`)
    generateModalVisible.value = false
    await fetchRotationPlans()
  } catch (e) {
    console.error('Generate plan failed:', e)
    const mockPlans = generateMockSchedules()
    rotationSchedules.value = [...rotationSchedules.value, ...mockPlans.slice(0, 2)]
    message.warning('生成失败，已添加模拟计划用于演示')
    generateModalVisible.value = false
  } finally {
    generating.value = false
  }
}

function refreshGantt() {
  fetchRotationPlans()
}

onMounted(async () => {
  startTimeValue.value = dayjs('08:00', 'HH:mm')
  endTimeValue.value = dayjs('18:00', 'HH:mm')

  await Promise.all([
    fetchZones(),
    fetchActiveStrategies()
  ])
  await fetchRotationPlans()
})
</script>

<style scoped>
.rotation-plan {
  padding: 24px;
  background-color: #f0f2f5;
  min-height: 100%;
}

.filter-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.filter-label {
  font-size: 13px;
  color: #595959;
  margin-bottom: 8px;
  font-weight: 500;
}

.gantt-chart-container {
  width: 100%;
  height: 400px;
  margin-top: 16px;
}

.gantt-legend {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend-color {
  display: inline-block;
  width: 16px;
  height: 16px;
  border-radius: 4px;
}

.legend-text {
  font-size: 13px;
  color: #595959;
}
</style>
