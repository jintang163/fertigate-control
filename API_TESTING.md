# 水肥一体化智能灌溉控制系统 - API测试文档

## 概述
本文档提供了所有功能模块的API接口测试方法，包括curl命令示例、请求参数说明、响应格式。
用于验证功能链路是否正常工作。

## 基础信息
- **Base URL**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **API JSON**: http://localhost:8080/v3/api-docs

---

## 1. 设备管理模块

### 1.1 获取所有设备状态
```bash
curl -X GET http://localhost:8080/device/status \
  -H "Content-Type: application/json"
```
**预期响应**: 返回所有设备的状态列表，包括在线/离线状态、当前值、离线时长等。

### 1.2 获取在线设备列表
```bash
curl -X GET http://localhost:8080/device/status/online \
  -H "Content-Type: application/json"
```

### 1.3 获取离线设备列表
```bash
curl -X GET http://localhost:8080/device/status/offline \
  -H "Content-Type: application/json"
```

### 1.4 按类型获取设备状态
```bash
curl -X GET "http://localhost:8080/device/status/type/soil" \
  -H "Content-Type: application/json"
```
**可选类型**: soil, weather, valve, fertilizer_pump, flow_sensor, pressure_sensor, gateway

### 1.5 获取设备统计信息
```bash
curl -X GET http://localhost:8080/device/statistics \
  -H "Content-Type: application/json"
```
**预期响应**:
```json
{
  "total": 10,
  "online": 8,
  "offline": 2,
  "onlineRate": 80.0,
  "byType": {
    "soil_sensor": 3,
    "valve": 4,
    "fertilizer_pump": 1,
    "flow_sensor": 1,
    "pressure_sensor": 1
  }
}
```

### 1.6 注册新设备
```bash
curl -X POST http://localhost:8080/device \
  -H "Content-Type: application/json" \
  -d '{
    "deviceCode": "soil-sensor-001",
    "name": "1号灌区土壤湿度传感器",
    "type": "soil",
    "unit": "%",
    "modbusAddress": 1,
    "modbusPort": "COM1"
  }'
```

### 1.7 模拟设备心跳上报（测试在线检测）
```bash
# 先通过MQTT模拟设备心跳，或使用API更新心跳
# 等待60秒后检查设备状态，验证离线检测功能
```

---

## 2. 灌区管理模块

### 2.1 获取所有生育期记录
```bash
curl -X GET http://localhost:8080/growth-stage \
  -H "Content-Type: application/json"
```

### 2.2 获取灌区当前生育期
```bash
curl -X GET "http://localhost:8080/growth-stage/zone/{zoneId}/current" \
  -H "Content-Type: application/json"
```
**说明**: 替换 `{zoneId}` 为实际的灌区UUID。

### 2.3 创建生育期记录
```bash
curl -X POST http://localhost:8080/growth-stage \
  -H "Content-Type: application/json" \
  -d '{
    "growthStage": "苗期",
    "startDate": "2024-03-01",
    "minHumidity": 60.0,
    "maxHumidity": 80.0,
    "optimalEc": 1.5,
    "optimalPh": 6.5,
    "waterRequirement": 20.0,
    "notes": "小麦苗期，需保持土壤湿润"
  }'
```

### 2.4 结束当前生育期
```bash
curl -X PUT "http://localhost:8080/growth-stage/crop/{cropId}/end" \
  -H "Content-Type: application/json"
```

---

## 3. 阈值策略配置模块

### 3.1 获取所有阈值策略
```bash
curl -X GET http://localhost:8080/threshold \
  -H "Content-Type: application/json"
```

### 3.2 获取激活的策略列表
```bash
curl -X GET http://localhost:8080/threshold/active \
  -H "Content-Type: application/json"
```

### 3.3 创建阈值策略
```bash
curl -X POST http://localhost:8080/threshold \
  -H "Content-Type: application/json" \
  -d '{
    "name": "小麦苗期灌溉策略",
    "description": "小麦苗期专用灌溉策略",
    "minHumidity": 50.0,
    "maxHumidity": 80.0,
    "minEc": 1.0,
    "maxEc": 2.5,
    "minPh": 5.5,
    "maxPh": 7.5,
    "minTemperature": 10.0,
    "maxTemperature": 35.0,
    "weatherLinkEnabled": true,
    "avoidRainIrrigation": true,
    "isActive": true,
    "priority": 1
  }'
```

### 3.4 检查阈值（模拟灌溉决策）
```bash
curl -X POST "http://localhost:8080/threshold/check/{zoneId}" \
  -H "Content-Type: application/json" \
  -d '{
    "humidity": 45.0,
    "ec": 1.2,
    "ph": 6.8,
    "temperature": 25.0,
    "windSpeed": 5.0,
    "rainfall": 0.0
  }'
```
**预期响应**:
```json
{
  "withinThresholds": false,
  "shouldIrrigate": true,
  "shouldStopIrrigation": false,
  "optimalEc": 1.5,
  "optimalPh": 6.5
}
```

### 3.5 启用/禁用策略
```bash
curl -X PUT "http://localhost:8080/threshold/{strategyId}/active?active=true" \
  -H "Content-Type: application/json"
```

---

## 4. 分区轮灌调度模块

### 4.1 获取所有轮灌计划
```bash
curl -X GET http://localhost:8080/rotation \
  -H "Content-Type: application/json"
```

### 4.2 创建轮灌计划
```bash
curl -X POST http://localhost:8080/rotation \
  -H "Content-Type: application/json" \
  -d '{
    "name": "1号灌区每日早灌",
    "description": "每天早上6点灌溉1小时",
    "zoneIds": ["zone-uuid-1", "zone-uuid-2"],
    "startTime": "06:00:00",
    "endTime": "07:00:00",
    "duration": 3600,
    "intervalHours": 24,
    "priority": 1,
    "waterAmount": 50.0,
    "irrigationType": "irrigation",
    "isActive": true
  }'
```

### 4.3 自动生成轮灌计划
```bash
curl -X POST "http://localhost:8080/rotation/generate?zoneId={zoneId}&irrigationType=irrigation&priority=1" \
  -H "Content-Type: application/json"
```

### 4.4 立即执行轮灌计划
```bash
curl -X POST "http://localhost:8080/rotation/{scheduleId}/execute" \
  -H "Content-Type: application/json"
```

### 4.5 启用/禁用调度
```bash
curl -X PUT "http://localhost:8080/rotation/{scheduleId}/active?active=true" \
  -H "Content-Type: application/json"
```

---

## 5. 手动/自动切换控制模块

### 5.1 获取控制状态
```bash
curl -X GET http://localhost:8080/irrigation/status \
  -H "Content-Type: application/json"
```
**预期响应**:
```json
{
  "controlMode": "auto",
  "scheduledValveStopsCount": 0,
  "scheduledPumpStopsCount": 0,
  "openValves": 2,
  "runningPumps": 1
}
```

### 5.2 切换控制模式
```bash
curl -X PUT "http://localhost:8080/irrigation/mode?mode=manual" \
  -H "Content-Type: application/json"
```
**模式**: auto (自动), manual (手动)

### 5.3 手动控制阀门（带开度）
```bash
curl -X POST "http://localhost:8080/irrigation/valve/{valveId}/control-with-degree?open=true&reason=测试灌溉&openingDegree=80" \
  -H "Content-Type: application/json"
```
**参数说明**:
- `open`: true开启，false关闭
- `reason`: 操作原因
- `openingDegree`: 阀门开度 0-100%

### 5.4 手动控制施肥泵
```bash
curl -X POST "http://localhost:8080/pump/{pumpId}/control?run=true&reason=测试施肥&openingDegree=50" \
  -H "Content-Type: application/json"
```

### 5.5 紧急停止所有设备
```bash
curl -X POST http://localhost:8080/irrigation/emergency-stop \
  -H "Content-Type: application/json"
```
**重要**: 此命令会立即关闭所有阀门和施肥泵！

---

## 6. 安全联锁模块

### 6.1 获取安全联锁状态
```bash
curl -X GET http://localhost:8080/device/interlock/status \
  -H "Content-Type: application/json"
```
**预期响应**:
```json
{
  "enabled": true,
  "activeInterlocks": 0,
  "activeInterlockKeys": [],
  "lowFlowThreshold": 0.5,
  "lowPressureThreshold": 0.3,
  "communicationTimeoutSeconds": 60
}
```

### 6.2 获取当前激活的联锁
```bash
curl -X GET http://localhost:8080/device/interlock/active \
  -H "Content-Type: application/json"
```

### 6.3 确认并清除联锁
```bash
curl -X POST "http://localhost:8080/device/interlock/{interlockKey}/acknowledge" \
  -H "Content-Type: application/json"
```

### 6.4 启用/禁用安全联锁
```bash
curl -X PUT "http://localhost:8080/device/interlock/enabled?enabled=true" \
  -H "Content-Type: application/json"
```

### 6.5 测试通信中断联锁
```bash
# 步骤1: 注册一个设备并标记为在线
# 步骤2: 等待超过 communicationTimeoutSeconds (默认60秒)
# 步骤3: 检查设备状态，应自动变为离线
# 步骤4: 检查 /device/interlock/active，应出现通信中断联锁
```

### 6.6 测试低流量联锁
```bash
# 步骤1: 打开一个灌区的阀门
# 步骤2: 通过MQTT上报低流量数据（< 0.5 m³/h）
# 步骤3: 等待安全联锁检查（默认10秒）
# 步骤4: 验证阀门是否自动关闭，联锁是否触发
```

---

## 7. 灌肥台账记录模块

### 7.1 获取所有灌肥记录
```bash
curl -X GET "http://localhost:8080/fertigation?startTime=2024-01-01T00:00:00&endTime=2024-12-31T23:59:59" \
  -H "Content-Type: application/json"
```

### 7.2 按灌区获取记录
```bash
curl -X GET "http://localhost:8080/fertigation/zone/{zoneId}" \
  -H "Content-Type: application/json"
```

### 7.3 按执行方式获取记录
```bash
curl -X GET "http://localhost:8080/fertigation/mode/manual" \
  -H "Content-Type: application/json"
```
**可选模式**: auto, manual

### 7.4 按灌溉类型获取记录
```bash
curl -X GET "http://localhost:8080/fertigation/type/fertilization" \
  -H "Content-Type: application/json"
```
**可选类型**: irrigation (灌溉), fertilization (施肥)

### 7.5 获取统计信息
```bash
curl -X GET "http://localhost:8080/fertigation/statistics?startTime=2024-01-01T00:00:00&endTime=2024-12-31T23:59:59&zoneId={zoneId}" \
  -H "Content-Type: application/json"
```
**预期响应**:
```json
{
  "totalRecords": 100,
  "totalWaterAmount": 5000.0,
  "totalFertilizerAmount": 200.0,
  "autoModeCount": 80,
  "manualModeCount": 20,
  "averageDurationMinutes": 45,
  "byZone": {...},
  "byType": {...}
}
```

---

## 8. MQTT 控制指令测试

### 8.1 订阅状态主题
```bash
# 使用MQTT客户端订阅状态上报主题
mosquitto_sub -h localhost -t "fertigate/telemetry/+" -v
```

### 8.2 模拟传感器数据上报
```bash
mosquitto_pub -h localhost -t "fertigate/telemetry/soil-sensor-001" \
  -m '{"humidity": 45.5, "temperature": 22.3, "ec": 1.2, "ph": 6.8, "timestamp": "2024-06-06T10:00:00"}'
```
**验证**: 检查 `/device/status/soil`，应能看到更新的传感器值。

### 8.3 模拟设备心跳
```bash
mosquitto_pub -h localhost -t "fertigate/heartbeat/soil-sensor-001" \
  -m '{"status": "online", "timestamp": "2024-06-06T10:00:00"}'
```
**验证**: 检查设备状态，应保持在线。

### 8.4 监听控制指令下发主题
```bash
# 监听阀门控制指令
mosquitto_sub -h localhost -t "fertigate/valve/command" -v

# 监听施肥泵控制指令
mosquitto_sub -h localhost -t "fertigate/fertilizer-pump/command" -v
```

### 8.5 触发安全联锁告警
```bash
mosquitto_pub -h localhost -t "fertigate/interlock-alert" \
  -m '{
    "interlockType": "low_flow",
    "level": "critical",
    "message": "灌区流量过低",
    "deviceCode": "flow-sensor-001",
    "sensorValue": "0.3",
    "thresholdValue": "0.5",
    "timestamp": "2024-06-06T10:00:00"
  }'
```
**验证**: 检查 `/device/interlock/active`，应能看到新增的联锁记录。

---

## 9. 功能链路完整验证流程

### 9.1 自动灌溉完整链路验证
```bash
# 步骤1: 确保控制模式为自动
curl -X PUT "http://localhost:8080/irrigation/mode?mode=auto"

# 步骤2: 创建或确保阈值策略存在
curl -X POST http://localhost:8080/threshold \
  -H "Content-Type: application/json" \
  -d '{
    "name": "测试策略",
    "minHumidity": 50.0,
    "maxHumidity": 80.0,
    "isActive": true
  }'

# 步骤3: 模拟传感器上报低湿度数据
mosquitto_pub -h localhost -t "fertigate/telemetry/soil-sensor-001" \
  -m '{"humidity": 45.0, "temperature": 25.0}'

# 步骤4: 等待自动灌溉检查（每30秒）
# 步骤5: 检查阀门状态，应自动打开
curl -X GET http://localhost:8080/device/valve

# 步骤6: 检查灌溉记录，应生成新记录
curl -X GET http://localhost:8080/fertigation

# 步骤7: 模拟传感器上报高湿度数据
mosquitto_pub -h localhost -t "fertigate/telemetry/soil-sensor-001" \
  -m '{"humidity": 85.0}'

# 步骤8: 等待检查，阀门应自动关闭
# 步骤9: 验证灌溉记录已完成，用水量已计算
```

### 9.2 轮灌调度完整链路验证
```bash
# 步骤1: 创建轮灌计划
curl -X POST http://localhost:8080/rotation \
  -H "Content-Type: application/json" \
  -d '{
    "name": "测试轮灌",
    "zoneIds": ["zone-uuid-1"],
    "startTime": "00:00:00",
    "duration": 60,
    "intervalHours": 1,
    "isActive": true
  }'

# 步骤2: 设置下次执行时间为当前时间
# 步骤3: 等待轮灌调度检查（每30秒）
# 步骤4: 验证阀门自动打开
# 步骤5: 验证灌溉记录已创建
# 步骤6: 验证60秒后阀门自动关闭
# 步骤7: 验证灌溉记录已完成
```

### 9.3 安全联锁完整链路验证
```bash
# 步骤1: 确保安全联锁已启用
curl -X PUT "http://localhost:8080/device/interlock/enabled?enabled=true"

# 步骤2: 手动打开阀门
curl -X POST "http://localhost:8080/irrigation/valve/{valveId}/control?open=true&reason=测试联锁"

# 步骤3: 模拟低流量数据
mosquitto_pub -h localhost -t "fertigate/telemetry/flow-sensor-001" \
  -m '{"flow": 0.2}'

# 步骤4: 等待安全联锁检查（每10秒）
# 步骤5: 验证阀门自动关闭
# 步骤6: 验证灌溉记录状态为 emergency_stopped
# 步骤7: 验证激活的联锁列表中有记录
# 步骤8: 确认并清除联锁
curl -X POST "http://localhost:8080/device/interlock/low_flow_{zoneId}/acknowledge"
```

---

## 10. 常见问题排查

### 10.1 API无响应
- 检查后端服务是否启动：`curl http://localhost:8080/actuator/health`
- 检查端口配置：默认8080
- 查看应用日志：`tail -f logs/application.log`

### 10.2 MQTT通信失败
- 检查MQTT Broker是否启动
- 检查配置：`application.yml`中的MQTT配置
- 查看MQTT消息日志

### 10.3 设备状态不更新
- 检查心跳超时配置：`control.device-offline-timeout-seconds`
- 检查设备心跳上报频率
- 查看定时任务日志

### 10.4 自动灌溉不执行
- 检查控制模式：应为 auto
- 检查阈值策略是否激活
- 检查传感器数据是否正常上报
- 检查是否触发安全联锁

---

## 11. 测试数据准备脚本

### 11.1 初始化测试数据SQL
```sql
-- 插入测试灌区
INSERT INTO zones (id, name, area, crop_id) 
VALUES ('00000000-0000-0000-0000-000000000001', '1号灌区', 50.0, null);

-- 插入测试设备
INSERT INTO devices (id, device_code, name, type, zone_id, status) 
VALUES 
  ('00000000-0000-0000-0000-000000000002', 'soil-sensor-001', '土壤湿度传感器', 'soil', '00000000-0000-0000-0000-000000000001', 'online'),
  ('00000000-0000-0000-0000-000000000003', 'valve-001', '进水电磁阀', 'valve', '00000000-0000-0000-0000-000000000001', 'online'),
  ('00000000-0000-0000-0000-000000000004', 'flow-sensor-001', '流量传感器', 'flow_sensor', '00000000-0000-0000-0000-000000000001', 'online'),
  ('00000000-0000-0000-0000-000000000005', 'pump-001', '施肥泵', 'fertilizer_pump', '00000000-0000-0000-0000-000000000001', 'online');

-- 插入测试阀门
INSERT INTO valves (id, device_id, zone_id, valve_number, is_open, auto_control)
VALUES ('00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 1, false, true);
```

---

## 12. 性能测试建议

### 12.1 并发测试
```bash
# 使用ab工具进行并发测试
ab -n 1000 -c 100 http://localhost:8080/device/status
```

### 12.2 长时间运行测试
- 启动应用，持续运行72小时
- 监控内存使用、CPU占用
- 验证定时任务是否按时执行
- 检查数据库连接是否正常释放

---

## 附录：所有API端点列表

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 设备管理 | GET | /device | 获取所有设备 |
| 设备管理 | GET | /device/{id} | 获取单个设备 |
| 设备管理 | POST | /device | 创建设备 |
| 设备管理 | PUT | /device/{id} | 更新设备 |
| 设备管理 | DELETE | /device/{id} | 删除设备 |
| 设备管理 | GET | /device/status | 获取所有设备状态 |
| 设备管理 | GET | /device/status/online | 获取在线设备 |
| 设备管理 | GET | /device/status/offline | 获取离线设备 |
| 设备管理 | GET | /device/statistics | 获取设备统计 |
| 设备管理 | GET | /device/interlock/status | 获取联锁状态 |
| 设备管理 | GET | /device/interlock/active | 获取激活联锁 |
| 设备管理 | POST | /device/interlock/{key}/acknowledge | 确认联锁 |
| 生育期 | GET | /growth-stage | 获取所有生育期 |
| 生育期 | POST | /growth-stage | 创建生育期 |
| 生育期 | GET | /growth-stage/zone/{zoneId}/current | 获取灌区当前生育期 |
| 阈值策略 | GET | /threshold | 获取所有策略 |
| 阈值策略 | POST | /threshold | 创建策略 |
| 阈值策略 | POST | /threshold/check/{zoneId} | 检查阈值 |
| 轮灌调度 | GET | /rotation | 获取所有调度 |
| 轮灌调度 | POST | /rotation | 创建调度 |
| 轮灌调度 | POST | /rotation/{id}/execute | 立即执行 |
| 灌溉控制 | GET | /irrigation/status | 获取控制状态 |
| 灌溉控制 | PUT | /irrigation/mode | 切换控制模式 |
| 灌溉控制 | POST | /irrigation/valve/{id}/control | 手动控制阀门 |
| 灌溉控制 | POST | /irrigation/valve/{id}/control-with-degree | 带开度控制 |
| 灌溉控制 | POST | /irrigation/emergency-stop | 紧急停止 |
| 施肥泵 | GET | /pump | 获取所有泵 |
| 施肥泵 | POST | /pump | 创建泵 |
| 施肥泵 | POST | /pump/{id}/control | 手动控制泵 |
| 灌肥记录 | GET | /fertigation | 获取所有记录 |
| 灌肥记录 | GET | /fertigation/statistics | 获取统计信息 |
