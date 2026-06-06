import os
import yaml
import logging
from typing import Dict, Any
from models.config import ServiceConfig, SpringBootConfig, DecisionConfig

logger = logging.getLogger(__name__)


class ConfigLoader:
    def __init__(self, config_path: str = "config.yaml"):
        self.config_path = config_path
        self._raw_config: Dict[str, Any] = {}
        self._service_config: ServiceConfig | None = None

    def load(self) -> ServiceConfig:
        if not os.path.exists(self.config_path):
            logger.warning(f"Config file not found at {self.config_path}, using defaults")
            self._service_config = ServiceConfig()
            return self._service_config

        try:
            with open(self.config_path, 'r', encoding='utf-8') as f:
                self._raw_config = yaml.safe_load(f) or {}
            logger.info(f"Configuration loaded from {self.config_path}")
        except Exception as e:
            logger.error(f"Failed to load config: {e}, using defaults")
            self._raw_config = {}

        self._service_config = self._parse_config()
        return self._service_config

    def _parse_config(self) -> ServiceConfig:
        service_cfg = self._raw_config.get('service', {})
        spring_cfg = self._raw_config.get('spring_boot', {})
        decision_cfg = self._raw_config.get('decision', {})
        crops_cfg = self._raw_config.get('crops', {})

        return ServiceConfig(
            name=service_cfg.get('name', 'growth-model-service'),
            host=service_cfg.get('host', '0.0.0.0'),
            port=service_cfg.get('port', 8081),
            log_level=service_cfg.get('log_level', 'INFO'),
            spring_boot=SpringBootConfig(
                base_url=spring_cfg.get('base_url', 'http://localhost:8080/api'),
                timeout=spring_cfg.get('timeout', 30),
                retry_attempts=spring_cfg.get('retry_attempts', 3),
                retry_delay=spring_cfg.get('retry_delay', 2),
            ),
            decision=DecisionConfig(
                check_interval_seconds=decision_cfg.get('check_interval_seconds', 60),
                default_irrigation_duration=decision_cfg.get('default_irrigation_duration', 1800),
                min_irrigation_duration=decision_cfg.get('min_irrigation_duration', 300),
                max_irrigation_duration=decision_cfg.get('max_irrigation_duration', 7200),
                flow_rate_lpm=decision_cfg.get('flow_rate_lpm', 10.0),
                enable_auto_callback=decision_cfg.get('enable_auto_callback', True),
            ),
            crops_config=crops_cfg.get('default', {}),
        )

    def get_config(self) -> ServiceConfig:
        if self._service_config is None:
            self.load()
        return self._service_config

    def get_crops_config(self) -> Dict[str, Any]:
        return self.get_config().crops_config
