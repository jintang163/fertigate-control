<template>
  <div class="crop-management">
    <div class="card-container">
      <div class="page-header">
        <span class="page-title">作物管理</span>
        <a-button type="primary" @click="addCrop">
          <PlusOutlined />
          添加作物
        </a-button>
      </div>
      
      <a-table
        :columns="cropColumns"
        :data-source="crops"
        :pagination="{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }"
        :loading="loading"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'growthStage'">
            <a-tag :color="getStageColor(record.currentGrowthStage)">
              {{ getStageName(record.currentGrowthStage) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'optimalHumidity'">
            {{ record.minOptimalHumidity }}% - {{ record.maxOptimalHumidity }}%
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" @click="editCrop(record)">编辑</a-button>
              <a-popconfirm
                title="确认删除此作物？"
                @confirm="deleteCrop(record.id)"
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
      :title="isEdit ? '编辑作物' : '添加作物'"
      @ok="handleSubmit"
      @cancel="handleCancel"
      :confirm-loading="submitting"
      width="700px"
    >
      <a-form
        ref="formRef"
        :model="cropForm"
        layout="vertical"
      >
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item
              label="作物名称"
              name="name"
              :rules="[{ required: true, message: '请输入作物名称' }]"
            >
              <a-input v-model:value="cropForm.name" placeholder="如: 番茄" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item
              label="品种"
              name="variety"
            >
              <a-input v-model:value="cropForm.variety" placeholder="如: 粉丽人" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item
              label="当前生长阶段"
              name="currentGrowthStage"
              :rules="[{ required: true, message: '请选择生长阶段' }]"
            >
              <a-select v-model:value="cropForm.currentGrowthStage">
                <a-select-option value="SEEDLING">苗期</a-select-option>
                <a-select-option value="VEGETATIVE">营养生长期</a-select-option>
                <a-select-option value="FLOWERING">开花期</a-select-option>
                <a-select-option value="FRUITING">结果期</a-select-option>
                <a-select-option value="RIPENING">成熟期</a-select-option>
                <a-select-option value="HARVEST">收获期</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item
              label="种植日期"
              name="plantingDate"
            >
              <a-date-picker v-model:value="cropForm.plantingDate" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item
              label="预计收获日期"
              name="expectedHarvestDate"
            >
              <a-date-picker v-model:value="cropForm.expectedHarvestDate" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-divider orientation="left">灌溉参数</a-divider>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item
              label="最适湿度下限(%)"
              name="minOptimalHumidity"
              :rules="[{ required: true, message: '请输入' }]"
            >
              <a-input-number v-model:value="cropForm.minOptimalHumidity" :min="0" :max="100" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item
              label="最适湿度上限(%)"
              name="maxOptimalHumidity"
              :rules="[{ required: true, message: '请输入' }]"
            >
              <a-input-number v-model:value="cropForm.maxOptimalHumidity" :min="0" :max="100" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item
              label="预警阈值(%)"
              name="humidityWarningThreshold"
              :rules="[{ required: true, message: '请输入' }]"
            >
              <a-input-number v-model:value="cropForm.humidityWarningThreshold" :min="0" :max="100" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item
              label="每日需水量(L/株)"
              name="dailyWaterRequirement"
            >
              <a-input-number v-model:value="cropForm.dailyWaterRequirement" :min="0" :step="0.1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item
              label="种植密度(株/亩)"
              name="plantingDensity"
            >
              <a-input-number v-model:value="cropForm.plantingDensity" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="备注" name="description">
          <a-textarea v-model:value="cropForm.description" :rows="3" placeholder="请输入备注信息" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useAppStore } from '@/stores'
import { storeToRefs } from 'pinia'
import { cropApi } from '@/api'
import type { Crop, GrowthStage } from '@/types'
import { PlusOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'

const appStore = useAppStore()
const { crops, loading } = storeToRefs(appStore)

const modalVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref()

const cropForm = reactive({
  id: '',
  name: '',
  variety: '',
  currentGrowthStage: 'SEEDLING' as GrowthStage,
  plantingDate: null as dayjs.Dayjs | null,
  expectedHarvestDate: null as dayjs.Dayjs | null,
  minOptimalHumidity: 60,
  maxOptimalHumidity: 80,
  humidityWarningThreshold: 40,
  dailyWaterRequirement: 0.5,
  plantingDensity: 2000,
  description: ''
})

const cropColumns = [
  { title: '作物名称', dataIndex: 'name', key: 'name' },
  { title: '品种', dataIndex: 'variety', key: 'variety' },
  { title: '当前阶段', key: 'growthStage', width: 120 },
  { title: '种植日期', dataIndex: 'plantingDate', key: 'plantingDate', width: 120 },
  { title: '预计收获', dataIndex: 'expectedHarvestDate', key: 'expectedHarvestDate', width: 120 },
  { title: '最适湿度', key: 'optimalHumidity', width: 120 },
  { title: '预警阈值', dataIndex: 'humidityWarningThreshold', key: 'humidityWarningThreshold', width: 100 },
  { title: '操作', key: 'action', width: 150, fixed: 'right' }
]

function getStageColor(stage: GrowthStage) {
  const colors: Record<GrowthStage, string> = {
    SEEDLING: 'cyan',
    VEGETATIVE: 'green',
    FLOWERING: 'purple',
    FRUITING: 'orange',
    RIPENING: 'gold',
    HARVEST: 'red'
  }
  return colors[stage] || 'default'
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

function addCrop() {
  isEdit.value = false
  Object.assign(cropForm, {
    id: '',
    name: '',
    variety: '',
    currentGrowthStage: 'SEEDLING' as GrowthStage,
    plantingDate: null,
    expectedHarvestDate: null,
    minOptimalHumidity: 60,
    maxOptimalHumidity: 80,
    humidityWarningThreshold: 40,
    dailyWaterRequirement: 0.5,
    plantingDensity: 2000,
    description: ''
  })
  modalVisible.value = true
}

function editCrop(crop: Crop) {
  isEdit.value = true
  Object.assign(cropForm, {
    id: crop.id,
    name: crop.name,
    variety: crop.variety || '',
    currentGrowthStage: crop.currentGrowthStage,
    plantingDate: crop.plantingDate ? dayjs(crop.plantingDate) : null,
    expectedHarvestDate: crop.expectedHarvestDate ? dayjs(crop.expectedHarvestDate) : null,
    minOptimalHumidity: crop.minOptimalHumidity,
    maxOptimalHumidity: crop.maxOptimalHumidity,
    humidityWarningThreshold: crop.humidityWarningThreshold,
    dailyWaterRequirement: crop.dailyWaterRequirement || 0.5,
    plantingDensity: crop.plantingDensity || 2000,
    description: crop.description || ''
  })
  modalVisible.value = true
}

async function handleSubmit() {
  try {
    submitting.value = true
    const submitData = {
      ...cropForm,
      plantingDate: cropForm.plantingDate?.toISOString(),
      expectedHarvestDate: cropForm.expectedHarvestDate?.toISOString()
    }
    if (isEdit.value) {
      await cropApi.update(cropForm.id, submitData as any)
      message.success('作物更新成功')
    } else {
      await cropApi.create(submitData as any)
      message.success('作物添加成功')
    }
    modalVisible.value = false
    await appStore.fetchCrops()
  } catch (e) {
    message.error(isEdit.value ? '更新作物失败' : '添加作物失败')
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  modalVisible.value = false
}

async function deleteCrop(id: string) {
  try {
    await cropApi.delete(id)
    message.success('删除成功')
    await appStore.fetchCrops()
  } catch (e) {
    message.error('删除失败')
  }
}

onMounted(async () => {
  await appStore.fetchCrops()
})
</script>
