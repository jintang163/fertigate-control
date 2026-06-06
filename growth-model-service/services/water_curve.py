import logging
import numpy as np
from typing import List, Optional
from models.growth_stage import CropVariety
from models.decision import WaterRequirementPoint

logger = logging.getLogger(__name__)


class WaterCurveCalculator:
    def __init__(self):
        self._interpolation_method = 'sigmoid'

    def calculate_daily_requirement(
        self,
        crop: CropVariety,
        day: Optional[int] = None,
    ) -> float:
        if day is None:
            day = crop.current_day

        stage_config = crop.get_current_stage_config()
        if not stage_config:
            logger.warning(f"No stage config found for day {day}")
            return 0.0

        stage_key = crop.get_stage_by_day(day)
        if not stage_key or stage_key not in crop.growth_stages:
            return stage_config.water_requirement

        stage = crop.growth_stages[stage_key]
        day_in_stage = day - (stage.start_day or 0)
        total_days_in_stage = stage.duration_days

        base_water = stage.water_requirement
        curve_factor = self._get_curve_factor(day_in_stage, total_days_in_stage)

        return round(base_water * curve_factor, 3)

    def _get_curve_factor(self, day_in_stage: int, total_days: int) -> float:
        if total_days <= 1:
            return 1.0

        progress = day_in_stage / total_days

        if self._interpolation_method == 'linear':
            return 1.0
        elif self._interpolation_method == 'sigmoid':
            k = 6
            midpoint = 0.5
            sigmoid = 1 / (1 + np.exp(-k * (progress - midpoint)))
            return 0.7 + 0.6 * sigmoid
        elif self._interpolation_method == 'bell':
            sigma = 0.3
            bell = np.exp(-((progress - 0.5) ** 2) / (2 * sigma ** 2))
            return 0.8 + 0.4 * bell
        else:
            return 1.0

    def generate_curve(
        self,
        crop: CropVariety,
        start_day: int = 0,
        end_day: Optional[int] = None,
    ) -> List[WaterRequirementPoint]:
        if end_day is None:
            end_day = crop.total_growth_days - 1

        curve = []
        for day in range(start_day, end_day + 1):
            stage_key = crop.get_stage_by_day(day)
            if not stage_key or stage_key not in crop.growth_stages:
                continue

            stage_config = crop.growth_stages[stage_key]
            water_req = self.calculate_daily_requirement(crop, day)
            growth_progress = round(day / max(crop.total_growth_days, 1), 4)

            curve.append(WaterRequirementPoint(
                day=day,
                stage=stage_key,
                stage_name=stage_config.name,
                water_requirement_mm=water_req,
                min_humidity=stage_config.min_humidity,
                max_humidity=stage_config.max_humidity,
                growth_progress=growth_progress,
            ))

        return curve

    def calculate_stage_curve(
        self,
        crop: CropVariety,
        stage_key: str,
    ) -> List[WaterRequirementPoint]:
        if stage_key not in crop.growth_stages:
            logger.warning(f"Stage {stage_key} not found in crop {crop.name}")
            return []

        stage = crop.growth_stages[stage_key]
        start_day = stage.start_day or 0
        end_day = stage.end_day or start_day

        return self.generate_curve(crop, start_day, end_day)

    def calculate_total_water_requirement(
        self,
        crop: CropVariety,
        start_day: int = 0,
        end_day: Optional[int] = None,
    ) -> float:
        curve = self.generate_curve(crop, start_day, end_day)
        total = sum(p.water_requirement_mm for p in curve)
        return round(total, 2)

    def calculate_remaining_water_requirement(self, crop: CropVariety) -> float:
        return self.calculate_total_water_requirement(
            crop, crop.current_day, crop.total_growth_days - 1
        )

    def get_growth_stage_factor(self, stage_key: str) -> float:
        factors = {
            'seedling': 0.6,
            'tillering': 0.75,
            'vegetative': 0.8,
            'jointing': 0.9,
            'heading': 1.0,
            'flowering': 1.0,
            'fruiting': 0.95,
            'filling': 0.9,
            'ripening': 0.5,
            'dormant': 0.3,
        }
        return factors.get(stage_key, 0.7)

    def calculate_weather_factor(
        self,
        air_temperature: Optional[float] = None,
        rainfall: Optional[float] = None,
        light: Optional[float] = None,
        wind_speed: Optional[float] = None,
    ) -> float:
        factor = 1.0

        if air_temperature is not None:
            if air_temperature > 30:
                factor += 0.2
            elif air_temperature < 15:
                factor -= 0.1

        if rainfall is not None and rainfall > 5:
            factor -= min(0.3, rainfall * 0.02)

        if light is not None and light > 50000:
            factor += 0.1

        if wind_speed is not None and wind_speed > 5:
            factor += min(0.2, wind_speed * 0.02)

        return round(max(0.3, min(1.5, factor)), 3)

    def calculate_adjusted_thresholds(
        self,
        crop: CropVariety,
        day: Optional[int] = None,
    ) -> tuple[float, float]:
        if day is None:
            day = crop.current_day

        stage_config = crop.get_current_stage_config()
        if not stage_config:
            return 50.0, 80.0

        stage_key = crop.get_stage_by_day(day)
        stage_factor = self.get_growth_stage_factor(stage_key)

        adjusted_min = stage_config.min_humidity * (1 + (1 - stage_factor) * 0.2)
        adjusted_max = stage_config.max_humidity * (0.8 + stage_factor * 0.2)

        return round(adjusted_min, 2), round(adjusted_max, 2)

    def calculate_water_deficit(
        self,
        current_humidity: float,
        crop: CropVariety,
        day: Optional[int] = None,
    ) -> float:
        adjusted_min, _ = self.calculate_adjusted_thresholds(crop, day)
        deficit = adjusted_min - current_humidity
        return round(max(0, deficit), 2)

    def calculate_irrigation_duration(
        self,
        water_deficit: float,
        water_requirement_mm: float,
        area_m2: float,
        weather_factor: float,
        flow_rate_lpm: float = 10.0,
        min_duration: int = 300,
        max_duration: int = 7200,
    ) -> int:
        if water_deficit <= 0:
            return 0

        mm_to_liters_per_m2 = 1.0
        total_water_needed_liters = water_deficit * area_m2 * mm_to_liters_per_m2
        total_water_needed_liters += water_requirement_mm * area_m2 * 0.5

        raw_duration_minutes = total_water_needed_liters / flow_rate_lpm
        duration_seconds = int(raw_duration_minutes * 60 * weather_factor)

        return max(min_duration, min(max_duration, duration_seconds))

    def calculate_water_amount(
        self,
        duration_seconds: int,
        flow_rate_lpm: float = 10.0,
    ) -> float:
        hours = duration_seconds / 3600.0
        return round(hours * flow_rate_lpm * 60, 2)
