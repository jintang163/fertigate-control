<template>
  <div class="device-management">
    <div class="card-container">
      <div class="page-header">
        <span class="page-title">设备管理</span>
        <a-space>
          <a-select
            v-model:value="deviceTypeFilter"
            placeholder="设备类型"
            style="width: 150px"
            allow-clear
          >
            <a-select-option value="SENSOR_SOIL">土壤传感器</a-select-option>
            <a-select-option value="SENSOR_WEATHER">气象站</a-select-option>
            <a-select-option value="VALVE">电磁阀</a-select-option>
            <a-select-option value="GATEWAY">网关</a-select-option>
          </a-select>
          <a-select
            v-model:value="statusFilter"
            placeholder="在线状态"
            style="width: 150px"
            allow-clear
          >
            <a-select-option value="ONLINE">在线</a-select-option>
            <a-select-option value="OFFLINE">离线</a-select-option>
            <a-select-option value="FAULT">故障</a-select-option>
          </a-select>
          <a-button type="primary" @click="addDevice">
            <PlusOutlined />
            添加设备
          </a-button>
        </a-space>
      </div>
      
      <a-table
        :columns="deviceColumns"
        :data-source="filteredDevices"
        :pagination="{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }"
        :loading="loading"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'deviceType'">
            <a-tag :color="getDeviceTypeColor(record.deviceType)">
              {{ getDeviceTypeName(record.deviceType) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-badge
              :status="getStatusBadge(record.status)"
              :text="getStatusName(record.status)"
            />
          </template>
          <template v-else-if="column.key === 'lastDataTime'">
            {{ record.lastDataTime || '--' }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" @click="viewDevice(record)">
                查看
              </a-button>
              <a-button type="link" @click="editDevice(record)">
                编辑
              </a-button>
              <a-popconfirm
                title="确认删除此设备？"
                @confirm="deleteDevice(record.id)"
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
      :title="isEdit ? '编辑设备' : '添加设备'"
      @ok="handleSubmit"
      @cancel="handleCancel"
      :confirm-loading="submitting"
      width="600px"
    >
      <a-form
        ref="formRef"
        :model="deviceForm"
        layout="vertical"
        label-col={{ span: 6 }}
        wrapper-col={{ span: 18 }}
      >
        <a-form-item
          label="设备名称"
          name="name"
          :rules="[{ required: true, message: '请输入设备名称' }]"
        >
          <a-input v-model:value="deviceForm.name" placeholder="请输入设备名称" />
        </a-form-item>
        <a-form-item
          label="设备编号"
          name="deviceCode"
          :rules="[{ required: true, message: '请输入设备编号' }]"
        >
          <a-input v-model:value="deviceForm.deviceCode" placeholder="如: SENSOR-SOIL-001" :disabled="isEdit" />
        </a-form-item>
        <a-form-item
          label="设备类型"
          name="deviceType"
          :rules="[{ required: true, message: '请选择设备类型' }]"
        >
          <a-select v-model:value="deviceForm.deviceType" placeholder="请选择设备类型">
            <a-select-option value="SENSOR_SOIL">土壤传感器</a-select-option>
            <a-select-option value="SENSOR_WEATHER">气象站</a-select-option>
            <a-select-option value="VALVE">电磁阀</a-select-option>
            <a-select-option value="GATEWAY">网关</a-select-option>
          </a-select>
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="Modbus地址" name="modbusAddress">
              <a-input-number v-model:value="deviceForm.modbusAddress" :min="1" :max="247" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="采集间隔(秒)" name="pollInterval">
              <a-input-number v-model:value="deviceForm.pollInterval" :min="1" :min="3600" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="位置描述" name="location">
          <a-input v-model:value="deviceForm.location" placeholder="请输入设备安装位置" />
        </a-form-item>
        <a-form-item label="备注" name="description">
          <a-textarea v-model:value="deviceForm.description" :rows="3" placeholder="请输入备注信息" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="detailModalVisible"
      title="设备详情"
      :footer="null"
      width="700px"
    >
      <a-descriptions :column="2" bordered v-if="selectedDevice">
        <a-descriptions-item label="设备名称">{{ selectedDevice.name }}</a-descriptions-item>
        <a-descriptions-item label="设备编号">{{ selectedDevice.deviceCode }}</a-descriptions-item>
        <a-descriptions-item label="设备类型">
          <a-tag :color="getDeviceTypeColor(selectedDevice.deviceType)">
            {{ getDeviceTypeName(selectedDevice.deviceType) }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-badge
            :status="getStatusBadge(selectedDevice.status)"
            :text="getStatusName(selectedDevice.status)"
          />
        </a-descriptions-item>
        <a-descriptions-item label="Modbus地址">{{ selectedDevice.modbusAddress }}</a-descriptions-item>
        <a-descriptions-item label="采集间隔">{{ selectedDevice.pollInterval }} 秒</a-descriptions-item>
        <a-descriptions-item label="安装位置">{{ selectedDevice.location || '--' }}</a-descriptions-item>
        <a-descriptions-item label="最后数据时间">{{ selectedDevice.lastDataTime || '--' }}</a-descriptions-item>
        <a-descriptions-item label="注册时间">{{ selectedDevice.createdAt }}</a-descriptions-item>
        <a-descriptions-item label="备注">{{ selectedDevice.description || '--' }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useAppStore } from '@/stores'
import { storeToRefs } from 'pinia'
import { deviceApi } from '@/api'
import type { Device, DeviceType, DeviceStatus } from '@/types'

const appStore = useAppStore()
const { devices, loading } = storeToRefs(appStore)

const deviceTypeFilter = ref<DeviceType | undefined>()
const statusFilter = ref<DeviceStatus | undefined>()
const modalVisible = ref(false)
const detailModalVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const selectedDevice = ref<Device | null>(null)
const formRef = ref()

const deviceForm = reactive({
  id: '',
  name: '',
  deviceCode: '',
  deviceType: '' as DeviceType,
  modbusAddress: 1,
  pollInterval: 5,
  location: '',
  description: ''
})

const deviceColumns = [
  { title: '设备名称', dataIndex: 'name', key: 'name' },
  { title: '设备编号', dataIndex: 'deviceCode', key: 'deviceCode' },
  { title: '类型', key: 'deviceType', width: 120 },
  { title: '状态', key: 'status', width: 120 },
  { title: 'Modbus地址', dataIndex: 'modbusAddress', key: 'modbusAddress', width: 120 },
  { title: '最后数据', key: 'lastDataTime', width: 180 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' }
]

const filteredDevices = computed(() => {
  let result = [...devices.value]
  if (deviceTypeFilter.value) {
    result = result.filter(d => d.deviceType === deviceTypeFilter.value)
  }
  if (statusFilter.value) {
    result = result.filter(d => d.status === statusFilter.value)
  }
  return result
})

function getDeviceTypeColor(type: DeviceType) {
  const colors: Record<DeviceType, string> = {
    SENSOR_SOIL: 'purple',
    SENSOR_WEATHER: 'blue',
    VALVE: 'green',
    GATEWAY: 'cyan',
    CONTROLLER: 'orange'
  }
  return colors[type] || 'default'
}

function getDeviceTypeName(type: DeviceType) {
  const names: Record<DeviceType, string> = {
    SENSOR_SOIL: '土壤传感器',
    SENSOR_WEATHER: '气象站',
    VALVE: '电磁阀',
    GATEWAY: '网关',
    CONTROLLER: '控制器'
  }
  return names[type] || type
}

function getStatusBadge(status: DeviceStatus) {
  const badges: Record<DeviceStatus, 'success' | 'error' | 'warning' | 'default'> = {
    ONLINE: 'success',
    OFFLINE: 'error',
    FAULT: 'warning',
    MAINTENANCE: 'default'
  }
  return badges[status] || 'default'
}

function getStatusName(status: DeviceStatus) {
  const names: Record<DeviceStatus, string> = {
    ONLINE: '在线',
    OFFLINE: '离线',
    FAULT: '故障',
    MAINTENANCE: '维护中'
  }
  return names[status] || status
}

function addDevice() {
  isEdit.value = false
  Object.assign(deviceForm, {
    id: '',
    name: '',
    deviceCode: '',
    deviceType: 'SENSOR_SOIL' as DeviceType,
    modbusAddress: 1,
    pollInterval: 5,
    location: '',
    description: ''
  })
  modalVisible.value = true
}

function editDevice(device: Device) {
  isEdit.value = true
  Object.assign(deviceForm, {
    id: device.id,
    name: device.name,
    deviceCode: device.deviceCode,
    deviceType: device.deviceType,
    modbusAddress: device.modbusAddress || 1,
    pollInterval: device.pollInterval || 5,
    location: device.location || '',
    description: device.description || ''
  })
  modalVisible.value = true
}

function viewDevice(device: Device) {
  selectedDevice.value = device
  detailModalVisible.value = true
}

async function handleSubmit() {
  try {
    submitting.value = true
    if (isEdit.value) {
      await deviceApi.update(deviceForm.id, deviceForm as any)
      message.success('设备更新成功')
    } else {
      await deviceApi.create(deviceForm as any)
      message.success('设备添加成功')
    }
    modalVisible.value = false
    await appStore.fetchDevices()
  } catch (e) {
    message.error(isEdit.value ? '更新设备失败' : '添加设备失败')
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  modalVisible.value = false
}

async function deleteDevice(id: string) {
  try {
    await deviceApi.delete(id)
    message.success('删除成功')
    await appStore.fetchDevices()
  } catch (e) {
    message.error('删除失败')
  }
}

onMounted(async () => {
  await appStore.fetchDevices()
})
</script>
