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
