import logging
import random
import time
from datetime import datetime
from typing import Dict, Any, List

from .client import ModbusClient

logger = logging.getLogger(__name__)


class DataCollector:
    def __init__(self, modbus_config: Dict[str, Any], devices: List[Dict[str, Any]], simulate: bool = False):
        self.devices = devices
        self.simulate = simulate
        self.modbus_client = None
        
        if not simulate:
            self.modbus_client = ModbusClient(modbus_config)
        else:
            logger.info("Running in simulation mode - no actual Modbus connection")

    def _simulate_sensor_data(self, device: Dict[str, Any]) -> Dict[str, Any]:
        sensor_data = {}
        device_type = device.get('type', '')
        
        if device_type == 'soil':
            base_humidity = 60 + random.uniform(-5, 5)
            base_ec = 1.8 + random.uniform(-0.2, 0.2)
            base_ph = 6.5 + random.uniform(-0.1, 0.1)
            base_temp = 25 + random.uniform(-2, 2)
            
            sensor_data = {
                'humidity': round(base_humidity, 2),
                'ec': round(base_ec, 3),
                'ph': round(base_ph, 2),
                'temperature': round(base_temp, 1)
            }
        elif device_type == 'weather':
            base_temp = 22 + random.uniform(-3, 3)
            base_humidity = 65 + random.uniform(-5, 5)
            base_light = 35000 + random.uniform(-5000, 5000)
            base_rainfall = random.uniform(0, 2)
            base_wind = 2 + random.uniform(-1, 1)
            
            sensor_data = {
                'air_temperature': round(base_temp, 1),
                'air_humidity': round(base_humidity, 1),
                'light': round(base_light, 0),
                'rainfall': round(base_rainfall, 1),
                'wind_speed': round(base_wind, 1)
            }
        elif device_type == 'valve':
            sensor_data = {
                'status': random.choice([0, 1]),
                'is_open': random.choice([False, True])
            }
        
        return sensor_data

    def _read_sensor_registers(self, device: Dict[str, Any]) -> Dict[str, Any]:
        if self.simulate:
            return self._simulate_sensor_data(device)

        sensor_data = {}
        unit_id = device['modbus_address']
        registers = device.get('registers', {})

        for sensor_name, reg_config in registers.items():
            try:
                address = reg_config['address']
                count = reg_config['count']
                scale = reg_config.get('scale', 1.0)
                data_type = reg_config.get('data_type', 'uint32')

                raw_values = self.modbus_client.read_holding_registers(
                    address=address,
                    count=count,
                    unit_id=unit_id
                )

                if raw_values:
                    value = self.modbus_client.decode_value(raw_values, data_type, scale)
                    sensor_data[sensor_name] = value
                else:
                    logger.warning(f"Failed to read {sensor_name} from {device['device_code']}")
                    sensor_data[sensor_name] = None
            except Exception as e:
                logger.error(f"Error reading {sensor_name} from {device['device_code']}: {e}")
                sensor_data[sensor_name] = None

        return sensor_data

    def _read_valve_status(self, device: Dict[str, Any]) -> Dict[str, Any]:
        if self.simulate:
            return self._simulate_sensor_data(device)

        unit_id = device['modbus_address']
        status_reg = device.get('status_register', {})
        
        status_data = {}
        
        if status_reg:
            try:
                raw_values = self.modbus_client.read_holding_registers(
                    address=status_reg['address'],
                    count=status_reg['count'],
                    unit_id=unit_id
                )
                
                if raw_values:
                    status = raw_values[0]
                    status_data['status'] = status
                    status_data['is_open'] = status == 1
                else:
                    status_data['status'] = None
                    status_data['is_open'] = None
            except Exception as e:
                logger.error(f"Error reading valve status from {device['device_code']}: {e}")
                status_data['status'] = None
                status_data['is_open'] = None
        
        return status_data

    def collect_device_data(self, device: Dict[str, Any]) -> Dict[str, Any]:
        device_type = device.get('type', '')
        
        if device_type in ['soil', 'weather']:
            sensor_values = self._read_sensor_registers(device)
        elif device_type == 'valve':
            sensor_values = self._read_valve_status(device)
        else:
            sensor_values = {}

        return {
            'device_code': device['device_code'],
            'device_name': device['name'],
            'device_type': device_type,
            'zone': device.get('zone', ''),
            'gateway_id': 'GW-001',
            'timestamp': datetime.utcnow().isoformat() + 'Z',
            'values': sensor_values
        }

    def collect_all(self) -> List[Dict[str, Any]]:
        all_data = []
        
        for device in self.devices:
            try:
                device_data = self.collect_device_data(device)
                all_data.append(device_data)
                logger.debug(f"Collected data from {device['device_code']}: {device_data['values']}")
            except Exception as e:
                logger.error(f"Error collecting data from {device.get('device_code', 'unknown')}: {e}")
        
        return all_data

    def control_valve(self, device: Dict[str, Any], open_valve: bool) -> bool:
        if self.simulate:
            logger.info(f"[SIMULATION] Valve {device['device_code']} {'opened' if open_valve else 'closed'}")
            return True

        unit_id = device['modbus_address']
        control_reg = device.get('control_register', {})
        
        if not control_reg:
            logger.error(f"No control register defined for {device['device_code']}")
            return False

        try:
            value = 1 if open_valve else 0
            success = self.modbus_client.write_register(
                address=control_reg['address'],
                value=value,
                unit_id=unit_id
            )
            
            if success:
                logger.info(f"Valve {device['device_code']} {'opened' if open_valve else 'closed'} successfully")
            else:
                logger.error(f"Failed to control valve {device['device_code']}")
            
            return success
        except Exception as e:
            logger.error(f"Error controlling valve {device['device_code']}: {e}")
            return False

    def close(self) -> None:
        if self.modbus_client:
            self.modbus_client.close()
