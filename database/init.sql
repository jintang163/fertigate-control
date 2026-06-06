-- 创建扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 作物表
CREATE TABLE crops (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    variety VARCHAR(100),
    growth_stage VARCHAR(50) NOT NULL DEFAULT 'seedling',
    min_humidity DECIMAL(5,2) NOT NULL DEFAULT 50.00,
    max_humidity DECIMAL(5,2) NOT NULL DEFAULT 80.00,
    optimal_ec DECIMAL(5,2) DEFAULT 1.80,
    optimal_ph DECIMAL(5,2) DEFAULT 6.50,
    water_requirement DECIMAL(8,2) DEFAULT 0.00,
    planting_date DATE,
    expected_harvest_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 区域表
CREATE TABLE zones (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    crop_id UUID REFERENCES crops(id),
    area DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 设备表
CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    zone_id UUID REFERENCES zones(id),
    modbus_address INTEGER,
    modbus_port VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'offline',
    last_heartbeat TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 传感器配置表
CREATE TABLE sensor_configs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID REFERENCES devices(id) ON DELETE CASCADE,
    sensor_type VARCHAR(50) NOT NULL,
    unit VARCHAR(20),
    min_threshold DECIMAL(10,2),
    max_threshold DECIMAL(10,2),
    warning_enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 电磁阀表
CREATE TABLE valves (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID REFERENCES devices(id) ON DELETE CASCADE,
    zone_id UUID REFERENCES zones(id),
    valve_number INTEGER,
    flow_rate DECIMAL(8,2) DEFAULT 0.00,
    is_open BOOLEAN DEFAULT FALSE,
    auto_control BOOLEAN DEFAULT TRUE,
    last_operation TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 灌溉计划表
CREATE TABLE irrigation_plans (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    zone_id UUID REFERENCES zones(id),
    crop_id UUID REFERENCES crops(id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    start_time TIME,
    end_time TIME,
    duration INTEGER,
    interval_hours INTEGER,
    water_amount DECIMAL(8,2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 灌溉记录表
CREATE TABLE irrigation_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    zone_id UUID REFERENCES zones(id),
    valve_id UUID REFERENCES valves(id),
    plan_id UUID REFERENCES irrigation_plans(id),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    water_amount DECIMAL(8,2) DEFAULT 0.00,
    reason VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 告警记录表
CREATE TABLE alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID REFERENCES devices(id),
    alert_type VARCHAR(50) NOT NULL,
    level VARCHAR(20) NOT NULL DEFAULT 'warning',
    message TEXT,
    sensor_value DECIMAL(10,2),
    threshold_value DECIMAL(10,2),
    is_acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 系统配置表
CREATE TABLE system_configs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    config_key VARCHAR(100) UNIQUE NOT NULL,
    config_value TEXT,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入默认数据
INSERT INTO crops (name, variety, growth_stage, min_humidity, max_humidity, optimal_ec, optimal_ph)
VALUES 
('番茄', '红美玉', 'seedling', 60.00, 80.00, 2.00, 6.20),
('黄瓜', '津优1号', 'vegetative', 55.00, 75.00, 1.80, 6.00),
('草莓', '红颜', 'flowering', 50.00, 70.00, 1.50, 5.80);

INSERT INTO zones (name, description, area)
VALUES 
('A区 - 温室大棚1', '主要种植番茄', 500.00),
('B区 - 温室大棚2', '主要种植黄瓜', 480.00),
('C区 - 露天种植区', '草莓种植区', 800.00);

INSERT INTO system_configs (config_key, config_value, description)
VALUES 
('mqtt_broker', 'tcp://localhost:1883', 'MQTT broker地址'),
('data_collection_interval', '30', '数据采集间隔(秒)'),
('control_mode', 'auto', '控制模式: auto/manual'),
('emergency_stop', 'false', '紧急停止状态'),
('default_irrigation_duration', '1800', '默认灌溉时长(秒)');

-- 施肥泵表
CREATE TABLE fertilizer_pumps (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID REFERENCES devices(id) ON DELETE CASCADE,
    zone_id UUID REFERENCES zones(id),
    pump_number INTEGER,
    flow_rate DECIMAL(8,2) DEFAULT 0.00,
    max_pressure DECIMAL(8,2) DEFAULT 10.00,
    current_pressure DECIMAL(8,2) DEFAULT 0.00,
    is_running BOOLEAN DEFAULT FALSE,
    auto_control BOOLEAN DEFAULT TRUE,
    opening_degree INTEGER DEFAULT 0,
    last_operation TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 生育期记录表
CREATE TABLE growth_stage_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    crop_id UUID REFERENCES crops(id) ON DELETE CASCADE,
    zone_id UUID REFERENCES zones(id),
    growth_stage VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    notes TEXT,
    min_humidity DECIMAL(5,2),
    max_humidity DECIMAL(5,2),
    optimal_ec DECIMAL(5,2),
    optimal_ph DECIMAL(5,2),
    water_requirement DECIMAL(8,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 阈值策略表
CREATE TABLE threshold_strategies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    zone_id UUID REFERENCES zones(id),
    crop_id UUID REFERENCES crops(id),
    min_humidity DECIMAL(5,2) NOT NULL DEFAULT 50.00,
    max_humidity DECIMAL(5,2) NOT NULL DEFAULT 80.00,
    min_ec DECIMAL(5,2) DEFAULT 1.00,
    max_ec DECIMAL(5,2) DEFAULT 2.50,
    min_ph DECIMAL(5,2) DEFAULT 5.50,
    max_ph DECIMAL(5,2) DEFAULT 7.50,
    min_temperature DECIMAL(5,2) DEFAULT 10.00,
    max_temperature DECIMAL(5,2) DEFAULT 35.00,
    max_wind_speed DECIMAL(5,2) DEFAULT 15.00,
    min_rainfall DECIMAL(5,2) DEFAULT 0.00,
    weather_link_enabled BOOLEAN DEFAULT FALSE,
    avoid_rain_irrigation BOOLEAN DEFAULT TRUE,
    high_temp_irrigation BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 轮灌调度表
CREATE TABLE rotation_schedules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    strategy_id UUID REFERENCES threshold_strategies(id),
    zone_ids TEXT,
    start_time TIME NOT NULL,
    end_time TIME,
    duration INTEGER NOT NULL,
    interval_hours INTEGER DEFAULT 24,
    priority INTEGER DEFAULT 0,
    water_amount DECIMAL(8,2),
    fertilizer_amount DECIMAL(8,2),
    irrigation_type VARCHAR(20) DEFAULT 'irrigation',
    is_active BOOLEAN DEFAULT TRUE,
    last_execution TIMESTAMP,
    next_execution TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 扩展阀门表，添加开度字段
ALTER TABLE valves ADD COLUMN IF NOT EXISTS opening_degree INTEGER DEFAULT 100;

-- 扩展灌溉记录表，支持施肥记录和执行方式
ALTER TABLE irrigation_records ADD COLUMN IF NOT EXISTS execution_mode VARCHAR(20) DEFAULT 'auto';
ALTER TABLE irrigation_records ADD COLUMN IF NOT EXISTS irrigation_type VARCHAR(20) DEFAULT 'irrigation';
ALTER TABLE irrigation_records ADD COLUMN IF NOT EXISTS fertilizer_amount DECIMAL(8,2) DEFAULT 0.00;
ALTER TABLE irrigation_records ADD COLUMN IF NOT EXISTS fertilizer_type VARCHAR(50);
ALTER TABLE irrigation_records ADD COLUMN IF NOT EXISTS average_ec DECIMAL(5,2);
ALTER TABLE irrigation_records ADD COLUMN IF NOT EXISTS average_ph DECIMAL(5,2);
ALTER TABLE irrigation_records ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'completed';

-- 扩展设备表，添加更多状态字段
ALTER TABLE devices ADD COLUMN IF NOT EXISTS current_value DECIMAL(10,2);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS unit VARCHAR(20);

-- 创建索引
CREATE INDEX idx_devices_zone ON devices(zone_id);
CREATE INDEX idx_sensor_configs_device ON sensor_configs(device_id);
CREATE INDEX idx_valves_zone ON valves(zone_id);
CREATE INDEX idx_irrigation_records_time ON irrigation_records(start_time);
CREATE INDEX idx_alerts_created ON alerts(created_at);
CREATE INDEX idx_fertilizer_pumps_zone ON fertilizer_pumps(zone_id);
CREATE INDEX idx_growth_stage_records_crop ON growth_stage_records(crop_id);
CREATE INDEX idx_threshold_strategies_zone ON threshold_strategies(zone_id);
CREATE INDEX idx_rotation_schedules_time ON rotation_schedules(next_execution);
