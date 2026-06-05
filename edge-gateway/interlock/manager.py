import logging
from datetime import datetime
from typing import Dict, Any, List, Tuple, Optional

logger = logging.getLogger(__name__)


class InterlockManager:
    def __init__(self, config: Dict[str, Any], devices: List[Dict[str, Any]]):
        self.config = config
        self.devices = devices
        self.enabled = config.get('enabled', True)
        self.emergency_stop = False
        self.valve_status = {}
        
        self._init_valve_status()
        self._load_thresholds()

    def _init_valve_status(self) -> None:
        for device in self.devices:
            if device.get('type') == 'valve':
                self.valve_status[device['device_code']] = {
                    'is_open': False,
                    'last_change': None,
                    'interlock_reason': None
                }

    def _load_thresholds(self) -> None:
        self.min_soil_humidity = self.config.get('min_soil_humidity', 30)
        self.max_soil_humidity = self.config.get('max_soil_humidity', 90)
        self.max_ec = self.config.get('max_ec', 5.0)
        self.min_ph = self.config.get('min_ph', 4.0)
        self.max_ph = self.config.get('max_ph', 9.0)
        self.emergency_pressure = self.config.get('emergency_pressure', 8.0)

    def check_sensor_thresholds(self, sensor_data: Dict[str, Any]) -> Tuple[bool, List[Dict[str, Any]]]:
        if not self.enabled:
            return True, []

        alerts = []
        is_safe = True
        values = sensor_data.get('values', {})
        device_type = sensor_data.get('device_type', '')
        device_code = sensor_data.get('device_code', '')

        if device_type == 'soil':
            humidity = values.get('humidity')
            ec = values.get('ec')
            ph = values.get('ph')

            if humidity is not None:
                if humidity < self.min_soil_humidity:
                    is_safe = False
                    alerts.append({
                        'device_code': device_code,
                        'sensor_type': 'humidity',
                        'value': humidity,
                        'threshold': self.min_soil_humidity,
                        'level': 'warning',
                        'message': f'土壤湿度过低: {humidity}% < {self.min_soil_humidity}%',
                        'action': 'start_irrigation'
                    })
                elif humidity > self.max_soil_humidity:
                    is_safe = False
                    alerts.append({
                        'device_code': device_code,
                        'sensor_type': 'humidity',
                        'value': humidity,
                        'threshold': self.max_soil_humidity,
                        'level': 'critical',
                        'message': f'土壤湿度过高: {humidity}% > {self.max_soil_humidity}%',
                        'action': 'stop_irrigation'
                    })

            if ec is not None and ec > self.max_ec:
                is_safe = False
                alerts.append({
                    'device_code': device_code,
                    'sensor_type': 'ec',
                    'value': ec,
                    'threshold': self.max_ec,
                    'level': 'warning',
                    'message': f'EC值过高: {ec} mS/cm > {self.max_ec} mS/cm',
                    'action': 'reduce_fertilizer'
                })

            if ph is not None:
                if ph < self.min_ph:
                    is_safe = False
                    alerts.append({
                        'device_code': device_code,
                        'sensor_type': 'ph',
                        'value': ph,
                        'threshold': self.min_ph,
                        'level': 'warning',
                        'message': f'pH值过低: {ph} < {self.min_ph}',
                        'action': 'alert'
                    })
                elif ph > self.max_ph:
                    is_safe = False
                    alerts.append({
                        'device_code': device_code,
                        'sensor_type': 'ph',
                        'value': ph,
                        'threshold': self.max_ph,
                        'level': 'warning',
                        'message': f'pH值过高: {ph} > {self.max_ph}',
                        'action': 'alert'
                    })

        return is_safe, alerts

    def check_weather_conditions(self, weather_data: Dict[str, Any]) -> Tuple[bool, List[Dict[str, Any]]]:
        if not self.enabled:
            return True, []

        alerts = []
        is_safe = True
        values = weather_data.get('values', {})
        device_code = weather_data.get('device_code', '')

        rainfall = values.get('rainfall')
        wind_speed = values.get('wind_speed')

        if rainfall is not None and rainfall > 10:
            is_safe = False
            alerts.append({
                'device_code': device_code,
                'sensor_type': 'rainfall',
                'value': rainfall,
                'threshold': 10,
                'level': 'warning',
                'message': f'降雨量过大: {rainfall}mm，建议停止灌溉',
                'action': 'stop_irrigation'
            })

        if wind_speed is not None and wind_speed > 10:
            alerts.append({
                'device_code': device_code,
                'sensor_type': 'wind_speed',
                'value': wind_speed,
                'threshold': 10,
                'level': 'info',
                'message': f'风速较大: {wind_speed}m/s，请注意灌溉均匀性',
                'action': 'alert'
            })

        return is_safe, alerts

    def process_sensor_data(self, sensor_data_list: List[Dict[str, Any]]) -> Dict[str, Any]:
        result = {
            'emergency_stop': self.emergency_stop,
            'alerts': [],
            'valve_commands': [],
            'is_safe': True
        }

        if not self.enabled:
            return result

        for sensor_data in sensor_data_list:
            device_type = sensor_data.get('device_type', '')
            
            if device_type in ['soil', 'weather']:
                is_safe, alerts = self.check_sensor_thresholds(sensor_data)
                if not is_safe:
                    result['is_safe'] = False
                result['alerts'].extend(alerts)

                for alert in alerts:
                    action = alert.get('action')
                    zone = sensor_data.get('zone', '')
                    
                    if action == 'stop_irrigation':
                        valve_cmds = self._create_valve_commands(zone, False, alert['message'])
                        result['valve_commands'].extend(valve_cmds)
                    elif action == 'start_irrigation':
                        if not self.emergency_stop:
                            valve_cmds = self._create_valve_commands(zone, True, alert['message'])
                            result['valve_commands'].extend(valve_cmds)

        return result

    def _create_valve_commands(self, zone: str, open_valve: bool, reason: str) -> List[Dict[str, Any]]:
        commands = []
        
        for device in self.devices:
            if device.get('type') == 'valve' and device.get('zone') == zone:
                device_code = device['device_code']
                
                current_status = self.valve_status.get(device_code, {})
                current_open = current_status.get('is_open', False)
                
                if current_open != open_valve:
                    commands.append({
                        'device_code': device_code,
                        'open': open_valve,
                        'reason': reason,
                        'zone': zone,
                        'timestamp': datetime.utcnow().isoformat() + 'Z'
                    })
                    
                    self.valve_status[device_code] = {
                        'is_open': open_valve,
                        'last_change': datetime.utcnow(),
                        'interlock_reason': reason
                    }
                    
                    logger.warning(f"Interlock: Valve {device_code} {'opening' if open_valve else 'closing'} - {reason}")
        
        return commands

    def update_valve_status(self, device_code: str, is_open: bool) -> None:
        if device_code in self.valve_status:
            self.valve_status[device_code]['is_open'] = is_open
            self.valve_status[device_code]['last_change'] = datetime.utcnow()

    def set_emergency_stop(self, stop: bool) -> List[Dict[str, Any]]:
        self.emergency_stop = stop
        commands = []
        
        if stop:
            logger.critical("EMERGENCY STOP ACTIVATED - Closing all valves")
            
            for device in self.devices:
                if device.get('type') == 'valve':
                    device_code = device['device_code']
                    commands.append({
                        'device_code': device_code,
                        'open': False,
                        'reason': 'Emergency Stop',
                        'zone': device.get('zone', ''),
                        'timestamp': datetime.utcnow().isoformat() + 'Z'
                    })
                    self.valve_status[device_code] = {
                        'is_open': False,
                        'last_change': datetime.utcnow(),
                        'interlock_reason': 'Emergency Stop'
                    }
        else:
            logger.info("Emergency Stop deactivated")
        
        return commands

    def get_valve_status(self, device_code: str = None) -> Dict[str, Any]:
        if device_code:
            return self.valve_status.get(device_code, {})
        return self.valve_status

    def get_status_summary(self) -> Dict[str, Any]:
        open_valves = sum(1 for v in self.valve_status.values() if v.get('is_open', False))
        closed_valves = len(self.valve_status) - open_valves
        
        return {
            'enabled': self.enabled,
            'emergency_stop': self.emergency_stop,
            'total_valves': len(self.valve_status),
            'open_valves': open_valves,
            'closed_valves': closed_valves,
            'valve_status': self.valve_status,
            'thresholds': {
                'min_soil_humidity': self.min_soil_humidity,
                'max_soil_humidity': self.max_soil_humidity,
                'max_ec': self.max_ec,
                'min_ph': self.min_ph,
                'max_ph': self.max_ph
            }
        }

    def set_threshold(self, threshold_type: str, value: float) -> bool:
        threshold_map = {
            'min_soil_humidity': 'min_soil_humidity',
            'max_soil_humidity': 'max_soil_humidity',
            'max_ec': 'max_ec',
            'min_ph': 'min_ph',
            'max_ph': 'max_ph'
        }
        
        attr = threshold_map.get(threshold_type)
        if attr and hasattr(self, attr):
            setattr(self, attr, value)
            logger.info(f"Threshold {threshold_type} updated to {value}")
            return True
        return False
