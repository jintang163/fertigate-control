import logging
import json
import time
from typing import Dict, Any, Callable, Optional

import paho.mqtt.client as mqtt

logger = logging.getLogger(__name__)


class MqttClient:
    def __init__(self, config: Dict[str, Any]):
        self.config = config
        self.client = None
        self._connected = False
        self._message_callbacks = {}
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
            logger.info(f"Connected to MQTT broker with result code {rc}")
            
            for topic in self._message_callbacks.keys():
                self.client.subscribe(topic, qos=self.config.get('qos', 1))
                logger.info(f"Subscribed to topic: {topic}")
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

    def publish(self, topic: str, payload: Dict[str, Any], qos: Optional[int] = None) -> bool:
        if not self.is_connected():
            logger.warning(f"MQTT not connected. Cannot publish to {topic}")
            return False
        
        try:
            payload_json = json.dumps(payload, ensure_ascii=False)
            msg_info = self.client.publish(
                topic=topic,
                payload=payload_json,
                qos=qos if qos is not None else self.config.get('qos', 1)
            )
            
            if msg_info.rc == mqtt.MQTT_ERR_SUCCESS:
                logger.debug(f"Published to {topic} successfully")
                return True
            else:
                logger.error(f"Failed to publish to {topic}: {msg_info.rc}")
                return False
        except Exception as e:
            logger.error(f"Error publishing to {topic}: {e}")
            return False

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
