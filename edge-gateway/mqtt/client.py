import logging
import json
import time
from typing import Dict, Any, Callable, Optional, List, Tuple
from threading import Lock, Event
from collections import deque

import paho.mqtt.client as mqtt

logger = logging.getLogger(__name__)


class MqttClient:
    def __init__(self, config: Dict[str, Any]):
        self.config = config
        self.client = None
        self._connected = False
        self._was_connected = False
        self._message_callbacks = {}
        self._connect_callbacks: List[Callable[[bool], None]] = []
        
        self._pending_messages: Dict[int, Tuple[str, str, Event]] = {}
        self._pending_lock = Lock()
        self._puback_timeout = config.get('puback_timeout', 5)
        
        self._connect()

    def _connect(self) -> None:
        try:
            self.client = mqtt.Client(
                client_id=self.config.get('client_id', f'edge-gateway-{int(time.time())}'),
                clean_session=True
            )
            
            if self.config.get('username'):
                self.client.username_pw_set(
                    username=self.config['username'],
                    password=self.config.get('password', '')
                )
            
            self.client.on_connect = self._on_connect
            self.client.on_disconnect = self._on_disconnect
            self.client.on_message = self._on_message
            self.client.on_publish = self._on_publish
            
            self.client.connect(
                host=self.config['broker'],
                port=self.config.get('port', 1883),
                keepalive=self.config.get('keepalive', 60)
            )
            
            self.client.loop_start()
            
            timeout = 10
            start_time = time.time()
            while not self._connected and time.time() - start_time < timeout:
                time.sleep(0.1)
            
            if self._connected:
                logger.info("MQTT client connected successfully")
            else:
                logger.warning("MQTT client connection timeout")
                
        except Exception as e:
            logger.error(f"Error connecting to MQTT broker: {e}")
            self._connected = False

    def _on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            self._connected = True
            is_reconnect = self._was_connected
            self._was_connected = True
            logger.info(f"Connected to MQTT broker with result code {rc}")
            
            for topic in self._message_callbacks.keys():
                self.client.subscribe(topic, qos=self.config.get('qos', 1))
                logger.info(f"Subscribed to topic: {topic}")
            
            self._notify_connect_callbacks(is_reconnect)
        else:
            self._connected = False
            logger.error(f"Failed to connect to MQTT broker with result code {rc}")

    def _on_disconnect(self, client, userdata, rc):
        self._connected = False
        if rc != 0:
            logger.warning(f"Unexpected MQTT disconnection (code: {rc}). Reconnecting...")
            self._reconnect()

    def _on_message(self, client, userdata, msg):
        try:
            topic = msg.topic
            payload = json.loads(msg.payload.decode('utf-8'))
            
            logger.debug(f"Received message on {topic}: {payload}")
            
            callback = self._message_callbacks.get(topic)
            if callback:
                callback(payload, topic)
            else:
                for registered_topic, callback in self._message_callbacks.items():
                    if self._topic_matches(registered_topic, topic):
                        callback(payload, topic)
                        break
        except json.JSONDecodeError as e:
            logger.error(f"Failed to parse MQTT message: {e}")
        except Exception as e:
            logger.error(f"Error processing MQTT message: {e}")

    def _on_publish(self, client, userdata, mid):
        logger.debug(f"Message published with mid: {mid}")
        
        with self._pending_lock:
            if mid in self._pending_messages:
                topic, key, event = self._pending_messages.pop(mid)
                logger.debug(f"PUBACK received for mid {mid}, topic {topic}, key {key}")
                event.set()

    def _notify_connect_callbacks(self, is_reconnect: bool) -> None:
        for callback in self._connect_callbacks:
            try:
                callback(is_reconnect)
            except Exception as e:
                logger.error(f"Error in connect callback: {e}")

    def add_connect_callback(self, callback: Callable[[bool], None]) -> None:
        self._connect_callbacks.append(callback)

    def remove_connect_callback(self, callback: Callable[[bool], None]) -> None:
        if callback in self._connect_callbacks:
            self._connect_callbacks.remove(callback)

    def _topic_matches(self, pattern: str, topic: str) -> bool:
        pattern_parts = pattern.split('/')
        topic_parts = topic.split('/')
        
        if len(pattern_parts) != len(topic_parts):
            return False
        
        for p, t in zip(pattern_parts, topic_parts):
            if p == '+':
                continue
            elif p == '#':
                return True
            elif p != t:
                return False
        
        return True

    def _reconnect(self) -> None:
        max_retries = 10
        retry_delay = 2
        
        for attempt in range(max_retries):
            try:
                logger.info(f"MQTT reconnection attempt {attempt + 1}/{max_retries}")
                self.client.reconnect()
                
                timeout = 5
                start_time = time.time()
                while not self._connected and time.time() - start_time < timeout:
                    time.sleep(0.1)
                
                if self._connected:
                    logger.info("MQTT reconnected successfully")
                    return
            except Exception as e:
                logger.error(f"MQTT reconnection failed: {e}")
            
            time.sleep(retry_delay)
            retry_delay = min(retry_delay * 2, 30)
        
        logger.error("Failed to reconnect to MQTT broker after max retries")

    def is_connected(self) -> bool:
        return self._connected and self.client is not None

    def publish(self, topic: str, payload: Dict[str, Any], qos: Optional[int] = None,
                wait_confirm: bool = False, cache_key: Optional[str] = None) -> bool:
        if not self.is_connected():
            logger.warning(f"MQTT not connected. Cannot publish to {topic}")
            return False
        
        try:
            actual_qos = qos if qos is not None else self.config.get('qos', 1)
            
            if wait_confirm and actual_qos < 1:
                logger.warning(f"QoS must be >= 1 for PUBACK confirmation, using QoS 1")
                actual_qos = 1
            
            payload_json = json.dumps(payload, ensure_ascii=False)
            msg_info = self.client.publish(
                topic=topic,
                payload=payload_json,
                qos=actual_qos
            )
            
            if msg_info.rc != mqtt.MQTT_ERR_SUCCESS:
                logger.error(f"Failed to publish to {topic}: {msg_info.rc}")
                return False
            
            if wait_confirm and actual_qos >= 1:
                return self._wait_for_puback(msg_info.mid, topic, cache_key)
            
            logger.debug(f"Published to {topic} successfully (mid: {msg_info.mid})")
            return True
            
        except Exception as e:
            logger.error(f"Error publishing to {topic}: {e}")
            return False

    def _wait_for_puback(self, mid: int, topic: str, cache_key: Optional[str] = None) -> bool:
        event = Event()
        
        with self._pending_lock:
            self._pending_messages[mid] = (topic, cache_key or '', event)
        
        logger.debug(f"Waiting for PUBACK for mid {mid}, timeout {self._puback_timeout}s")
        
        if event.wait(timeout=self._puback_timeout):
            logger.debug(f"PUBACK confirmed for mid {mid}")
            return True
        else:
            with self._pending_lock:
                if mid in self._pending_messages:
                    self._pending_messages.pop(mid)
            logger.warning(f"PUBACK timeout for mid {mid} after {self._puback_timeout}s")
            return False

    def publish_batch(self, topic: str, payloads: List[Dict[str, Any]], 
                      qos: Optional[int] = None, wait_confirm: bool = False) -> List[bool]:
        results = []
        for payload in payloads:
            results.append(self.publish(topic, payload, qos, wait_confirm))
        return results

    def publish_with_timestamp(self, topic: str, payload: Dict[str, Any], 
                               original_timestamp: str, qos: Optional[int] = None,
                               wait_confirm: bool = False, cache_key: Optional[str] = None) -> bool:
        enriched_payload = {
            **payload,
            'original_timestamp': original_timestamp,
            'is_retransmission': True
        }
        return self.publish(topic, enriched_payload, qos, wait_confirm, cache_key)

    def subscribe(self, topic: str, callback: Callable[[Dict[str, Any], str], None]) -> None:
        self._message_callbacks[topic] = callback
        
        if self.is_connected():
            self.client.subscribe(topic, qos=self.config.get('qos', 1))
            logger.info(f"Subscribed to topic: {topic}")

    def unsubscribe(self, topic: str) -> None:
        if topic in self._message_callbacks:
            del self._message_callbacks[topic]
            
            if self.is_connected():
                self.client.unsubscribe(topic)
                logger.info(f"Unsubscribed from topic: {topic}")

    def close(self) -> None:
        if self.client:
            self.client.loop_stop()
            self.client.disconnect()
            logger.info("MQTT client closed")
