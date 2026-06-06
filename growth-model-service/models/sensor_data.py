from datetime import datetime
from typing import Dict, Optional, List
from pydantic import BaseModel, Field


class SensorData(BaseModel):
    device_code: str
    timestamp: datetime = Field(default_factory=datetime.now)
    humidity: Optional[float] = None
    ec: Optional[float] = None
    ph: Optional[float] = None
    temperature: Optional[float] = None
    air_temperature: Optional[float] = None
    air_humidity: Optional[float] = None
    light: Optional[float] = None
    rainfall: Optional[float] = None
    wind_speed: Optional[float] = None
    raw_data: Optional[Dict] = None


class ZoneSensorData(BaseModel):
    zone_id: str
    zone_name: str
    crop_id: Optional[str] = None
    sensors: List[SensorData]
    aggregated_humidity: Optional[float] = None
    aggregated_ec: Optional[float] = None
    aggregated_ph: Optional[float] = None
    aggregated_temperature: Optional[float] = None
    rainfall: Optional[float] = None
    wind_speed: Optional[float] = None
    timestamp: datetime = Field(default_factory=datetime.now)

    def aggregate(self) -> None:
        humidity_values = [s.humidity for s in self.sensors if s.humidity is not None]
        ec_values = [s.ec for s in self.sensors if s.ec is not None]
        ph_values = [s.ph for s in self.sensors if s.ph is not None]
        temp_values = [s.temperature for s in self.sensors if s.temperature is not None]
        rainfall_values = [s.rainfall for s in self.sensors if s.rainfall is not None]
        wind_values = [s.wind_speed for s in self.sensors if s.wind_speed is not None]

        self.aggregated_humidity = self._avg(humidity_values) if humidity_values else None
        self.aggregated_ec = self._avg(ec_values) if ec_values else None
        self.aggregated_ph = self._avg(ph_values) if ph_values else None
        self.aggregated_temperature = self._avg(temp_values) if temp_values else None
        self.rainfall = self._avg(rainfall_values) if rainfall_values else None
        self.wind_speed = self._avg(wind_values) if wind_values else None

    @staticmethod
    def _avg(values: List[float]) -> float:
        return round(sum(values) / len(values), 2)
