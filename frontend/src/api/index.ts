import request from './request'
import type { 
  Device, Valve, Zone, Crop, Alert, 
  IrrigationDecision, IrrigationPlan, IrrigationRecord,
  ControlStatus, SensorDataPoint 
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
  getByLevel: (level: string) => request.get<any, Alert[]>('/alert/level/' + level),
  acknowledge: (id: string) => request.put<any, Alert>(`/alert/${id}/acknowledge`),
  acknowledgeAll: () => request.put<any, string>('/alert/acknowledge-all')
}
