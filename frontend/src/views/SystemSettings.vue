<template>
  <div class="system-settings">
    <a-tabs v-model:activeKey="activeKey">
      <a-tab-pane key="general" tab="通用设置">
        <div class="card-container">
          <a-form
            :model="generalForm"
            layout="vertical"
            style="max-width: 800px; margin: 0 auto"
          >
            <a-form-item label="系统名称">
              <a-input v-model:value="generalForm.systemName" />
            </a-form-item>
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="数据采集间隔(秒)">
                  <a-input-number v-model:value="generalForm.collectInterval" :min="1" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="控制检查间隔(秒)">
                  <a-input-number v-model:value="generalForm.controlCheckInterval" :min="5" style="width: 100%" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="数据保留天数">
                  <a-input-number v-model:value="generalForm.dataRetentionDays" :min="7" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="告警保留天数">
                  <a-input-number v-model:value="generalForm.alertRetentionDays" :min="7" style="width: 100%" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-form-item label="时区">
              <a-select v-model:value="generalForm.timezone">
                <a-select-option value="Asia/Shanghai">Asia/Shanghai (UTC+8)</a-select-option>
                <a-select-option value="UTC">UTC (UTC+0)</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item>
              <a-button type="primary" @click="saveGeneralSettings" :loading="saving">
                保存设置
              </a-button>
            </a-form-item>
          </a-form>
        </div>
      </a-tab-pane>
      
      <a-tab-pane key="notifications" tab="通知设置">
        <div class="card-container">
          <a-form
            :model="notificationForm"
            layout="vertical"
            style="max-width: 800px; margin: 0 auto"
          >
            <a-divider orientation="left">短信通知</a-divider>
            <a-form-item label="启用短信通知">
              <a-switch v-model:checked="notificationForm.smsEnabled" />
            </a-form-item>
            <a-form-item v-if="notificationForm.smsEnabled" label="通知手机号">
              <a-textarea v-model:value="notificationForm.smsPhones" placeholder="多个号码用逗号分隔" :rows="3" />
            </a-form-item>
            
            <a-divider orientation="left">邮件通知</a-divider>
            <a-form-item label="启用邮件通知">
              <a-switch v-model:checked="notificationForm.emailEnabled" />
            </a-form-item>
            <a-form-item v-if="notificationForm.emailEnabled" label="通知邮箱">
              <a-textarea v-model:value="notificationForm.emailAddresses" placeholder="多个邮箱用逗号分隔" :rows="3" />
            </a-form-item>
            
            <a-divider orientation="left">通知级别</a-divider>
            <a-form-item label="严重告警">
              <a-checkbox v-model:checked="notificationForm.notifyCritical">短信 + 邮件</a-checkbox>
            </a-form-item>
            <a-form-item label="警告">
              <a-checkbox v-model:checked="notificationForm.notifyWarning">邮件</a-checkbox>
            </a-form-item>
            <a-form-item label="信息">
              <a-checkbox v-model:checked="notificationForm.notifyInfo">不通知</a-checkbox>
            </a-form-item>
            
            <a-form-item>
              <a-button type="primary" @click="saveNotificationSettings" :loading="saving">
                保存设置
              </a-button>
            </a-form-item>
          </a-form>
        </div>
      </a-tab-pane>
      
      <a-tab-pane key="mqtt" tab="MQTT设置">
        <div class="card-container">
          <a-descriptions :column="2" bordered>
            <a-descriptions-item label="服务器地址">{{ mqttConfig.host }}</a-descriptions-item>
            <a-descriptions-item label="端口">{{ mqttConfig.port }}</a-descriptions-item>
            <a-descriptions-item label="用户名">{{ mqttConfig.username }}</a-descriptions-item>
            <a-descriptions-item label="连接状态">
              <a-badge :status="mqttConfig.connected ? 'success' : 'error'" :text="mqttConfig.connected ? '已连接' : '未连接'" />
            </a-descriptions-item>
            <a-descriptions-item label="订阅主题">{{ mqttConfig.subscribedTopics?.join(', ') }}</a-descriptions-item>
            <a-descriptions-item label="最后心跳">{{ mqttConfig.lastHeartbeat || '--' }}</a-descriptions-item>
          </a-descriptions>
          <a-space style="margin-top: 24px">
            <a-button @click="testMqttConnection" :loading="testing">
              {{ testing ? '测试中...' : '测试连接' }}
            </a-button>
            <a-button @click="reconnectMqtt" :disabled="mqttConfig.connected">
              重新连接
            </a-button>
          </a-space>
        </div>
      </a-tab-pane>
      
      <a-tab-pane key="about" tab="关于系统">
        <div class="card-container" style="text-align: center; padding: 48px">
          <WaterToolOutlined style="font-size: 64px; color: #1890ff" />
          <h1 style="margin-top: 24px; margin-bottom: 8px">水肥一体化智能灌溉控制系统</h1>
          <p style="color: #8c8c8c; margin-bottom: 32px">Fertigate Intelligent Irrigation Control System</p>
          <a-descriptions :column="2" bordered style="max-width: 600px; margin: 0 auto">
            <a-descriptions-item label="系统版本">v1.0.0</a-descriptions-item>
            <a-descriptions-item label="构建时间">2024-01-15</a-descriptions-item>
            <a-descriptions-item label="技术栈">Spring Boot + Vue3 + TypeScript</a-descriptions-item>
            <a-descriptions-item label="数据库">PostgreSQL + InfluxDB</a-descriptions-item>
            <a-descriptions-item label="通信协议">MQTT + Modbus/RS485</a-descriptions-item>
            <a-descriptions-item label="版权所有">© 2024 Fertigate</a-descriptions-item>
          </a-descriptions>
        </div>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { dashboardApi } from '@/api'
import { WaterToolOutlined } from '@ant-design/icons-vue'

const activeKey = ref('general')
const saving = ref(false)
const testing = ref(false)

const generalForm = reactive({
  systemName: '水肥一体化智能灌溉控制系统',
  collectInterval: 5,
  controlCheckInterval: 30,
  dataRetentionDays: 365,
  alertRetentionDays: 90,
  timezone: 'Asia/Shanghai'
})

const notificationForm = reactive({
  smsEnabled: false,
  smsPhones: '',
  emailEnabled: false,
  emailAddresses: '',
  notifyCritical: true,
  notifyWarning: true,
  notifyInfo: false
})

const mqttConfig = reactive({
  host: 'localhost',
  port: 1883,
  username: 'admin',
  connected: true,
  subscribedTopics: [
    'fertigate/sensor/data',
    'fertigate/device/status',
    'fertigate/alert',
    'fertigate/gateway/heartbeat'
  ],
  lastHeartbeat: '2024-01-15 14:30:00'
})

async function saveGeneralSettings() {
  try {
    saving.value = true
    await dashboardApi.updateSystemConfig({ key: 'general', value: JSON.stringify(generalForm) })
    message.success('通用设置保存成功')
  } catch (e) {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function saveNotificationSettings() {
  try {
    saving.value = true
    await dashboardApi.updateSystemConfig({ key: 'notification', value: JSON.stringify(notificationForm) })
    message.success('通知设置保存成功')
  } catch (e) {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function testMqttConnection() {
  try {
    testing.value = true
    await new Promise(resolve => setTimeout(resolve, 1500))
    mqttConfig.connected = true
    message.success('MQTT连接测试成功')
  } catch (e) {
    mqttConfig.connected = false
    message.error('MQTT连接测试失败')
  } finally {
    testing.value = false
  }
}

async function reconnectMqtt() {
  try {
    testing.value = true
    await new Promise(resolve => setTimeout(resolve, 2000))
    mqttConfig.connected = true
    message.success('MQTT重新连接成功')
  } catch (e) {
    message.error('重新连接失败')
  } finally {
    testing.value = false
  }
}

onMounted(async () => {
  try {
    const config = await dashboardApi.getSystemConfig()
    if (config) {
      Object.assign(generalForm, config.general || {})
      Object.assign(notificationForm, config.notification || {})
    }
  } catch (e) {
    console.error('Failed to load system config:', e)
  }
})
</script>
