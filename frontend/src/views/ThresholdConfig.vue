<template>
  <div class="threshold-config">
    <div class="card-container">
      <div class="page-header">
        <span class="page-title">阈值策略配置</span>
        <a-button v-if="hasPermission('threshold:manage')" type="primary" @click="addStrategy">
          <PlusOutlined />
          新增策略
        </a-button>
      </div>

      <div class="filter-section">
        <a-row :gutter="16">
          <a-col :span="6">
            <a-select
              v-model:value="filterZoneId"
              placeholder="选择灌区"
              allow-clear
              style="width: 100%"
              @change="fetchStrategies"
            >
              <a-select-option v-for="zone in zones" :key="zone.id" :value="zone.id">
                {{ zone.name }}
              </a-select-option>
            </a-select>
          </a-col>
          <a-col :span="6">
            <a-select
              v-model:value="filterCropId"
              placeholder="选择作物"
              allow-clear
              style="width: 100%"
              @change="fetchStrategies"
            >
              <a-select-option v-for="crop in crops" :key="crop.id" :value="crop.id">
                {{ crop.name }}
              </a-select-option>
            </a-select>
          </a-col>
          <a-col :span="6">
            <a-select
              v-model:value="filterStatus"
              placeholder="选择状态"
              allow-clear
              style="width: 100%"
              @change="fetchStrategies"
            >
              <a-select-option value="active">启用</a-select-option>
              <a-select-option value="inactive">禁用</a-select-option>
            </a-select>
          </a-col>
          <a-col :span="6">
            <a-button @click="resetFilters">重置筛选</a-button>
          </a-col>
        </a-row>
      </div>

      <a-table
        :columns="columns"
        :data-source="displayStrategies"
        :pagination="{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }"
        :loading="loading"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'zoneId'">
            {{ getZoneName(record.zoneId) }}
          </template>
          <template v-else-if="column.key === 'cropId'">
            {{ getCropName(record.cropId) }}
          </template>
          <template v-else-if="column.key === 'humidity'">
            {{ record.minHumidity }}% - {{ record.maxHumidity }}%
          </template>
          <template v-else-if="column.key === 'ec'">
            {{ record.minEc }} - {{ record.maxEc }} mS/cm
          </template>
          <template v-else-if="column.key === 'ph'">
            {{ record.minPh }} - {{ record.maxPh }}
          </template>
          <template v-else-if="column.key === 'isActive'">
            <a-tag :color="record.isActive ? 'green' : 'default'">
              {{ record.isActive ? '启用' : '禁用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space v-if="hasPermission('threshold:manage')">
              <a-button type="link" @click="editStrategy(record)">编辑</a-button>
              <a-popconfirm
                :title="record.isActive ? '确认禁用此策略？' : '确认启用此策略？'"
                @confirm="toggleActive(record)"
              >
                <a-button type="link" :type="record.isActive ? undefined : 'primary'">
                  {{ record.isActive ? '禁用' : '启用' }}
                </a-button>
              </a-popconfirm>
              <a-popconfirm
                title="确认删除此策略？"
                @confirm="deleteStrategy(record.id)"
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
      :title="isEdit ? '编辑策略' : '新增策略'"
      @ok="handleSubmit"
      @cancel="handleCancel"
      :confirm-loading="submitting"
      width="800px"
    >
      <a-form
        ref="formRef"
        :model="strategyForm"
        layout="vertical"
      >
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item
              label="策略名称"
              name="name"
              :rules="[{ required: true, message: '请输入策略名称' }]"
            >
              <a-input v-model:value="strategyForm.name" placeholder="如: 番茄生长期阈值策略" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="优先级" name="priority">
              <a-input-number
                v-model:value="strategyForm.priority"
                :min="1"
                :max="10"
                style="width: 100%"
                placeholder="1-10，数字越大优先级越高"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="描述" name="description">
          <a-textarea v-model:value="strategyForm.description" :rows="2" placeholder="请输入策略描述" />
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item
              label="关联灌区"
              name="zoneId"
              :rules="[{ required: true, message: '请选择关联灌区' }]"
            >
              <a-select v-model:value="strategyForm.zoneId" placeholder="请选择灌区">
                <a-select-option v-for="zone in zones" :key="zone.id" :value="zone.id">
                  {{ zone.name }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item
              label="关联作物"
              name="cropId"
              :rules="[{ required: true, message: '请选择关联作物' }]"
            >
              <a-select v-model:value="strategyForm.cropId" placeholder="请选择作物">
                <a-select-option v-for="crop in crops" :key="crop.id" :value="crop.id">
                  {{ crop.name }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>

        <a-divider orientation="left">环境参数阈值</a-divider>

        <a-form-item label="土壤湿度范围 (%)">
          <a-slider
            v-model:value="strategyForm.humidityRange"
            range
            :min="0"
            :max="100"
            :marks="{ 0: '0%', 25: '25%', 50: '50%', 75: '75%', 100: '100%' }"
          />
          <div class="range-display">
            当前范围: {{ strategyForm.humidityRange[0] }}% - {{ strategyForm.humidityRange[1] }}%
          </div>
        </a-form-item>

        <a-form-item label="EC值范围 (mS/cm)">
          <a-slider
            v-model:value="strategyForm.ecRange"
            range
            :min="0"
            :max="5"
            :step="0.1"
            :marks="{ 0: '0', 1: '1', 2: '2', 3: '3', 4: '4', 5: '5' }"
          />
          <div class="range-display">
            当前范围: {{ strategyForm.ecRange[0] }} - {{ strategyForm.ecRange[1] }} mS/cm
          </div>
        </a-form-item>

        <a-form-item label="pH值范围">
          <a-slider
            v-model:value="strategyForm.phRange"
            range
            :min="4"
            :max="9"
            :step="0.1"
            :marks="{ 4: '4', 5: '5', 6: '6', 7: '7', 8: '8', 9: '9' }"
          />
          <div class="range-display">
            当前范围: {{ strategyForm.phRange[0] }} - {{ strategyForm.phRange[1] }}
          </div>
        </a-form-item>

        <a-form-item label="温度范围 (°C)">
          <a-slider
            v-model:value="strategyForm.temperatureRange"
            range
            :min="0"
            :max="50"
            :marks="{ 0: '0°C', 10: '10°C', 20: '20°C', 30: '30°C', 40: '40°C', 50: '50°C' }"
          />
          <div class="range-display">
            当前范围: {{ strategyForm.temperatureRange[0] }}°C - {{ strategyForm.temperatureRange[1] }}°C
          </div>
        </a-form-item>

        <a-divider orientation="left">智能控制</a-divider>

        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="气象联动">
              <a-switch v-model:checked="strategyForm.weatherLinkEnabled" />
              <span class="switch-label">
                {{ strategyForm.weatherLinkEnabled ? '已启用' : '已禁用' }}
              </span>
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="避免雨天灌溉">
              <a-switch v-model:checked="strategyForm.avoidRainIrrigation" />
              <span class="switch-label">
                {{ strategyForm.avoidRainIrrigation ? '已启用' : '已禁用' }}
              </span>
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="高温灌溉">
              <a-switch v-model:checked="strategyForm.highTempIrrigation" />
              <span class="switch-label">
                {{ strategyForm.highTempIrrigation ? '已启用' : '已禁用' }}
              </span>
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="启用状态">
          <a-switch v-model:checked="strategyForm.isActive" />
          <span class="switch-label">
            {{ strategyForm.isActive ? '已启用' : '已禁用' }}
          </span>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { thresholdApi, zoneApi, cropApi } from '@/api'
import type { ThresholdStrategy, Zone, Crop } from '@/types'
import { PlusOutlined } from '@ant-design/icons-vue'
import { useAppStore } from '@/stores'

const store = useAppStore()
const hasPermission = store.hasPermission
const hasAnyRole = store.hasAnyRole

const loading = ref(false)
const submitting = ref(false)
const modalVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()

const strategies = ref<ThresholdStrategy[]>([])
const zones = ref<Zone[]>([])
const crops = ref<Crop[]>([])

const filterZoneId = ref<string | undefined>()
const filterCropId = ref<string | undefined>()
const filterStatus = ref<string | undefined>()

const mockZones: Zone[] = [
  { id: 'zone-1', name: '一号灌区', area: 50, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'zone-2', name: '二号灌区', area: 30, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'zone-3', name: '三号灌区', area: 40, createdAt: '2024-01-01', updatedAt: '2024-01-01' }
]

const mockCrops: Crop[] = [
  { id: 'crop-1', name: '番茄', growthStage: 'FRUITING', minHumidity: 60, maxHumidity: 80, optimalEc: 2.0, optimalPh: 6.5, waterRequirement: 5, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'crop-2', name: '黄瓜', growthStage: 'VEGETATIVE', minHumidity: 70, maxHumidity: 90, optimalEc: 1.8, optimalPh: 6.0, waterRequirement: 6, createdAt: '2024-01-01', updatedAt: '2024-01-01' },
  { id: 'crop-3', name: '草莓', growthStage: 'FLOWERING', minHumidity: 65, maxHumidity: 85, optimalEc: 1.5, optimalPh: 5.8, waterRequirement: 4, createdAt: '2024-01-01', updatedAt: '2024-01-01' }
]

const mockStrategies: ThresholdStrategy[] = [
  {
    id: 'strategy-1',
    name: '番茄结果期阈值策略',
    description: '番茄结果期的最优环境参数控制',
    zoneId: 'zone-1',
    cropId: 'crop-1',
    minHumidity: 60,
    maxHumidity: 80,
    minEc: 1.5,
    maxEc: 2.5,
    minPh: 6.0,
    maxPh: 7.0,
    minTemperature: 18,
    maxTemperature: 30,
    maxWindSpeed: 10,
    minRainfall: 0,
    weatherLinkEnabled: true,
    avoidRainIrrigation: true,
    highTempIrrigation: false,
    isActive: true,
    priority: 5,
    createdAt: '2024-01-15',
    updatedAt: '2024-01-15'
  },
  {
    id: 'strategy-2',
    name: '黄瓜营养生长期策略',
    description: '黄瓜营养生长期快速生长阶段的阈值控制',
    zoneId: 'zone-2',
    cropId: 'crop-2',
    minHumidity: 70,
    maxHumidity: 90,
    minEc: 1.2,
    maxEc: 2.0,
    minPh: 5.5,
    maxPh: 6.5,
    minTemperature: 20,
    maxTemperature: 32,
    maxWindSpeed: 8,
    minRainfall: 0,
    weatherLinkEnabled: true,
    avoidRainIrrigation: true,
    highTempIrrigation: true,
    isActive: true,
    priority: 3,
    createdAt: '2024-01-20',
    updatedAt: '2024-01-20'
  },
  {
    id: 'strategy-3',
    name: '草莓开花期策略',
    description: '草莓开花期的精细环境控制',
    zoneId: 'zone-3',
    cropId: 'crop-3',
    minHumidity: 65,
    maxHumidity: 85,
    minEc: 1.0,
    maxEc: 1.8,
    minPh: 5.5,
    maxPh: 6.2,
    minTemperature: 15,
    maxTemperature: 28,
    maxWindSpeed: 5,
    minRainfall: 0,
    weatherLinkEnabled: true,
    avoidRainIrrigation: true,
    highTempIrrigation: false,
    isActive: false,
    priority: 8,
    createdAt: '2024-02-01',
    updatedAt: '2024-02-01'
  }
]

const strategyForm = reactive({
  id: '',
  name: '',
  description: '',
  zoneId: '',
  cropId: '',
  humidityRange: [50, 80] as [number, number],
  ecRange: [1.0, 2.5] as [number, number],
  phRange: [5.5, 7.0] as [number, number],
  temperatureRange: [15, 35] as [number, number],
  weatherLinkEnabled: true,
  avoidRainIrrigation: true,
  highTempIrrigation: false,
  isActive: true,
  priority: 5
})

const columns = [
  { title: '策略名称', dataIndex: 'name', key: 'name', width: 180 },
  { title: '关联灌区', key: 'zoneId', width: 120 },
  { title: '关联作物', key: 'cropId', width: 120 },
  { title: '湿度范围', key: 'humidity', width: 120 },
  { title: 'EC范围', key: 'ec', width: 140 },
  { title: 'pH范围', key: 'ph', width: 120 },
  { title: '优先级', dataIndex: 'priority', key: 'priority', width: 80 },
  { title: '状态', key: 'isActive', width: 80 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' }
]

const displayStrategies = computed(() => {
  let result = [...strategies.value]
  
  if (filterStatus.value) {
    const isActive = filterStatus.value === 'active'
    result = result.filter(s => s.isActive === isActive)
  }
  
  return result
})

const zoneMap = computed(() => {
  const map: Record<string, string> = {}
  zones.value.forEach(z => { map[z.id] = z.name })
  return map
})

const cropMap = computed(() => {
  const map: Record<string, string> = {}
  crops.value.forEach(c => { map[c.id] = c.name })
  return map
})

function getZoneName(zoneId?: string) {
  return zoneMap.value[zoneId || ''] || '未关联'
}

function getCropName(cropId?: string) {
  return cropMap.value[cropId || ''] || '未关联'
}

async function fetchZones() {
  try {
    const data = await zoneApi.getAll()
    zones.value = data
  } catch (e) {
    zones.value = mockZones
  }
}

async function fetchCrops() {
  try {
    const data = await cropApi.getAll()
    crops.value = data
  } catch (e) {
    crops.value = mockCrops
  }
}

async function fetchStrategies() {
  loading.value = true
  try {
    let data: ThresholdStrategy[]
    if (filterZoneId.value) {
      data = await thresholdApi.getByZone(filterZoneId.value)
    } else if (filterCropId.value) {
      data = await thresholdApi.getByCrop(filterCropId.value)
    } else {
      data = await thresholdApi.getAll()
    }
    strategies.value = data
  } catch (e) {
    strategies.value = mockStrategies
    if (filterZoneId.value) {
      strategies.value = strategies.value.filter(s => s.zoneId === filterZoneId.value)
    }
    if (filterCropId.value) {
      strategies.value = strategies.value.filter(s => s.cropId === filterCropId.value)
    }
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filterZoneId.value = undefined
  filterCropId.value = undefined
  filterStatus.value = undefined
  fetchStrategies()
}

function addStrategy() {
  isEdit.value = false
  Object.assign(strategyForm, {
    id: '',
    name: '',
    description: '',
    zoneId: zones.value[0]?.id || '',
    cropId: crops.value[0]?.id || '',
    humidityRange: [50, 80] as [number, number],
    ecRange: [1.0, 2.5] as [number, number],
    phRange: [5.5, 7.0] as [number, number],
    temperatureRange: [15, 35] as [number, number],
    weatherLinkEnabled: true,
    avoidRainIrrigation: true,
    highTempIrrigation: false,
    isActive: true,
    priority: 5
  })
  modalVisible.value = true
}

function editStrategy(strategy: ThresholdStrategy) {
  isEdit.value = true
  Object.assign(strategyForm, {
    id: strategy.id,
    name: strategy.name,
    description: strategy.description || '',
    zoneId: strategy.zoneId || '',
    cropId: strategy.cropId || '',
    humidityRange: [strategy.minHumidity, strategy.maxHumidity] as [number, number],
    ecRange: [strategy.minEc, strategy.maxEc] as [number, number],
    phRange: [strategy.minPh, strategy.maxPh] as [number, number],
    temperatureRange: [strategy.minTemperature, strategy.maxTemperature] as [number, number],
    weatherLinkEnabled: strategy.weatherLinkEnabled,
    avoidRainIrrigation: strategy.avoidRainIrrigation,
    highTempIrrigation: strategy.highTempIrrigation,
    isActive: strategy.isActive,
    priority: strategy.priority
  })
  modalVisible.value = true
}

async function handleSubmit() {
  try {
    submitting.value = true
    const submitData: Partial<ThresholdStrategy> = {
      name: strategyForm.name,
      description: strategyForm.description,
      zoneId: strategyForm.zoneId,
      cropId: strategyForm.cropId,
      minHumidity: strategyForm.humidityRange[0],
      maxHumidity: strategyForm.humidityRange[1],
      minEc: strategyForm.ecRange[0],
      maxEc: strategyForm.ecRange[1],
      minPh: strategyForm.phRange[0],
      maxPh: strategyForm.phRange[1],
      minTemperature: strategyForm.temperatureRange[0],
      maxTemperature: strategyForm.temperatureRange[1],
      maxWindSpeed: 10,
      minRainfall: 0,
      weatherLinkEnabled: strategyForm.weatherLinkEnabled,
      avoidRainIrrigation: strategyForm.avoidRainIrrigation,
      highTempIrrigation: strategyForm.highTempIrrigation,
      isActive: strategyForm.isActive,
      priority: strategyForm.priority
    }

    if (isEdit.value) {
      await thresholdApi.update(strategyForm.id, submitData)
      message.success('策略更新成功')
    } else {
      await thresholdApi.create(submitData)
      message.success('策略创建成功')
    }
    modalVisible.value = false
    await fetchStrategies()
  } catch (e) {
    if (isEdit.value) {
      const idx = strategies.value.findIndex(s => s.id === strategyForm.id)
      if (idx !== -1) {
        strategies.value[idx] = {
          ...strategies.value[idx],
          name: strategyForm.name,
          description: strategyForm.description,
          zoneId: strategyForm.zoneId,
          cropId: strategyForm.cropId,
          minHumidity: strategyForm.humidityRange[0],
          maxHumidity: strategyForm.humidityRange[1],
          minEc: strategyForm.ecRange[0],
          maxEc: strategyForm.ecRange[1],
          minPh: strategyForm.phRange[0],
          maxPh: strategyForm.phRange[1],
          minTemperature: strategyForm.temperatureRange[0],
          maxTemperature: strategyForm.temperatureRange[1],
          weatherLinkEnabled: strategyForm.weatherLinkEnabled,
          avoidRainIrrigation: strategyForm.avoidRainIrrigation,
          highTempIrrigation: strategyForm.highTempIrrigation,
          isActive: strategyForm.isActive,
          priority: strategyForm.priority
        }
      }
      message.success('策略更新成功（本地模拟）')
    } else {
      const newStrategy: ThresholdStrategy = {
        id: 'strategy-' + Date.now(),
        name: strategyForm.name,
        description: strategyForm.description,
        zoneId: strategyForm.zoneId,
        cropId: strategyForm.cropId,
        minHumidity: strategyForm.humidityRange[0],
        maxHumidity: strategyForm.humidityRange[1],
        minEc: strategyForm.ecRange[0],
        maxEc: strategyForm.ecRange[1],
        minPh: strategyForm.phRange[0],
        maxPh: strategyForm.phRange[1],
        minTemperature: strategyForm.temperatureRange[0],
        maxTemperature: strategyForm.temperatureRange[1],
        maxWindSpeed: 10,
        minRainfall: 0,
        weatherLinkEnabled: strategyForm.weatherLinkEnabled,
        avoidRainIrrigation: strategyForm.avoidRainIrrigation,
        highTempIrrigation: strategyForm.highTempIrrigation,
        isActive: strategyForm.isActive,
        priority: strategyForm.priority,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      }
      strategies.value.unshift(newStrategy)
      message.success('策略创建成功（本地模拟）')
    }
    modalVisible.value = false
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  modalVisible.value = false
}

async function toggleActive(strategy: ThresholdStrategy) {
  try {
    await thresholdApi.setActive(strategy.id, !strategy.isActive)
    message.success(strategy.isActive ? '已禁用' : '已启用')
    await fetchStrategies()
  } catch (e) {
    const idx = strategies.value.findIndex(s => s.id === strategy.id)
    if (idx !== -1) {
      strategies.value[idx].isActive = !strategy.isActive
    }
    message.success(strategy.isActive ? '已禁用（本地模拟）' : '已启用（本地模拟）')
  }
}

async function deleteStrategy(id: string) {
  try {
    await thresholdApi.delete(id)
    message.success('删除成功')
    await fetchStrategies()
  } catch (e) {
    strategies.value = strategies.value.filter(s => s.id !== id)
    message.success('删除成功（本地模拟）')
  }
}

onMounted(async () => {
  await Promise.all([
    fetchZones(),
    fetchCrops()
  ])
  await fetchStrategies()
})
</script>

<style scoped>
.threshold-config {
  padding: 20px;
}

.card-container {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}

.filter-section {
  margin-bottom: 20px;
  padding: 16px;
  background: #fafafa;
  border-radius: 6px;
}

.range-display {
  margin-top: 8px;
  font-size: 14px;
  color: #1890ff;
  font-weight: 500;
}

.switch-label {
  margin-left: 8px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.65);
}
</style>
