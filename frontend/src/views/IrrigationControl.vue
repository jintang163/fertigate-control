<template>
  <div class="irrigation-control">
    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :lg="12">
        <div class="card-container">
          <div class="page-header">
            <span class="page-title">控制模式</span>
            <a-switch
              :checked="controlMode === 'auto'"
              checked-children="自动"
              un-checked-children="手动"
              @change="handleModeChange"
            />
          </div>
          <a-alert
            v-if="controlMode === 'auto'"
            type="success"
            show-icon
            message="自动控制已启用"
            description="系统将根据土壤湿度阈值和作物生长模型自动控制阀门开关"
            style="margin-top: 16px"
          />
          <a-alert
            v-else
            type="warning"
            show-icon
            message="手动控制已启用"
            description="所有阀门需要手动操作，请谨慎控制"
            style="margin-top: 16px"
          />
        </div>
      </a-col>
      
      <a-col :xs="24" :lg="12">
        <div class="card-container">
          <div class="page-title">灌溉计划</div>
          <a-list
            :data-source="irrigationPlans"
            :locale="{ emptyText: '暂无灌溉计划' }"
          >
            <template #renderItem="{ item }">
              <a-list-item>
                <a-list-item-meta
                  :title="item.name"
                  :description="`${item.zoneName} | ${item.startTime} - ${item.endTime} | 每周: ${item.daysOfWeek?.join(',') || '每天'}`"
                />
                <a-tag :color="item.enabled ? 'success' : 'default'">
                  {{ item.enabled ? '已启用' : '已禁用' }}
                </a-tag>
              </a-list-item>
            </template>
          </a-list>
        </div>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]" style="margin-top: 16px">
      <a-col :xs="24">
        <div class="card-container">
          <div class="page-header">
            <span class="page-title">阀门控制</span>
            <a-space>
              <a-button type="primary" @click="openAllValves" :disabled="controlMode === 'auto'">
                全部开启
              </a-button>
              <a-button danger @click="closeAllValves">
                全部关闭
              </a-button>
            </a-space>
          </div>
          
          <a-table
            :columns="valveColumns"
            :data-source="valves"
            :pagination="false"
            :loading="loading"
            row-key="id"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'isOpen'">
                <a-tag :color="record.isOpen ? 'success' : 'default'">
                  {{ record.isOpen ? '已开启' : '已关闭' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'isAutoControlled'">
                <a-tag :color="record.isAutoControlled ? 'blue' : 'orange'">
                  {{ record.isAutoControlled ? '自动' : '手动' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space>
                  <a-button
                    :type="record.isOpen ? 'default' : 'primary'"
                    :loading="loadingMap[record.id]"
                    :disabled="controlMode === 'auto' && record.isAutoControlled"
                    @click="toggleValve(record)"
                  >
                    {{ record.isOpen ? '关闭' : '开启' }}
                  </a-button>
                  <a-button type="link" @click="showValveDetail(record)">
                    详情
                  </a-button>
                </a-space>
              </template>
            </template>
          </a-table>
        </div>
      </a-col>
    </a-row>

    <a-modal
      v-model:open="detailModalVisible"
      title="阀门详情"
      :footer="null"
      width="600px"
    >
      <a-descriptions :column="2" bordered v-if="selectedValve">
        <a-descriptions-item label="阀门名称">{{ selectedValve.name }}</a-descriptions-item>
        <a-descriptions-item label="设备编号">{{ selectedValve.deviceCode }}</a-descriptions-item>
        <a-descriptions-item label="所属区域">{{ selectedValve.zoneName }}</a-descriptions-item>
        <a-descriptions-item label="当前状态">
          <a-tag :color="selectedValve.isOpen ? 'success' : 'default'">
            {{ selectedValve.isOpen ? '已开启' : '已关闭' }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="控制模式">
          <a-tag :color="selectedValve.isAutoControlled ? 'blue' : 'orange'">
            {{ selectedValve.isAutoControlled ? '自动' : '手动' }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="流量(L/min)">{{ selectedValve.flowRate || '--' }}</a-descriptions-item>
        <a-descriptions-item label="上次开启">{{ selectedValve.lastOpenTime || '--' }}</a-descriptions-item>
        <a-descriptions-item label="上次关闭">{{ selectedValve.lastCloseTime || '--' }}</a-descriptions-item>
        <a-descriptions-item label="累计灌溉时长">{{ selectedValve.totalIrrigationTime || '--' }} 分钟</a-descriptions-item>
        <a-descriptions-item label="备注">{{ selectedValve.description || '--' }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useAppStore } from '@/stores'
import { storeToRefs } from 'pinia'
import { irrigationApi } from '@/api'
import type { Valve, IrrigationPlan } from '@/types'

const appStore = useAppStore()
const { valves, irrigationPlans, controlStatus, loading } = storeToRefs(appStore)

const controlMode = ref('auto')
const detailModalVisible = ref(false)
const selectedValve = ref<Valve | null>(null)
const loadingMap = reactive<Record<string, boolean>>({})

const valveColumns = [
  { title: '阀门名称', dataIndex: 'name', key: 'name' },
  { title: '设备编号', dataIndex: 'deviceCode', key: 'deviceCode' },
  { title: '所属区域', dataIndex: 'zoneName', key: 'zoneName' },
  { title: '状态', key: 'isOpen' },
  { title: '控制模式', key: 'isAutoControlled' },
  { title: '操作', key: 'action', width: 180 }
]

function handleModeChange(checked: boolean) {
  Modal.confirm({
    title: '确认切换控制模式',
    content: checked ? '切换到自动模式后，系统将自动控制阀门开关。是否继续？' : '切换到手动模式后，所有自动控制将暂停。是否继续？',
    onOk: async () => {
      try {
        const newMode = checked ? 'auto' : 'manual'
        await irrigationApi.setControlMode(newMode)
        controlMode.value = newMode
        message.success(`已切换到${checked ? '自动' : '手动'}模式`)
        await appStore.fetchControlStatus()
      } catch (e) {
        message.error('切换控制模式失败')
      }
    }
  })
}

async function toggleValve(valve: Valve) {
  if (controlMode.value === 'auto' && valve.isAutoControlled) {
    message.warning('该阀门处于自动控制模式，请先切换到手动模式')
    return
  }
  
  const open = !valve.isOpen
  Modal.confirm({
    title: `确认${open ? '开启' : '关闭'}阀门`,
    content: `确定要${open ? '开启' : '关闭'}阀门【${valve.name}】吗？`,
    onOk: async () => {
      try {
        loadingMap[valve.id] = true
        await irrigationApi.controlValve(valve.id, open, '手动操作')
        message.success(`阀门已${open ? '开启' : '关闭'}`)
        await appStore.fetchValves()
      } catch (e) {
        message.error(`${open ? '开启' : '关闭'}阀门失败`)
      } finally {
        loadingMap[valve.id] = false
      }
    }
  })
}

async function openAllValves() {
  Modal.confirm({
    title: '确认开启所有阀门',
    content: '确定要开启所有阀门吗？此操作将导致大面积灌溉。',
    okType: 'danger',
    onOk: async () => {
      try {
        const results = await Promise.all(
          valves.value.map(v => irrigationApi.controlValve(v.id, true, '手动全开'))
        )
        if (results.every(r => r)) {
          message.success('所有阀门已开启')
          await appStore.fetchValves()
        }
      } catch (e) {
        message.error('开启阀门失败')
      }
    }
  })
}

async function closeAllValves() {
  Modal.confirm({
    title: '确认关闭所有阀门',
    content: '确定要关闭所有阀门吗？',
    onOk: async () => {
      try {
        const results = await Promise.all(
          valves.value.map(v => irrigationApi.controlValve(v.id, false, '手动全关'))
        )
        if (results.every(r => r)) {
          message.success('所有阀门已关闭')
          await appStore.fetchValves()
        }
      } catch (e) {
        message.error('关闭阀门失败')
      }
    }
  })
}

function showValveDetail(valve: Valve) {
  selectedValve.value = valve
  detailModalVisible.value = true
}

onMounted(async () => {
  await Promise.all([
    appStore.fetchValves(),
    appStore.fetchIrrigationPlans(),
    appStore.fetchControlStatus()
  ])
  controlMode.value = controlStatus.value?.controlMode || 'auto'
})
</script>
