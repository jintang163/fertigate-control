import request from './request'
import type { 
  Device, Valve, Zone, Crop, Alert, 
  IrrigationDecision, IrrigationPlan, IrrigationRecord,
  ControlStatus, SensorDataPoint,
  ThresholdStrategy, RotationSchedule, FertigationRecord,
  ZoneSensorData, WeatherData
} from '@/types'

export const dashboardApi = {
  getOverview: () => request.get<any, any>('/dashboard/overview'),
  getRealtime: () => request.get<any, any>('/dashboard/realtime'),
  getSummary: () => request.get<any, any>('/dashboard/summary')
}

export const sensorApi = {
  getData: (deviceCode: string, sensorType: string, startTime: string, endTime: string, limit = 100) => 
    request.get<any, SensorDataPoint[]>(`/sensor/data/${deviceCode}`, {
      params: { sensorType, startTime, endTime, limit }
    }),
  getLatest: (deviceCode: string) => 
    request.get<any, Record<string, any>>(`/sensor/latest/${deviceCode}`),
  getByType: (deviceType: string, limit = 50) => 
    request.get<any, SensorDataPoint[]>(`/sensor/type/${deviceType}`, { params: { limit } })
}

export const deviceApi = {
  getAll: () => request.get<any, Device[]>('/device'),
  getById: (id: string) => request.get<any, Device>(`/device/${id}`),
  getByCode: (code: string) => request.get<any, Device>(`/device/code/${code}`),
  getByType: (type: string) => request.get<any, Device[]>(`/device/type/${type}`),
  create: (data: Partial<Device>) => request.post<any, Device>('/device', data),
  update: (id: string, data: Partial<Device>) => request.put<any, Device>(`/device/${id}`, data),
  delete: (id: string) => request.delete(`/device/${id}`),
  
  getAllValves: () => request.get<any, Valve[]>('/device/valve'),
  getValveById: (id: string) => request.get<any, Valve>(`/device/valve/${id}`),
  getValvesByZone: (zoneId: string) => request.get<any, Valve[]>(`/device/valve/zone/${zoneId}`),
  setValveAuto: (id: string, autoControl: boolean) => 
    request.put<any, Valve>(`/device/valve/${id}/auto`, null, { params: { autoControl } })
}

export const irrigationApi = {
  getControlStatus: () => request.get<any, ControlStatus>('/irrigation/control/status'),
  setControlMode: (mode: 'auto' | 'manual') => 
    request.put<any, any>('/irrigation/control/mode', null, { params: { mode } }),
  emergencyStop: () => request.post<any, any>('/irrigation/control/emergency-stop'),
  controlValve: (valveId: string, open: boolean, reason = '手动操作') => 
    request.post<any, any>(`/irrigation/valve/${valveId}/control`, null, { params: { open, reason } }),
  
  getDecision: (zoneId: string) => request.get<any, IrrigationDecision>(`/irrigation/decision/${zoneId}`),
  getAllDecisions: () => request.get<any, IrrigationDecision[]>('/irrigation/decision/all'),
  
  getAllPlans: () => request.get<any, IrrigationPlan[]>('/irrigation/plan'),
  getActivePlans: () => request.get<any, IrrigationPlan[]>('/irrigation/plan/active'),
  createPlan: (data: Partial<IrrigationPlan>) => 
    request.post<any, IrrigationPlan>('/irrigation/plan', data),
  setPlanActive: (id: string, active: boolean) => 
    request.put<any, IrrigationPlan>(`/irrigation/plan/${id}/active`, null, { params: { active } }),
  
  getRecords: (startTime?: string, endTime?: string) => 
    request.get<any, IrrigationRecord[]>('/irrigation/record', { params: { startTime, endTime } }),
  getRecordsByZone: (zoneId: string) => 
    request.get<any, IrrigationRecord[]>(`/irrigation/record/zone/${zoneId}`)
}

export const cropApi = {
  getAll: () => request.get<any, Crop[]>('/crop'),
  getById: (id: string) => request.get<any, Crop>(`/crop/${id}`),
  getGrowthInfo: (id: string) => request.get<any, any>(`/crop/${id}/growth-info`),
  create: (data: Partial<Crop>) => request.post<any, Crop>('/crop', data),
  update: (id: string, data: Partial<Crop>) => request.put<any, Crop>(`/crop/${id}`, data),
  delete: (id: string) => request.delete(`/crop/${id}`)
}

export const zoneApi = {
  getAll: () => request.get<any, Zone[]>('/zone'),
  getById: (id: string) => request.get<any, Zone>(`/zone/${id}`),
  create: (data: Partial<Zone>) => request.post<any, Zone>('/zone', data),
  update: (id: string, data: Partial<Zone>) => request.put<any, Zone>(`/zone/${id}`, data),
  delete: (id: string) => request.delete(`/zone/${id}`)
}

export const alertApi = {
  getAll: () => request.get<any, Alert[]>('/alert'),
  getUnacknowledged: () => request.get<any, Alert[]>('/alert/unacknowledged'),
  getById: (id: string) => request.get<any, Alert>(`/alert/${id}`),
  getByLevel: (level: string) => request.get<any, Alert[]>(`/alert/level/` + level),
  acknowledge: (id: string) => request.put<any, Alert>(`/alert/${id}/acknowledge`),
  acknowledgeAll: () => request.put<any, string>('/alert/acknowledge-all')
}

export const thresholdApi = {
  getAll: () => request.get<any, ThresholdStrategy[]>('/threshold'),
  getActive: () => request.get<any, ThresholdStrategy[]>('/threshold/active'),
  getById: (id: string) => request.get<any, ThresholdStrategy>(`/threshold/${id}`),
  getByZone: (zoneId: string) => request.get<any, ThresholdStrategy[]>(`/threshold/zone/${zoneId}`),
  getByCrop: (cropId: string) => request.get<any, ThresholdStrategy[]>(`/threshold/crop/${cropId}`),
  getByZoneAndCrop: (zoneId: string, cropId: string) => 
    request.get<any, ThresholdStrategy>(`/threshold/zone/${zoneId}/crop/${cropId}`),
  create: (data: Partial<ThresholdStrategy>) => 
    request.post<any, ThresholdStrategy>('/threshold', data),
  update: (id: string, data: Partial<ThresholdStrategy>) => 
    request.put<any, ThresholdStrategy>(`/threshold/${id}`, data),
  setActive: (id: string, active: boolean) => 
    request.put<any, ThresholdStrategy>(`/threshold/${id}/active`, null, { params: { active } }),
  delete: (id: string) => request.delete(`/threshold/${id}`),
  checkThresholds: (zoneId: string, sensorData: Record<string, number>) => 
    request.post<any, any>(`/threshold/check/${zoneId}`, sensorData)
}

export const rotationApi = {
  getAll: () => request.get<any, RotationSchedule[]>('/rotation'),
  getActive: () => request.get<any, RotationSchedule[]>('/rotation/active'),
  getById: (id: string) => request.get<any, RotationSchedule>(`/rotation/${id}`),
  getByZone: (zoneId: string) => request.get<any, RotationSchedule[]>(`/rotation/zone/${zoneId}`),
  create: (data: Partial<RotationSchedule>) => 
    request.post<any, RotationSchedule>('/rotation', data),
  generatePlan: (zoneId: string, irrigationType = 'irrigation', priority = 1) => 
    request.post<any, RotationSchedule[]>('/rotation/generate', null, { 
      params: { zoneId, irrigationType, priority } 
    }),
  update: (id: string, data: Partial<RotationSchedule>) => 
    request.put<any, RotationSchedule>(`/rotation/${id}`, data),
  setActive: (id: string, active: boolean) => 
    request.put<any, RotationSchedule>(`/rotation/${id}/active`, null, { params: { active } }),
  executeNow: (id: string) => request.post<any, any>(`/rotation/${id}/execute`),
  delete: (id: string) => request.delete(`/rotation/${id}`)
}

export const fertigationApi = {
  getAll: (startTime?: string, endTime?: string) => 
    request.get<any, FertigationRecord[]>('/fertigation', { params: { startTime, endTime } }),
  getById: (id: string) => request.get<any, FertigationRecord>(`/fertigation/${id}`),
  getByZone: (zoneId: string, startTime?: string, endTime?: string) => 
    request.get<any, FertigationRecord[]>(`/fertigation/zone/${zoneId}`, { params: { startTime, endTime } }),
  getByMode: (mode: string) => request.get<any, FertigationRecord[]>(`/fertigation/mode/${mode}`),
  getByType: (type: string) => request.get<any, FertigationRecord[]>(`/fertigation/type/${type}`),
  getStatistics: (zoneId: string, startTime: string, endTime: string) => 
    request.get<any, any>('/fertigation/statistics', { params: { zoneId, startTime, endTime } }),
  create: (data: Partial<FertigationRecord>) => 
    request.post<any, FertigationRecord>('/fertigation', data),
  update: (id: string, data: Partial<FertigationRecord>) => 
    request.put<any, FertigationRecord>(`/fertigation/${id}`, data),
  complete: (id: string) => request.put<any, FertigationRecord>(`/fertigation/${id}/complete`),
  delete: (id: string) => request.delete(`/fertigation/${id}`),
  exportExcel: (startTime?: string, endTime?: string, zoneId?: string) => 
    request.get<any, Blob>('/fertigation/export', { 
      params: { startTime, endTime, zoneId },
      responseType: 'blob'
    })
}

export const monitorApi = {
  getZoneSensorData: (zoneId: string) => 
    request.get<any, ZoneSensorData>(`/monitor/zone/${zoneId}/sensor`),
  getAllZoneSensorData: () => 
    request.get<any, ZoneSensorData[]>('/monitor/zone/sensor/all'),
  getWeatherData: () => 
    request.get<any, WeatherData>('/monitor/weather'),
  getHistoricalData: (zoneId: string, sensorType: string, startTime: string, endTime: string) => 
    request.get<any, SensorDataPoint[]>(`/monitor/historical/${zoneId}/${sensorType}`, {
      params: { startTime, endTime }
    })
}
