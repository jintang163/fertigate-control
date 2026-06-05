# 水肥一体化智能灌溉控制系统

## 项目概述

基于物联网的水肥一体化智能灌溉系统，通过Modbus/RS485采集田间传感器数据，结合土壤湿度阈值和作物生长模型，智能控制电磁阀启停，实现精准灌溉。

## 系统架构

```
                        ┌───────────────────────────────────────────────────┐
                        │                   PC 前端                        │
                        │  (TypeScript + Vite + Ant Design Vue4 + ECharts) │
                        └─────────────────────────┬─────────────────────────┘
                                                  │ HTTP/REST API
                        ┌─────────────────────────▼─────────────────────────┐
                        │                 后端服务 (Spring Boot)            │
                        │  MQTT接入 | 数据处理 | 自动控制 | 业务逻辑         │
                        │  PostgreSQL(关系数据) | InfluxDB(时序数据)        │
                        └─────────────────────────┬─────────────────────────┘
                                                  │ MQTT
                        ┌─────────────────────────▼─────────────────────────┐
                        │                 边缘网关 (Python)                  │
                        │  Modbus采集 | 协议转换 | 本地缓存 | 联锁逻辑       │
                        └─────────────────────────┬─────────────────────────┘
                                                  │ Modbus/RS485
                        ┌─────────────────────────▼─────────────────────────┐
                        │               田间设备                              │
                        │  土壤传感器 | 气象传感器 | 电磁阀                   │
                        └───────────────────────────────────────────────────┘
```

## 目录结构

```
fertigate-control/
├── edge-gateway/                    # 边缘网关 (Python)
│   ├── modbus/                       # Modbus/RS485 采集模块
│   │   ├── client.py                 # Modbus RTU/TCP 客户端
│   │   └── collector.py              # 数据采集器（支持仿真模式）
│   ├── mqtt/                         # MQTT 通信模块
│   │   └── client.py                 # MQTT 客户端封装
│   ├── cache/                        # 断网缓存模块
│   │   └── manager.py                # SQLite 缓存管理
│   ├── interlock/                    # 本地联锁逻辑
│   │   └── manager.py                # 超限自动关阀、紧急停止
│   ├── config.yaml                   # 网关配置
│   ├── requirements.txt              # Python 依赖
│   └── main.py                       # 主程序入口
├── backend/                          # 后端服务 (Spring Boot 3.2)
│   ├── src/main/java/com/fertigate/
│   │   ├── config/                   # 配置类（InfluxDB、MQTT、跨域）
│   │   ├── entity/                   # JPA 实体类
│   │   ├── dto/                      # 数据传输对象
│   │   ├── repository/               # Spring Data JPA 接口
│   │   ├── service/                  # 业务逻辑层
│   │   │   ├── InfluxDBService.java         # InfluxDB 数据操作
│   │   │   ├── MqttMessageHandler.java      # MQTT 消息处理
│   │   │   ├── CropGrowthModelService.java  # 作物生长模型
│   │   │   └── IrrigationControlService.java # 灌溉控制核心逻辑
│   │   └── controller/               # REST API 控制器
│   └── pom.xml                       # Maven 依赖
├── frontend/                         # 前端 (Vue3 + Vite)
│   ├── src/
│   │   ├── views/                    # 页面组件
│   │   │   ├── Dashboard.vue         # 监控总览
│   │   │   ├── RealtimeMonitor.vue   # 实时监控
│   │   │   ├── DeviceManagement.vue  # 设备管理
│   │   │   ├── IrrigationControl.vue # 灌溉控制
│   │   │   ├── CropManagement.vue    # 作物管理
│   │   │   ├── ZoneManagement.vue    # 区域管理
│   │   │   ├── AlertCenter.vue       # 告警中心
│   │   │   ├── IrrigationRecords.vue # 灌溉记录
│   │   │   └── SystemSettings.vue    # 系统设置
│   │   ├── api/                      # API 调用封装
│   │   ├── stores/                   # Pinia 状态管理
│   │   ├── router/                   # Vue Router 路由
│   │   ├── types/                    # TypeScript 类型定义
│   │   ├── App.vue                   # 根组件
│   │   ├── main.ts                   # 入口文件
│   │   └── style.css                 # 全局样式
│   ├── package.json                  # NPM 依赖
│   └── vite.config.ts                # Vite 配置
└── database/                         # 数据库脚本
    ├── docker-compose.yml            # PostgreSQL + InfluxDB + EMQX
    └── init.sql                      # 初始化表结构
```

## 技术栈

| 层级 | 技术 |
|------|------|
| **前端** | TypeScript 5 + Vite 5 + Vue 3.4 + Ant Design Vue 4 + ECharts 5 + Pinia + Vue Router 4 |
| **后端** | Spring Boot 3.2 + Spring Data JPA + HiveMQ MQTT Client + InfluxDB Java Client |
| **边缘网关** | Python 3.10 + pymodbus + paho-mqtt + sqlitedict + apscheduler |
| **数据库** | PostgreSQL 15 (关系数据) + InfluxDB 2.7 (时序数据) |
| **消息中间件** | EMQX (MQTT Broker) |
| **设备协议** | Modbus RTU/RS485, Modbus TCP |

## 快速启动

### 前置要求

- Docker & Docker Compose
- JDK 17+
- Maven 3.8+
- Python 3.10+
- Node.js 18+

### 1. 启动基础设施（数据库 + MQTT Broker）

```bash
cd database
docker-compose up -d
```

访问以下地址验证：
- InfluxDB UI: http://localhost:8086 (admin/fertigate123)
- EMQX Dashboard: http://localhost:18083 (admin/public)
- PostgreSQL: localhost:5432 (admin/fertigate123)

### 2. 初始化数据库

数据库初始化脚本会自动执行，创建所有表和初始数据。

### 3. 启动边缘网关（仿真模式）

```bash
cd edge-gateway
pip install -r requirements.txt
python main.py --simulate
```

> `--simulate` 参数启动仿真模式，无需真实硬件即可生成模拟数据。

### 4. 启动后端服务

```bash
cd backend
mvn clean package -DskipTests
mvn spring-boot:run
```

后端服务启动在 http://localhost:8080

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端启动在 http://localhost:5173

## 功能模块详解

### 一、田间感知与边缘采集

#### 1. 传感器接入
- **土壤传感器**: 湿度(%)、EC(mS/cm)、pH
- **气象传感器**: 空气温度(°C)、空气湿度(%)、光照(Lux)、降雨量(mm)
- **通信协议**: Modbus RTU over RS485

#### 2. 边缘网关功能
- **数据采集**: 定时轮询传感器数据，5秒/次（可配置）
- **协议转换**: Modbus 数据 → JSON → MQTT
- **断网缓存**: 网络断开时使用 SQLite 缓存，恢复后自动同步
- **本地联锁**:
  - 土壤湿度 < 30% → 自动开阀
  - 土壤湿度 > 85% → 自动关阀
  - 紧急停止 → 立即关闭所有阀门
  - 传感器超时 → 告警并安全关阀

### 二、后端自动控制逻辑

#### 1. 双阈值控制
```
灌溉触发: 湿度 < 最小阈值(50%)
灌溉停止: 湿度 > 最大阈值(80%)
```

#### 2. 作物生长模型
| 生长阶段 | 需水系数 | 最适湿度范围 |
|---------|---------|-------------|
| 苗期 SEEDLING | 0.6 | 50-60% |
| 营养生长期 VEGETATIVE | 0.8 | 60-70% |
| 开花期 FLOWERING | 1.0 | 70-80% |
| 结果期 FRUITING | 1.0 | 70-80% |
| 成熟期 RIPENING | 0.7 | 50-60% |

#### 3. 气象修正因子
- 温度修正: 每高5°C 增加10%灌溉量
- 降雨修正: 24h内降雨 > 10mm 取消灌溉
- 光照修正: 光照强度修正系数

#### 4. 灌溉决策公式
```
灌溉需求 = (目标湿度 - 当前湿度) × 面积 × 土壤系数 × 生长阶段系数 × 气象修正
```

### 三、前端功能页面

| 页面 | 功能说明 |
|------|---------|
| **监控总览** | 设备统计、实时趋势、设备分布、最新告警、灌溉决策 |
| **实时监控** | 传感器实时数据、趋势曲线、仪表盘、EC/pH监测 |
| **设备管理** | 设备增删改查、状态监控、Modbus配置 |
| **灌溉控制** | 手动/自动模式切换、阀门控制、灌溉计划 |
| **作物管理** | 作物信息、生长阶段、灌溉参数配置 |
| **区域管理** | 区域划分、面积、阈值配置、灌溉模式 |
| **告警中心** | 告警分级、处理、批量操作、通知设置 |
| **灌溉记录** | 历史记录查询、详情、导出 |
| **系统设置** | 通用配置、通知、MQTT、系统信息 |

## API 接口概览

| 模块 | 接口 | 说明 |
|------|------|------|
| **传感器** | `GET /api/sensor/latest/{deviceCode}` | 获取最新数据 |
| | `GET /api/sensor/history` | 查询历史数据 |
| **设备** | `GET /api/device` | 设备列表 |
| | `POST /api/device` | 创建设备 |
| | `PUT /api/device/{id}` | 更新设备 |
| **灌溉** | `POST /api/irrigation/valve/{id}` | 控制阀门 |
| | `POST /api/irrigation/mode` | 设置控制模式 |
| | `POST /api/irrigation/emergency-stop` | 紧急停止 |
| | `GET /api/irrigation/decisions` | 灌溉决策 |
| **作物** | `CRUD /api/crop` | 作物管理 |
| **区域** | `CRUD /api/zone` | 区域管理 |
| **告警** | `GET /api/alert` | 告警列表 |
| | `POST /api/alert/{id}/acknowledge` | 处理告警 |
| **仪表盘** | `GET /api/dashboard/overview` | 概览数据 |

## MQTT 主题规范

| 主题 | 方向 | 说明 |
|------|------|------|
| `fertigate/sensor/data` | 网关 → 后端 | 传感器数据上报 |
| `fertigate/device/status` | 网关 → 后端 | 设备状态上报 |
| `fertigate/alert` | 网关 → 后端 | 告警上报 |
| `fertigate/gateway/heartbeat` | 网关 → 后端 | 网关心跳 |
| `fertigate/command/valve` | 后端 → 网关 | 阀门控制命令 |
| `fertigate/command/config` | 后端 → 网关 | 配置更新 |

## 核心控制流程

```
1. 边缘网关采集传感器数据 (Modbus)
2. 网关本地联锁检查 (超限即时关阀)
3. 数据通过 MQTT 上报后端
4. 后端写入 InfluxDB (时序数据)
5. 定时调度任务检查各区域灌溉需求:
   ├─ 读取最新传感器数据
   ├─ 查询作物生长阶段
   ├─ 计算气象修正因子
   ├─ 分析灌溉需求
   └─ 生成灌溉决策
6. 如需灌溉:
   ├─ 记录灌溉计划
   ├─ 发送 MQTT 阀门开启命令
   └─ 网关执行 Modbus 写线圈操作
7. 湿度达到上限后自动关阀
8. 记录灌溉历史
```

## 仿真模式说明

系统支持完整的仿真模式，无需真实硬件即可测试全部功能：

1. 边缘网关 `--simulate` 参数启动
2. 自动生成模拟传感器数据（包含随机波动）
3. 模拟设备状态变化
4. 支持手动控制阀门
5. 触发阈值告警

## 配置参数说明

### 边缘网关配置 (edge-gateway/config.yaml)
```yaml
mqtt:
  host: localhost
  port: 1883
  
modbus:
  serial_port: /dev/ttyUSB0  # Windows: COM3
  baudrate: 9600
  
collection:
  interval_seconds: 5
  
interlock:
  min_humidity: 30
  max_humidity: 85
```

### 后端配置 (application.yml)
```yaml
control:
  check-interval-seconds: 30  # 灌溉决策检查间隔
  default-min-humidity: 50
  default-max-humidity: 80
```

## 安全注意事项

1. **紧急停止按钮**: 前端右上角红色按钮，立即关闭所有阀门
2. **本地联锁优先**: 边缘端联锁逻辑优先级高于云端控制
3. **超时保护**: 传感器数据超时30秒自动进入安全模式
4. **最大灌溉时长**: 单轮灌溉最大60分钟（可配置）
5. **操作审计**: 所有手动操作均记录日志

## 故障排查

| 问题 | 可能原因 | 解决方法 |
|------|---------|---------|
| 网关连接失败 | MQTT 服务未启动 | 检查 docker ps，确认 EMQX 运行 |
| 无传感器数据 | Modbus 串口错误 | 检查串口配置，或使用仿真模式 |
| 自动控制不生效 | 控制模式为手动 | 在灌溉控制页面切换为自动 |
| 数据不上报 | 断网缓存中 | 检查网络连接，恢复后自动同步 |

## 扩展开发

- 支持更多传感器类型：添加 Modbus 寄存器映射
- 接入水肥一体机：扩展 Modbus 控制线圈
- AI 预测模型：接入 LSTM 土壤湿度预测
- 移动端 APP：使用 UniApp 开发跨端应用

## License

© 2024 Fertigate Intelligent Irrigation Control System
