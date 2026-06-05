<template>
  <div class="irrigation-records">
    <div class="card-container">
      <div class="page-header">
        <span class="page-title">灌溉记录</span>
        <a-space>
          <a-range-picker
            v-model:value="dateRange"
            show-time
            format="YYYY-MM-DD HH:mm:ss"
            style="width: 400px"
          />
          <a-select
            v-model:value="zoneFilter"
            placeholder="选择区域"
            style="width: 150px"
            allow-clear
          >
            <a-select-option v-for="zone in zones" :key="zone.id" :value="zone.id">
              {{ zone.name }}
            </a-select-option>
          </a-select>
          <a-button type="primary" @click="exportRecords">
            <DownloadOutlined />
            导出
          </a-button>
        </a-space>
      </div>
      
      <a-table
        :columns="recordColumns"
        :data-source="records"
        :pagination="{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }"
        :loading="loading"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'irrigationType'">
            <a-tag :color="getTypeColor(record.irrigationType)">
              {{ getTypeName(record.irrigationType) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'triggerSource'">
            <a-tag :color="record.triggerSource === 'AUTO' ? 'blue' : 'orange'">
              {{ record.triggerSource === 'AUTO' ? '自动' : '手动' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'durationMinutes'">
            {{ record.durationMinutes }} 分钟
          </template>
          <template v-else-if="column.key === 'waterUsedLiters'">
            {{ record.waterUsedLiters?.toFixed(1) || '--' }} L
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" @click="showDetail(record)">详情</a-button>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal
      v-model:open="detailModalVisible"
      title="灌溉记录详情"
      :footer="null"
      width="700px"
    >
      <a-descriptions :column="2" bordered v-if="selectedRecord">
        <a-descriptions-item label="区域">{{ selectedRecord.zoneName }}</a-descriptions-item>
        <a-descriptions-item label="阀门">
          {{ selectedRecord.valveNames?.join(', ') || '--' }}
        </a-descriptions-item>
        <a-descriptions-item label="灌溉类型">
          <a-tag :color="getTypeColor(selectedRecord.irrigationType)">
            {{ getTypeName(selectedRecord.irrigationType) }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="触发方式">
          <a-tag :color="selectedRecord.triggerSource === 'AUTO' ? 'blue' : 'orange'">
            {{ selectedRecord.triggerSource === 'AUTO' ? '自动' : '手动' }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="开始时间">{{ selectedRecord.startTime }}</a-descriptions-item>
        <a-descriptions-item label="结束时间">{{ selectedRecord.endTime || '进行中' }}</a-descriptions-item>
        <a-descriptions-item label="持续时长">{{ selectedRecord.durationMinutes }} 分钟</a-descriptions-item>
        <a-descriptions-item label="用水量">{{ selectedRecord.waterUsedLiters?.toFixed(1) || '--' }} L</a-descriptions-item>
        <a-descriptions-item label="操作人">{{ selectedRecord.operatorName || '系统' }}</a-descriptions-item>
        <a-descriptions-item label="原因">{{ selectedRecord.reason || '--' }}</a-descriptions-item>
        <a-descriptions-item label="开始时湿度">{{ selectedRecord.startHumidity?.toFixed(1) || '--' }}%</a-descriptions-item>
        <a-descriptions-item label="结束时湿度">{{ selectedRecord.endHumidity?.toFixed(1) || '--' }}%</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useAppStore } from '@/stores'
import { storeToRefs } from 'pinia'
import { irrigationApi } from '@/api'
import type { IrrigationRecord, Zone } from '@/types'
import { DownloadOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'

const appStore = useAppStore()
const { irrigationRecords, zones, loading } = storeToRefs(appStore)

const dateRange = ref<[dayjs.Dayjs, dayjs.Dayjs] | null>(null)
const zoneFilter = ref<string | undefined>()
const detailModalVisible = ref(false)
const selectedRecord = ref<IrrigationRecord | null>(null)

const recordColumns = [
  { title: '区域', dataIndex: 'zoneName', key: 'zoneName' },
  { title: '灌溉类型', key: 'irrigationType', width: 100 },
  { title: '触发方式', key: 'triggerSource', width: 100 },
  { title: '开始时间', dataIndex: 'startTime', key: 'startTime', width: 180 },
  { title: '结束时间', dataIndex: 'endTime', key: 'endTime', width: 180 },
  { title: '持续时长', key: 'durationMinutes', width: 100 },
  { title: '用水量', key: 'waterUsedLiters', width: 100 },
  { title: '操作人', dataIndex: 'operatorName', key: 'operatorName' },
  { title: '操作', key: 'action', width: 80, fixed: 'right' }
]

const records = computed(() => {
  let result = [...irrigationRecords.value]
  if (zoneFilter.value) {
    result = result.filter(r => r.zoneId === zoneFilter.value)
  }
  return result.sort((a, b) => new Date(b.startTime).getTime() - new Date(a.startTime).getTime())
})

function getTypeColor(type: string) {
  const colors: Record<string, string> = {
    NORMAL: 'green',
    FERTIGATION: 'blue',
    FLUSHING: 'cyan',
    EMERGENCY: 'red'
  }
  return colors[type] || 'default'
}

function getTypeName(type: string) {
  const names: Record<string, string> = {
    NORMAL: '正常灌溉',
    FERTIGATION: '肥水灌溉',
    FLUSHING: '管道冲洗',
    EMERGENCY: '紧急灌溉'
  }
  return names[type] || type
}

function showDetail(record: IrrigationRecord) {
  selectedRecord.value = record
  detailModalVisible.value = true
}

async function exportRecords() {
  try {
    message.info('开始导出...')
    await irrigationApi.exportRecords({
      zoneId: zoneFilter.value,
      startTime: dateRange.value?.[0]?.toISOString(),
      endTime: dateRange.value?.[1]?.toISOString()
    })
    message.success('导出成功')
  } catch (e) {
    message.error('导出失败')
  }
}

onMounted(async () => {
  await Promise.all([
    appStore.fetchIrrigationRecords(),
    appStore.fetchZones()
  ])
})
</script>
