<template>
  <div class="operation-logs">
    <div class="statistics-section">
      <a-row :gutter="16">
        <a-col :span="6">
          <a-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon blue">
                <CalendarOutlined />
              </div>
              <div class="stat-info">
                <div class="stat-label">今日操作数</div>
                <div class="stat-value">{{ statistics.todayCount || 0 }}</div>
              </div>
            </div>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon green">
                <BarChartOutlined />
              </div>
              <div class="stat-info">
                <div class="stat-label">本周操作数</div>
                <div class="stat-value">{{ statistics.weekCount || 0 }}</div>
              </div>
            </div>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon purple">
                <CalendarOutlined />
              </div>
              <div class="stat-info">
                <div class="stat-label">本月操作数</div>
                <div class="stat-value">{{ statistics.monthCount || 0 }}</div>
              </div>
            </div>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon orange">
                <CheckCircleOutlined />
              </div>
              <div class="stat-info">
                <div class="stat-label">操作成功率</div>
                <div class="stat-value">{{ successRate }}%</div>
              </div>
            </div>
          </a-card>
        </a-col>
      </a-row>
    </div>

    <div class="card-container">
      <div class="page-header">
        <span class="page-title">操作日志</span>
        <a-space>
          <a-input
            v-model:value="filterParams.username"
            placeholder="用户名"
            style="width: 140px"
            allow-clear
          />
          <a-select
            v-model:value="filterParams.operationType"
            placeholder="操作类型"
            style="width: 140px"
            allow-clear
          >
            <a-select-option value="CREATE">创建</a-select-option>
            <a-select-option value="UPDATE">更新</a-select-option>
            <a-select-option value="DELETE">删除</a-select-option>
            <a-select-option value="CONTROL">控制</a-select-option>
            <a-select-option value="CONFIRM">确认</a-select-option>
            <a-select-option value="EXPORT">导出</a-select-option>
            <a-select-option value="LOGIN">登录</a-select-option>
          </a-select>
          <a-select
            v-model:value="filterParams.targetType"
            placeholder="目标类型"
            style="width: 140px"
            allow-clear
          >
            <a-select-option value="DEVICE">设备</a-select-option>
            <a-select-option value="ZONE">区域</a-select-option>
            <a-select-option value="CROP">作物</a-select-option>
            <a-select-option value="VALVE">阀门</a-select-option>
            <a-select-option value="USER">用户</a-select-option>
            <a-select-option value="ROLE">角色</a-select-option>
            <a-select-option value="IRRIGATION_PLAN">灌溉计划</a-select-option>
            <a-select-option value="THRESHOLD">阈值策略</a-select-option>
            <a-select-option value="ROTATION">轮灌计划</a-select-option>
          </a-select>
          <a-select
            v-model:value="filterParams.success"
            placeholder="状态"
            style="width: 120px"
            allow-clear
          >
            <a-select-option :value="true">成功</a-select-option>
            <a-select-option :value="false">失败</a-select-option>
          </a-select>
          <a-range-picker
            v-model:value="dateRange"
            style="width: 280px"
            :show-time="{ defaultValue: [dayjs('00:00:00', 'HH:mm:ss'), dayjs('23:59:59', 'HH:mm:ss')] }"
          />
          <a-button type="primary" @click="fetchLogs">
            <SearchOutlined />
            查询
          </a-button>
          <a-button @click="resetFilters">
            <ReloadOutlined />
            重置
          </a-button>
        </a-space>
      </div>

      <a-table
        :columns="logColumns"
        :data-source="logs"
        :pagination="pagination"
        :loading="loading"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'operationType'">
            <a-tag :color="getOperationTypeColor(record.operationType)">
              {{ record.operationTypeDesc || getOperationTypeName(record.operationType) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'success'">
            <a-badge
              :status="record.success ? 'success' : 'error'"
              :text="record.success ? '成功' : '失败'"
            />
          </template>
          <template v-else-if="column.key === 'executionTime'">
            <span>{{ record.executionTime !== undefined ? record.executionTime + ' ms' : '--' }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" @click="viewDetail(record)">
              查看详情
            </a-button>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal
      v-model:open="detailModalVisible"
      title="操作日志详情"
      :footer="null"
      width="700px"
    >
      <a-descriptions :column="2" bordered v-if="selectedLog">
        <a-descriptions-item label="操作时间" :span="2">
          {{ selectedLog.createdAt }}
        </a-descriptions-item>
        <a-descriptions-item label="用户名">
          {{ selectedLog.username }}
        </a-descriptions-item>
        <a-descriptions-item label="真实姓名">
          {{ selectedLog.realName }}
        </a-descriptions-item>
        <a-descriptions-item label="操作">
          {{ selectedLog.operation }}
        </a-descriptions-item>
        <a-descriptions-item label="操作类型">
          <a-tag :color="getOperationTypeColor(selectedLog.operationType)">
            {{ selectedLog.operationTypeDesc || getOperationTypeName(selectedLog.operationType) }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="目标类型">
          {{ selectedLog.targetType || '--' }}
        </a-descriptions-item>
        <a-descriptions-item label="目标ID">
          {{ selectedLog.targetId || '--' }}
        </a-descriptions-item>
        <a-descriptions-item label="IP地址">
          {{ selectedLog.ipAddress || '--' }}
        </a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-badge
            :status="selectedLog.success ? 'success' : 'error'"
            :text="selectedLog.success ? '成功' : '失败'"
          />
        </a-descriptions-item>
        <a-descriptions-item label="执行时间">
          {{ selectedLog.executionTime !== undefined ? selectedLog.executionTime + ' ms' : '--' }}
        </a-descriptions-item>
        <a-descriptions-item label="描述" :span="2">
          {{ selectedLog.description || '--' }}
        </a-descriptions-item>
        <a-descriptions-item label="错误信息" :span="2" v-if="!selectedLog.success && selectedLog.errorMessage">
          <span style="color: #ff4d4f">{{ selectedLog.errorMessage }}</span>
        </a-descriptions-item>
        <a-descriptions-item label="旧值" :span="2" v-if="selectedLog.oldValue">
          <pre class="json-display">{{ formatJson(selectedLog.oldValue) }}</pre>
        </a-descriptions-item>
        <a-descriptions-item label="新值" :span="2" v-if="selectedLog.newValue">
          <pre class="json-display">{{ formatJson(selectedLog.newValue) }}</pre>
        </a-descriptions-item>
        <a-descriptions-item label="请求方法" v-if="selectedLog.requestMethod">
          {{ selectedLog.requestMethod }}
        </a-descriptions-item>
        <a-descriptions-item label="请求URI" v-if="selectedLog.requestUri">
          {{ selectedLog.requestUri }}
        </a-descriptions-item>
        <a-descriptions-item label="User-Agent" :span="2" v-if="selectedLog.userAgent">
          {{ selectedLog.userAgent }}
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { operationLogApi } from '@/api'
import type { OperationLog } from '@/types'
import dayjs from 'dayjs'
import {
  SearchOutlined,
  ReloadOutlined,
  CalendarOutlined,
  BarChartOutlined,
  CheckCircleOutlined
} from '@ant-design/icons-vue'

const loading = ref(false)
const logs = ref<OperationLog[]>([])
const statistics = ref<any>({})
const detailModalVisible = ref(false)
const selectedLog = ref<OperationLog | null>(null)
const dateRange = ref<[dayjs.Dayjs, dayjs.Dayjs] | null>(null)

const filterParams = reactive({
  username: '' as string | undefined,
  operationType: '' as string | undefined,
  targetType: '' as string | undefined,
  success: undefined as boolean | undefined,
  startTime: '' as string | undefined,
  endTime: '' as string | undefined
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`
})

const logColumns = [
  { title: '操作时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '用户名', dataIndex: 'username', key: 'username', width: 120 },
  { title: '真实姓名', dataIndex: 'realName', key: 'realName', width: 120 },
  { title: '操作', dataIndex: 'operation', key: 'operation', ellipsis: true },
  { title: '操作类型', key: 'operationType', width: 100 },
  { title: '目标类型', dataIndex: 'targetType', key: 'targetType', width: 120 },
  { title: '目标ID', dataIndex: 'targetId', key: 'targetId', width: 120 },
  { title: 'IP地址', dataIndex: 'ipAddress', key: 'ipAddress', width: 130 },
  { title: '状态', key: 'success', width: 100 },
  { title: '执行时间', key: 'executionTime', width: 100 },
  { title: '操作', key: 'action', width: 100, fixed: 'right' }
]

const successRate = computed(() => {
  const total = statistics.value.todayCount || 0
  if (total === 0) return 0
  const success = statistics.value.todaySuccessCount || 0
  return Math.round((success / total) * 100)
})

function getOperationTypeColor(type: string): string {
  const colors: Record<string, string> = {
    CREATE: 'green',
    UPDATE: 'blue',
    DELETE: 'red',
    CONTROL: 'orange',
    CONFIRM: 'purple',
    EXPORT: 'cyan',
    LOGIN: 'default'
  }
  return colors[type] || 'default'
}

function getOperationTypeName(type: string): string {
  const names: Record<string, string> = {
    CREATE: '创建',
    UPDATE: '更新',
    DELETE: '删除',
    CONTROL: '控制',
    CONFIRM: '确认',
    EXPORT: '导出',
    LOGIN: '登录'
  }
  return names[type] || type
}

function formatJson(jsonStr: string): string {
  try {
    return JSON.stringify(JSON.parse(jsonStr), null, 2)
  } catch (e) {
    return jsonStr
  }
}

async function fetchStatistics() {
  try {
    const data = await operationLogApi.getStatistics()
    statistics.value = data || {}
  } catch (e) {
    console.error('获取统计数据失败:', e)
  }
}

async function fetchLogs() {
  try {
    loading.value = true
    
    let startTime: string | undefined
    let endTime: string | undefined
    
    if (dateRange.value && dateRange.value.length === 2) {
      startTime = dateRange.value[0].format('YYYY-MM-DD HH:mm:ss')
      endTime = dateRange.value[1].format('YYYY-MM-DD HH:mm:ss')
    }
    
    const params = {
      username: filterParams.username || undefined,
      operationType: filterParams.operationType || undefined,
      targetType: filterParams.targetType || undefined,
      success: filterParams.success,
      startTime,
      endTime,
      page: pagination.current - 1,
      size: pagination.pageSize
    }
    
    const response = await operationLogApi.getLogs(params)
    logs.value = response.content || []
    pagination.total = response.total || 0
  } catch (e) {
    message.error('获取操作日志失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filterParams.username = ''
  filterParams.operationType = ''
  filterParams.targetType = ''
  filterParams.success = undefined
  dateRange.value = null
  pagination.current = 1
  fetchLogs()
}

function handleTableChange(pager: any) {
  pagination.current = pager.current
  pagination.pageSize = pager.pageSize
  fetchLogs()
}

function viewDetail(log: OperationLog) {
  selectedLog.value = log
  detailModalVisible.value = true
}

onMounted(async () => {
  await Promise.all([
    fetchStatistics(),
    fetchLogs()
  ])
})
</script>

<style scoped>
.operation-logs {
  padding: 20px;
}

.statistics-section {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 8px;
}

.stat-content {
  display: flex;
  align-items: center;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #fff;
  margin-right: 16px;
}

.stat-icon.blue {
  background: linear-gradient(135deg, #1890ff 0%, #096dd9 100%);
}

.stat-icon.green {
  background: linear-gradient(135deg, #52c41a 0%, #389e0d 100%);
}

.stat-icon.purple {
  background: linear-gradient(135deg, #722ed1 0%, #531dab 100%);
}

.stat-icon.orange {
  background: linear-gradient(135deg, #fa8c16 0%, #d46b08 100%);
}

.stat-info {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: #8c8c8c;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #262626;
}

.card-container {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #262626;
}

.json-display {
  background: #fafafa;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 300px;
  overflow-y: auto;
  margin: 0;
}
</style>
