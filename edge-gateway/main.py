#!/usr/bin/env python3
import logging
import yaml
import time
import json
import argparse
import signal
import sys
from datetime import datetime
from typing import Dict, Any, List

from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.interval import IntervalTrigger

from modbus.collector import DataCollector
from mqtt.client import MqttClient
from cache.manager import CacheManager
from interlock.manager import InterlockManager

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler('logs/edge-gateway.log')
    ]
)

logger = logging.getLogger(__name__)


class EdgeGateway:
    def __init__(self, config_path: str, simulate: bool = False):
        self.simulate = simulate
        self.config = self._load_config(config_path)
        self.running = False
        self.scheduler = None
        
        self._setup_signal_handlers()
        self._init_components()

    def _load_config(self, config_path: str) -> Dict[str, Any]:
        try:
            with open(config_path, 'r', encoding='utf-8') as f:
                config = yaml.safe_load(f)
            logger.info("Configuration loaded successfully")
            return config
        except Exception as e:
            logger.error(f"Failed to load configuration: {e}")
            sys.exit(1)

    def _setup_signal_handlers(self) -> None:
        signal.signal(signal.SIGINT, self._signal_handler)
        signal.signal(signal.SIGTERM, self._signal_handler)

    def _signal_handler(self, signum, frame):
        logger.info(f"Received signal {signum}, shutting down...")
        self.stop()

    def _init_components(self) -> None:
        try:
            self.data_collector = DataCollector(
                modbus_config=self.config['modbus'],
                devices=self.config['devices'],
                simulate=self.simulate
            )
            logger.info("Data collector initialized")

            self.mqtt_client = MqttClient(self.config['mqtt'])
            logger.info("MQTT client initialized")

            self.cache_manager = CacheManager(self.config['cache'])
            logger.info("Cache manager initialized")

            self.interlock_manager = InterlockManager(
                config=self.config['interlock'],
                devices=self.config['devices']
            )
            logger.info("Interlock manager initialized")

            self._subscribe_to_topics()
            
            self._register_mqtt_callbacks()

        except Exception as e:
            logger.error(f"Failed to initialize components: {e}")
            sys.exit(1)

    def _register_mqtt_callbacks(self) -> None:
        self.mqtt_client.add_connect_callback(self._on_mqtt_connected)

    def _subscribe_to_topics(self) -> None:
        topics = self.config['mqtt'].get('topics', {})
        
        self.mqtt_client.subscribe(
            topics.get('valve_command', 'fertigate/valve/command'),
            self._handle_valve_command
        )
        
        self.mqtt_client.subscribe(
            'fertigate/gateway/command',
            self._handle_gateway_command
        )

    def _on_mqtt_connected(self, is_reconnect: bool) -> None:
        if is_reconnect:
            logger.info("MQTT reconnected, starting data backfill...")
            self._backfill_cached_data()
        else:
            logger.info("MQTT initial connection complete")
            self._backfill_cached_data()

    def _handle_valve_command(self, payload: Dict[str, Any], topic: str) -> None:
        logger.info(f"Received valve command: {payload}")
        
        try:
            device_code = payload.get('device_code')
            open_valve = payload.get('open', False)
            reason = payload.get('reason', 'Remote command')
            
            if self.interlock_manager.emergency_stop:
                logger.warning("Emergency stop active - ignoring valve command")
                return
            
            device = self._find_device(device_code)
            if device:
                success = self.data_collector.control_valve(device, open_valve)
                if success:
                    self.interlock_manager.update_valve_status(device_code, open_valve)
                    
                    status_msg = {
                        'device_code': device_code,
                        'is_open': open_valve,
                        'reason': reason,
                        'timestamp': datetime.utcnow().isoformat() + 'Z'
                    }
                    
                    self.mqtt_client.publish(
                        self.config['mqtt']['topics'].get('device_status', 'fertigate/device/status'),
                        status_msg
                    )
            else:
                logger.warning(f"Device not found: {device_code}")
                
        except Exception as e:
            logger.error(f"Error handling valve command: {e}")

    def _handle_gateway_command(self, payload: Dict[str, Any], topic: str) -> None:
        logger.info(f"Received gateway command: {payload}")
        
        try:
            command = payload.get('command')
            
            if command == 'emergency_stop':
                stop = payload.get('stop', True)
                commands = self.interlock_manager.set_emergency_stop(stop)
                
                for cmd in commands:
                    device = self._find_device(cmd['device_code'])
                    if device:
                        self.data_collector.control_valve(device, cmd['open'])
                
                response = {
                    'command': 'emergency_stop',
                    'status': 'executed',
                    'emergency_stop': stop,
                    'valves_closed': len(commands)
                }
                self.mqtt_client.publish('fertigate/gateway/response', response)
                
            elif command == 'get_status':
                status = self._get_gateway_status()
                self.mqtt_client.publish('fertigate/gateway/status', status)
                
            elif command == 'set_threshold':
                threshold_type = payload.get('threshold_type')
                value = payload.get('value')
                if threshold_type and value is not None:
                    success = self.interlock_manager.set_threshold(threshold_type, value)
                    response = {
                        'command': 'set_threshold',
                        'status': 'success' if success else 'failed',
                        'threshold_type': threshold_type,
                        'value': value
                    }
                    self.mqtt_client.publish('fertigate/gateway/response', response)
                    
        except Exception as e:
            logger.error(f"Error handling gateway command: {e}")

    def _find_device(self, device_code: str) -> Dict[str, Any]:
        for device in self.config['devices']:
            if device.get('device_code') == device_code:
                return device
        return None

    def _collect_and_send_data(self) -> None:
        try:
            logger.debug("Starting data collection...")
            data_list = self.data_collector.collect_all()
            
            if not data_list:
                logger.warning("No data collected")
                return

            interlock_result = self.interlock_manager.process_sensor_data(data_list)
            
            for cmd in interlock_result['valve_commands']:
                device = self._find_device(cmd['device_code'])
                if device:
                    self.data_collector.control_valve(device, cmd['open'])

            for alert in interlock_result['alerts']:
                self.mqtt_client.publish(
                    self.config['mqtt']['topics'].get('alert', 'fertigate/alert'),
                    alert
                )

            for data in data_list:
                data['interlock_safe'] = interlock_result['is_safe']
                
                published = self.mqtt_client.publish(
                    self.config['mqtt']['topics'].get('sensor_data', 'fertigate/sensor/data'),
                    data
                )
                
                if not published:
                    self.cache_manager.store(data)
                    logger.debug(f"Cached data for {data['device_code']}")

            self._flush_cache_if_needed()
            
            logger.info(f"Collected {len(data_list)} devices data")
            
        except Exception as e:
            logger.error(f"Error in data collection: {e}")

    def _backfill_cached_data(self) -> None:
        if not self.mqtt_client.is_connected():
            logger.warning("MQTT not connected, cannot backfill cached data")
            return

        try:
            total_backfilled = 0
            batch_size = self.config.get('cache', {}).get('backfill_batch_size', 100)
            
            while True:
                unsynced = self.cache_manager.get_unsynced(limit=batch_size)
                if not unsynced:
                    break

                published_keys = []
                
                for entry in unsynced:
                    published = self.mqtt_client.publish_with_timestamp(
                        self.config['mqtt']['topics'].get('sensor_data', 'fertigate/sensor/data'),
                        entry['data'],
                        entry['timestamp']
                    )
                    
                    if published:
                        published_keys.append(entry['key'])

                if published_keys:
                    self.cache_manager.mark_synced(published_keys)
                    total_backfilled += len(published_keys)
                    logger.info(f"Backfilled {len(published_keys)} entries, total: {total_backfilled}")

                if len(unsynced) < batch_size:
                    break

            if total_backfilled > 0:
                logger.info(f"Data backfill completed. Total {total_backfilled} entries backfilled")
                self.cache_manager.cleanup_synced()
                self.cache_manager.flush()
            else:
                logger.info("No cached data to backfill")
            
        except Exception as e:
            logger.error(f"Error during data backfill: {e}")

    def _flush_cache_if_needed(self) -> None:
        if not self.cache_manager.should_flush():
            return

        if not self.mqtt_client.is_connected():
            logger.debug("MQTT not connected, skipping cache flush")
            return

        try:
            unsynced = self.cache_manager.get_unsynced(limit=50)
            if not unsynced:
                self.cache_manager.flush()
                return

            published_keys = []
            
            for entry in unsynced:
                published = self.mqtt_client.publish(
                    self.config['mqtt']['topics'].get('sensor_data', 'fertigate/sensor/data'),
                    entry['data']
                )
                
                if published:
                    published_keys.append(entry['key'])

            if published_keys:
                self.cache_manager.mark_synced(published_keys)
                logger.info(f"Flushed {len(published_keys)} cached entries")

            self.cache_manager.cleanup_synced()
            self.cache_manager.flush()
            
        except Exception as e:
            logger.error(f"Error flushing cache: {e}")

    def _get_gateway_status(self) -> Dict[str, Any]:
        return {
            'gateway_id': self.config['gateway']['id'],
            'gateway_name': self.config['gateway']['name'],
            'timestamp': datetime.utcnow().isoformat() + 'Z',
            'mqtt_connected': self.mqtt_client.is_connected(),
            'simulation_mode': self.simulate,
            'interlock': self.interlock_manager.get_status_summary(),
            'cache': self.cache_manager.get_stats(),
            'devices': [
                {
                    'device_code': d['device_code'],
                    'name': d['name'],
                    'type': d['type'],
                    'zone': d.get('zone', '')
                }
                for d in self.config['devices']
            ]
        }

    def _send_heartbeat(self) -> None:
        try:
            heartbeat = {
                'gateway_id': self.config['gateway']['id'],
                'timestamp': datetime.utcnow().isoformat() + 'Z',
                'mqtt_connected': self.mqtt_client.is_connected(),
                'simulation_mode': self.simulate,
                'cache_stats': self.cache_manager.get_stats()
            }
            
            self.mqtt_client.publish(
                'fertigate/gateway/heartbeat',
                heartbeat
            )
            
            logger.debug("Heartbeat sent")
            
        except Exception as e:
            logger.error(f"Error sending heartbeat: {e}")

    def start(self) -> None:
        logger.info("Starting edge gateway...")
        
        if self.simulate:
            logger.info("Running in SIMULATION mode")
        
        self.running = True
        
        self.scheduler = BackgroundScheduler()
        
        collection_interval = self.config['collection'].get('interval_seconds', 30)
        self.scheduler.add_job(
            self._collect_and_send_data,
            trigger=IntervalTrigger(seconds=collection_interval),
            id='data_collection',
            replace_existing=True
        )
        
        self.scheduler.add_job(
            self._send_heartbeat,
            trigger=IntervalTrigger(seconds=60),
            id='heartbeat',
            replace_existing=True
        )
        
        self.scheduler.start()
        logger.info(f"Scheduler started, collecting data every {collection_interval}s")
        
        self._collect_and_send_data()
        
        logger.info("Edge gateway started successfully")
        
        try:
            while self.running:
                time.sleep(1)
        except KeyboardInterrupt:
            pass

    def stop(self) -> None:
        logger.info("Stopping edge gateway...")
        self.running = False
        
        if self.scheduler:
            self.scheduler.shutdown(wait=False)
            logger.info("Scheduler stopped")
        
        if self.data_collector:
            self.data_collector.close()
        
        if self.mqtt_client:
            self.mqtt_client.close()
        
        if self.cache_manager:
            self.cache_manager.close()
        
        logger.info("Edge gateway stopped")


def main():
    parser = argparse.ArgumentParser(description='Edge Gateway for Fertigate Control System')
    parser.add_argument('--config', default='config.yaml', help='Path to configuration file')
    parser.add_argument('--simulate', action='store_true', help='Run in simulation mode without actual hardware')
    args = parser.parse_args()

    import os
    os.makedirs('logs', exist_ok=True)
    os.makedirs('data', exist_ok=True)

    gateway = EdgeGateway(config_path=args.config, simulate=args.simulate)
    
    try:
        gateway.start()
    except Exception as e:
        logger.error(f"Gateway error: {e}")
    finally:
        gateway.stop()


if __name__ == '__main__':
    main()
