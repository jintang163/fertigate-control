<template>
  <div class="zone-management">
    <div class="card-container">
      <div class="page-header">
        <span class="page-title">区域管理</span>
        <a-button v-if="hasPermission('zone:manage')" type="primary" @click="addZone">
          <PlusOutlined />
          添加区域
        </a-button>
      </div>
      
      <a-table
        :columns="zoneColumns"
        :data-source="zones"
        :pagination="{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }"
        :loading="loading"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'area'">
            {{ record.area }} 亩
          </template>
          <template v-if="column.key === 'cropId'">
            {{ getCropName(record.cropId) }}
          </template>
          <template v-else-if="column.key === 'irrigationMode'">
            <a-tag :color="getModeColor(record.irrigationMode)">
              {{ getModeName(record.irrigationMode) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'humidityThreshold'">
            {{ record.minHumidity }}% - {{ record.maxHumidity }}%
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space v-if="hasPermission('zone:manage')">
              <a-button type="link" @click="editZone(record)">编辑</a-button>
              <a-popconfirm
                title="确认删除此区域？"
                @confirm="deleteZone(record.id)"
              >
                <a-button type="link" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal
      v-model:open="modalVisible"
      :title="isEdit ? '编辑区域' : '添加区域'"
      @ok="handleSubmit"
      @cancel="handleCancel"
      :confirm-loading="submitting"
      width="700px"
    >
      <a-form
        ref="formRef"
        :model="zoneForm"
        layout="vertical"
      >
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item
              label="区域名称"
              name="name"
              :rules="[{ required: true, message: '请输入区域名称' }]"
            >
              <a-input v-model:value="zoneForm.name" placeholder="如: 一号田块" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item
              label="区域代码"
              name="code"
              :rules="[{ required: true, message: '请输入区域代码' }]"
            >
              <a-input v-model:value="zoneForm.code" placeholder="如: ZONE-001" :disabled="isEdit" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item
              label="面积(亩)"
              name="area"
              :rules="[{ required: true, message: '请输入面积' }]"
            >
              <a-input-number v-model:value="zoneForm.area" :min="0" :step="0.1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item
              label="种植作物"
              name="cropId"
            >
              <a-select v-model:value="zoneForm.cropId" placeholder="请选择作物" allow-clear>
                <a-select-option v-for="crop in crops" :key="crop.id" :value="crop.id">
                  {{ crop.name }} - {{ getStageName(crop.currentGrowthStage) }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item
              label="灌溉模式"
              name="irrigationMode"
              :rules="[{ required: true, message: '请选择灌溉模式' }]"
            >
              <a-select v-model:value="zoneForm.irrigationMode">
                <a-select-option value="THRESHOLD">阈值控制</a-select-option>
                <a-select-option value="SCHEDULE">定时灌溉</a-select-option>
                <a-select-option value="MODEL">作物模型</a-select-option>
                <a-select-option value="MANUAL">手动控制</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
        <a-divider orientation="left">湿度阈值</a-divider>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item
              label="最小湿度(%)"
              name="minHumidity"
              :rules="[{ required: true, message: '请输入' }]"
            >
              <a-input-number v-model:value="zoneForm.minHumidity" :min="0" :max="100" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item
              label="最大湿度(%)"
              name="maxHumidity"
              :rules="[{ required: true, message: '请输入' }]"
            >
              <a-input-number v-model:value="zoneForm.maxHumidity" :min="0" :max="100" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item
              label="单次最大时长(分钟)"
              name="maxIrrigationDuration"
            >
              <a-input-number v-model:value="zoneForm.maxIrrigationDuration" :min="1" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="位置描述" name="location">
          <a-input v-model:value="zoneForm.location" placeholder="请输入区域位置" />
        </a-form-item>
        <a-form-item label="备注" name="description">
          <a-textarea v-model:value="zoneForm.description" :rows="3" placeholder="请输入备注信息" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { useAppStore } from '@/stores'
import { storeToRefs } from 'pinia'
import { zoneApi } from '@/api'
import type { Zone, Crop, GrowthStage } from '@/types'
import { PlusOutlined } from '@ant-design/icons-vue'

const appStore = useAppStore()
const { zones, crops, loading } = storeToRefs(appStore)
const hasPermission = appStore.hasPermission
const hasAnyRole = appStore.hasAnyRole

const modalVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref()

const zoneForm = reactive({
  id: '',
  name: '',
  code: '',
  area: 0,
  cropId: '',
  irrigationMode: 'THRESHOLD' as const,
  minHumidity: 50,
  maxHumidity: 80,
  maxIrrigationDuration: 60,
  location: '',
  description: ''
})

const zoneColumns = [
  { title: '区域名称', dataIndex: 'name', key: 'name' },
  { title: '区域代码', dataIndex: 'code', key: 'code' },
  { title: '面积', key: 'area', width: 100 },
  { title: '种植作物', key: 'cropId', width: 180 },
  { title: '灌溉模式', key: 'irrigationMode', width: 120 },
  { title: '湿度阈值', key: 'humidityThreshold', width: 120 },
  { title: '位置', dataIndex: 'location', key: 'location' },
  { title: '操作', key: 'action', width: 150, fixed: 'right' }
]

const cropMap = computed(() => {
  const map: Record<string, string> = {}
  crops.value.forEach(c => { map[c.id] = c.name })
  return map
})

function getCropName(cropId?: string) {
  return cropMap.value[cropId || ''] || '未种植'
}

function getStageName(stage: GrowthStage) {
  const names: Record<GrowthStage, string> = {
    SEEDLING: '苗期',
    VEGETATIVE: '营养生长期',
    FLOWERING: '开花期',
    FRUITING: '结果期',
    RIPENING: '成熟期',
    HARVEST: '收获期'
  }
  return names[stage] || stage
}

function getModeColor(mode: string) {
  const colors: Record<string, string> = {
    THRESHOLD: 'blue',
    SCHEDULE: 'purple',
    MODEL: 'green',
    MANUAL: 'orange'
  }
  return colors[mode] || 'default'
}

function getModeName(mode: string) {
  const names: Record<string, string> = {
    THRESHOLD: '阈值控制',
    SCHEDULE: '定时灌溉',
    MODEL: '作物模型',
    MANUAL: '手动控制'
  }
  return names[mode] || mode
}

function addZone() {
  isEdit.value = false
  Object.assign(zoneForm, {
    id: '',
    name: '',
    code: '',
    area: 0,
    cropId: '',
    irrigationMode: 'THRESHOLD' as const,
    minHumidity: 50,
    maxHumidity: 80,
    maxIrrigationDuration: 60,
    location: '',
    description: ''
  })
  modalVisible.value = true
}

function editZone(zone: Zone) {
  isEdit.value = true
  Object.assign(zoneForm, {
    id: zone.id,
    name: zone.name,
    code: zone.code,
    area: zone.area,
    cropId: zone.cropId || '',
    irrigationMode: zone.irrigationMode,
    minHumidity: zone.minHumidity,
    maxHumidity: zone.maxHumidity,
    maxIrrigationDuration: zone.maxIrrigationDuration || 60,
    location: zone.location || '',
    description: zone.description || ''
  })
  modalVisible.value = true
}

async function handleSubmit() {
  try {
    submitting.value = true
    if (isEdit.value) {
      await zoneApi.update(zoneForm.id, zoneForm as any)
      message.success('区域更新成功')
    } else {
      await zoneApi.create(zoneForm as any)
      message.success('区域添加成功')
    }
    modalVisible.value = false
    await appStore.fetchZones()
  } catch (e) {
    message.error(isEdit.value ? '更新区域失败' : '添加区域失败')
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  modalVisible.value = false
}

async function deleteZone(id: string) {
  try {
    await zoneApi.delete(id)
    message.success('删除成功')
    await appStore.fetchZones()
  } catch (e) {
    message.error('删除失败')
  }
}

onMounted(async () => {
  await Promise.all([
    appStore.fetchZones(),
    appStore.fetchCrops()
  ])
})
</script>
