from typing import Dict, Any
from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings


class SpringBootConfig(BaseModel):
    base_url: str = "http://localhost:8080/api"
    timeout: int = 30
    retry_attempts: int = 3
    retry_delay: int = 2


class DecisionConfig(BaseModel):
    check_interval_seconds: int = 60
    default_irrigation_duration: int = 1800
    min_irrigation_duration: int = 300
    max_irrigation_duration: int = 7200
    flow_rate_lpm: float = 10.0
    enable_auto_callback: bool = True


class ServiceConfig(BaseModel):
    name: str = "growth-model-service"
    host: str = "0.0.0.0"
    port: int = 8081
    log_level: str = "INFO"
    spring_boot: SpringBootConfig = Field(default_factory=SpringBootConfig)
    decision: DecisionConfig = Field(default_factory=DecisionConfig)
    crops_config: Dict[str, Any] = Field(default_factory=dict)
