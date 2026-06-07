export interface Device {
  id: string
  deviceCode: string
  name: string
  type: string
  zoneId?: string
  modbusAddress?: number
  modbusPort?: string
  status: string
  lastHeartbeat?: string
  createdAt: string
  updatedAt: string
}

export interface Valve {
  id: string
  device?: Device
  zoneId?: string
  valveNumber?: number
  flowRate: number
  isOpen: boolean
  autoControl: boolean
  lastOperation?: string
  createdAt: string
  updatedAt: string
}

export interface Zone {
  id: string
  name: string
  description?: string
  cropId?: string
  crop?: Crop
  area?: number
  createdAt: string
  updatedAt: string
}

export interface Crop {
  id: string
  name: string
  variety?: string
  growthStage: string
  minHumidity: number
  maxHumidity: number
  optimalEc: number
  optimalPh: number
  waterRequirement: number
  plantingDate?: string
  expectedHarvestDate?: string
  createdAt: string
  updatedAt: string
}

export interface SensorData {
  deviceCode: string
  deviceName: string
  deviceType: string
  zone: string
  gatewayId: string
  timestamp: string
  values: Record<string, number>
  interlockSafe?: boolean
}

export interface IrrigationDecision {
  zoneId: string
  zoneName: string
  cropId?: string
  currentHumidity?: number
  minHumidity: number
  maxHumidity: number
  needIrrigation: boolean
  reason: string
  durationSeconds?: number
  waterAmount?: number
}

export interface IrrigationPlan {
  id: string
  zoneId?: string
  cropId?: string
  name: string
  description?: string
  startTime?: string
  endTime?: string
  duration?: number
  intervalHours?: number
  waterAmount?: number
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface IrrigationRecord {
  id: string
  zoneId?: string
  valveId?: string
  planId?: string
  startTime: string
  endTime?: string
  waterAmount?: number
  reason?: string
  createdAt: string
}

export interface Alert {
  id: string
  deviceId?: string
  device?: Device
  alertType: string
  level: string
  message: string
  sensorValue?: number
  thresholdValue?: number
  isAcknowledged: boolean
  acknowledgedAt?: string
  createdAt: string
}

export interface ControlStatus {
  controlMode: string
  scheduledStops: number
  openValves: number
  scheduledValveStops: Array<{
    valveId: string
    scheduledStopTime: string
  }>
}

export interface SensorDataPoint {
  time: string
  value: number
  device_code: string
  zone: string
}

export interface ThresholdStrategy {
  id: string
  name: string
  description?: string
  zoneId?: string
  cropId?: string
  minHumidity: number
  maxHumidity: number
  minEc: number
  maxEc: number
  minPh: number
  maxPh: number
  minTemperature: number
  maxTemperature: number
  maxWindSpeed: number
  minRainfall: number
  weatherLinkEnabled: boolean
  avoidRainIrrigation: boolean
  highTempIrrigation: boolean
  isActive: boolean
  priority: number
  createdAt: string
  updatedAt: string
}

export interface RotationSchedule {
  id: string
  name: string
  description?: string
  strategyId?: string
  zoneIds: string[]
  startTime: string
  endTime: string
  duration: number
  intervalHours: number
  priority: number
  waterAmount: number
  fertilizerAmount: number
  irrigationType: string
  isActive: boolean
  lastExecution?: string
  nextExecution?: string
  createdAt: string
  updatedAt: string
}

export interface FertigationRecord {
  id: string
  zoneId: string
  zoneName: string
  valveId?: string
  planId?: string
  startTime: string
  endTime?: string
  durationSeconds: number
  waterAmount: number
  fertilizerAmount: number
  fertilizerType?: string
  averageEc: number
  averagePh: number
  executionMode: string
  irrigationType: string
  status: string
  reason?: string
}

export interface ZoneSensorData {
  zoneId: string
  zoneName: string
  humidity: number
  ec: number
  ph: number
  temperature: number
  timestamp: string
}

export interface WeatherData {
  temperature: number
  humidity: number
  windSpeed: number
  rainfall: number
  light: number
  timestamp: string
}

export interface DeviceStatus {
  deviceId: string
  deviceCode: string
  name: string
  type: string
  status: 'online' | 'offline'
  lastHeartbeat?: string
  zoneId?: string
}

export interface MapZone {
  id: string
  name: string
  x: number
  y: number
  width: number
  height: number
  status: 'normal' | 'warning' | 'error' | 'irrigation'
  devices: MapDevice[]
}

export interface MapDevice {
  id: string
  name: string
  type: 'valve' | 'sensor' | 'pump'
  x: number
  y: number
  status: 'online' | 'offline'
  isOpen?: boolean
}

export interface GanttTask {
  id: string
  name: string
  start: string
  end: string
  progress: number
  zoneId: string
  status: 'pending' | 'running' | 'completed' | 'cancelled'
}

export interface User {
  id: string
  username: string
  realName: string
  email?: string
  phone?: string
  enabled: boolean
  lastLoginTime?: string
  lastLoginIp?: string
  createdAt: string
  updatedAt: string
  roleCodes?: string[]
  roleNames?: string[]
}

export interface Role {
  id: string
  roleCode: string
  roleName: string
  description?: string
  createdAt: string
  updatedAt: string
  permissionCodes?: string[]
  permissionNames?: string[]
}

export interface OperationLog {
  id: string
  userId?: string
  username: string
  realName: string
  operation: string
  operationType: string
  operationTypeDesc: string
  description?: string
  targetType?: string
  targetId?: string
  oldValue?: string
  newValue?: string
  ipAddress?: string
  userAgent?: string
  requestUri?: string
  requestMethod?: string
  success: boolean
  errorMessage?: string
  executionTime?: number
  createdAt: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresIn: number
  userId: string
  username: string
  realName: string
  roles: string[]
  permissions: string[]
}

export interface CreateUserRequest {
  username: string
  password: string
  realName: string
  email?: string
  phone?: string
  enabled?: boolean
  roleIds?: string[]
}

export interface UpdateUserRequest {
  realName?: string
  email?: string
  phone?: string
  enabled?: boolean
  roleIds?: string[]
}
