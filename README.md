# 水肥一体化智能灌溉控制系统

## 项目架构

```
fertigate-control/
├── edge-gateway/          # 边缘网关 (Python)
│   ├── modbus/            # Modbus/RS485 采集模块
│   ├── mqtt/              # MQTT 通信模块
│   ├── cache/             # 断网缓存模块
│   └── interlock/         # 本地联锁逻辑
├── backend/               # 后端服务 (Spring Boot)
│   ├── mqtt/              # MQTT 接入
│   ├── control/           # 自动控制逻辑
│   ├── repository/        # InfluxDB + PostgreSQL
│   └── api/               # REST API
├── frontend/              # 前端 (Vue3 + Vite)
│   ├── views/             # 页面组件
│   ├── components/        # 公共组件
│   └── api/               # API 调用
└── database/              # 数据库脚本
```

## 技术栈

- **前端**: TypeScript + Vite + Ant Design Vue4 + ECharts
- **后端**: Spring Boot + MQTT + Python
- **设备端**: Modbus/RS485
- **数据库**: InfluxDB (时序数据) + PostgreSQL (关系数据)

## 快速启动

### 1. 启动基础设施

```bash
cd database
docker-compose up -d
```

### 2. 启动边缘网关

```bash
cd edge-gateway
pip install -r requirements.txt
python main.py
```

### 3. 启动后端服务

```bash
cd backend
mvn spring-boot:run
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

## 功能模块

### 田间感知与边缘采集
- 土壤传感器：湿度、EC、pH
- 气象传感器：温度、湿度、光照、降雨
- Modbus/RS485 协议接入
- 边缘网关本地数据采集、协议转换
- 断网缓存机制
- 本地联锁逻辑（超限即时关闭阀门）

### 自动控制逻辑
- 基于土壤湿度阈值控制
- 基于作物生长模型的智能决策
- 电磁阀启停控制
- 手动/自动模式切换
