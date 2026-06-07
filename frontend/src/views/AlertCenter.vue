<template>
  <div class="alert-center">
    <div class="card-container">
      <div class="page-header">
        <span class="page-title">告警中心</span>
        <a-space>
          <a-select
            v-model:value="levelFilter"
            placeholder="告警级别"
            style="width: 150px"
            allow-clear
          >
            <a-select-option value="CRITICAL">严重</a-select-option>
            <a-select-option value="WARNING">警告</a-select-option>
            <a-select-option value="INFO">信息</a-select-option>
          </a-select>
          <a-select
            v-model:value="acknowledgedFilter"
            placeholder="处理状态"
            style="width: 150px"
            allow-clear
          >
            <a-select-option :value="true">已处理</a-select-option>
            <a-select-option :value="false">未处理</a-select-option>
          </a-select>
          <a-button v-if="hasPermission('alert:acknowledge')" type="primary" @click="acknowledgeAll" :disabled="unacknowledgedCount === 0">
            <CheckOutlined />
            全部处理
          </a-button>
        </a-space>
      </div>
      
      <a-row :gutter="[16, 16]" style="margin-bottom: 24px">
        <a-col :xs="24" :sm="8">
          <div class="stat-card">
            <a-row align="middle">
              <a-col :span="18">
                <div class="stat-value" style="color: #ff4d4f">{{ criticalCount }}</div>
                <div class="stat-label">严重告警</div>
              </a-col>
              <a-col :span="6" class="text-right">
                <ExclamationCircleOutlined style="font-size: 32px; color: #ff4d4f" />
              </a-col>
            </a-row>
          </div>
        </a-col>
        <a-col :xs="24" :sm="8">
          <div class="stat-card">
            <a-row align="middle">
              <a-col :span="18">
                <div class="stat-value" style="color: #faad14">{{ warningCount }}</div>
                <div class="stat-label">警告</div>
              </a-col>
              <a-col :span="6" class="text-right">
                <WarningOutlined style="font-size: 32px; color: #faad14" />
              </a-col>
            </a-row>
          </div>
        </a-col>
        <a-col :xs="24" :sm="8">
          <div class="stat-card">
            <a-row align="middle">
              <a-col :span="18">
                <div class="stat-value" style="color: #1890ff">{{ infoCount }}</div>
                <div class="stat-label">信息</div>
              </a-col>
              <a-col :span="6" class="text-right">
                <InfoCircleOutlined style="font-size: 32px; color: #1890ff" />
              </a-col>
            </a-row>
          </div>
        </a-col>
      </a-row>

      <a-list
        :data-source="filteredAlerts"
        :locale="{ emptyText: '暂无告警' }"
        :pagination="{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }"
      >
        <template #renderItem="{ item }">
          <a-list-item>
            <div :class="['alert-item', item.level.toLowerCase()]">
              <a-row>
                <a-col :xs="24" :lg="18">
                  <div class="alert-title">
                    <component :is="getAlertIcon(item.level)" style="margin-right: 8px" />
                    {{ item.alertType }}
                  </div>
                  <div class="alert-message">{{ item.message }}</div>
                  <a-space size="large" style="margin-top: 8px">
                    <span class="alert-time">
                      <ClockCircleOutlined style="margin-right: 4px" />
                      {{ formatTime(item.createdAt) }}
                    </span>
                    <span class="alert-time">
                      <EnvironmentOutlined style="margin-right: 4px" />
                      {{ item.zoneName || item.deviceCode || '系统' }}
                    </span>
                    <span v-if="item.acknowledgedBy" class="alert-time">
                      <UserOutlined style="margin-right: 4px" />
                      处理人: {{ item.acknowledgedBy }}
                    </span>
                    <span v-if="item.acknowledgedAt" class="alert-time">
                      处理时间: {{ formatTime(item.acknowledgedAt) }}
                    </span>
                  </a-space>
                </a-col>
                <a-col :xs="24" :lg="6" style="text-align: right">
                  <a-space>
                    <a-tag :color="getLevelColor(item.level)">
                      {{ getLevelName(item.level) }}
                    </a-tag>
                    <a-tag :color="item.acknowledged ? 'default' : 'warning'">
                      {{ item.acknowledged ? '已处理' : '未处理' }}
                    </a-tag>
                    <a-button
                      v-if="!item.acknowledged && hasPermission('alert:acknowledge')"
                      type="primary"
                      size="small"
                      @click="acknowledgeAlert(item.id)"
                    >
                      处理
                    </a-button>
                  </a-space>
                </a-col>
              </a-row>
            </div>
          </a-list-item>
        </template>
      </a-list>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useAppStore } from '@/stores'
import { storeToRefs } from 'pinia'
import { alertApi } from '@/api'
import type { Alert, AlertLevel } from '@/types'
import dayjs from 'dayjs'
import {
  ExclamationCircleOutlined,
  WarningOutlined,
  InfoCircleOutlined,
  ClockCircleOutlined,
  EnvironmentOutlined,
  UserOutlined,
  CheckOutlined
} from '@ant-design/icons-vue'

const appStore = useAppStore()
const { alerts, loading } = storeToRefs(appStore)
const hasPermission = appStore.hasPermission
const hasAnyRole = appStore.hasAnyRole

const levelFilter = ref<AlertLevel | undefined>()
const acknowledgedFilter = ref<boolean | undefined>()

const criticalCount = computed(() => alerts.value.filter(a => a.level === 'CRITICAL').length)
const warningCount = computed(() => alerts.value.filter(a => a.level === 'WARNING').length)
const infoCount = computed(() => alerts.value.filter(a => a.level === 'INFO').length)
const unacknowledgedCount = computed(() => alerts.value.filter(a => !a.acknowledged).length)

const filteredAlerts = computed(() => {
  let result = [...alerts.value]
  if (levelFilter.value) {
    result = result.filter(a => a.level === levelFilter.value)
  }
  if (acknowledgedFilter.value !== undefined) {
    result = result.filter(a => a.acknowledged === acknowledgedFilter.value)
  }
  return result.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
})

function getAlertIcon(level: AlertLevel) {
  const icons: Record<AlertLevel, any> = {
    CRITICAL: ExclamationCircleOutlined,
    WARNING: WarningOutlined,
    INFO: InfoCircleOutlined
  }
  return icons[level] || InfoCircleOutlined
}

function getLevelColor(level: AlertLevel) {
  const colors: Record<AlertLevel, string> = {
    CRITICAL: 'red',
    WARNING: 'orange',
    INFO: 'blue'
  }
  return colors[level] || 'default'
}

function getLevelName(level: AlertLevel) {
  const names: Record<AlertLevel, string> = {
    CRITICAL: '严重',
    WARNING: '警告',
    INFO: '信息'
  }
  return names[level] || level
}

function formatTime(time: string) {
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

async function acknowledgeAlert(id: string) {
  try {
    await alertApi.acknowledge(id, '管理员')
    message.success('告警已处理')
    await appStore.fetchAlerts()
  } catch (e) {
    message.error('处理告警失败')
  }
}

async function acknowledgeAll() {
  Modal.confirm({
    title: '确认批量处理',
    content: `确定要处理所有 ${unacknowledgedCount.value} 条未处理告警吗？`,
    onOk: async () => {
      try {
        const results = await Promise.all(
          alerts.value
            .filter(a => !a.acknowledged)
            .map(a => alertApi.acknowledge(a.id, '管理员'))
        )
        if (results.every(r => r)) {
          message.success(`已处理 ${results.length} 条告警`)
          await appStore.fetchAlerts()
        }
      } catch (e) {
        message.error('批量处理失败')
      }
    }
  })
}

onMounted(async () => {
  await appStore.fetchAlerts()
})
</script>
