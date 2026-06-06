import logging
import uuid
from typing import Optional
from models.growth_stage import CropVariety
from models.sensor_data import ZoneSensorData
from models.decision import IrrigationDecision, DecisionAction
from models.config import DecisionConfig
from .water_curve import WaterCurveCalculator

logger = logging.getLogger(__name__)


class IrrigationDecisionEngine:
    def __init__(
        self,
        water_curve: WaterCurveCalculator,
        decision_config: Optional[DecisionConfig] = None,
    ):
        self.water_curve = water_curve
        self.decision_config = decision_config or DecisionConfig()

    def make_decision(
        self,
        zone_data: ZoneSensorData,
        crop: Optional[CropVariety] = None,
        area_m2: float = 100.0,
    ) -> IrrigationDecision:
        zone_data.aggregate()

        if crop is None:
            return self._make_no_crop_decision(zone_data)

        stage_config = crop.get_current_stage_config()
        if not stage_config:
            return self._make_no_stage_decision(zone_data, crop)

        current_humidity = zone_data.aggregated_humidity
        rainfall = zone_data.rainfall

        adjusted_min, adjusted_max = self.water_curve.calculate_adjusted_thresholds(crop)
        water_requirement = self.water_curve.calculate_daily_requirement(crop)
        stage_factor = self.water_curve.get_growth_stage_factor(crop.current_stage)
        weather_factor = self.water_curve.calculate_weather_factor(
            air_temperature=zone_data.aggregated_temperature,
            rainfall=rainfall,
            wind_speed=zone_data.wind_speed,
        )

        if current_humidity is None:
            return self._make_no_sensor_decision(
                zone_data, crop, stage_config, adjusted_min, adjusted_max,
                water_requirement, stage_factor, weather_factor, rainfall
            )

        water_deficit = self.water_curve.calculate_water_deficit(
            current_humidity, crop
        )

        action, need_irrigation, reason = self._determine_action(
            current_humidity,
            adjusted_min,
            adjusted_max,
            water_deficit,
            rainfall,
        )

        if need_irrigation:
            duration = self.water_curve.calculate_irrigation_duration(
                water_deficit=water_deficit,
                water_requirement_mm=water_requirement,
                area_m2=area_m2,
                weather_factor=weather_factor,
                flow_rate_lpm=self.decision_config.flow_rate_lpm,
                min_duration=self.decision_config.min_irrigation_duration,
                max_duration=self.decision_config.max_irrigation_duration,
            )
            water_amount = self.water_curve.calculate_water_amount(
                duration, self.decision_config.flow_rate_lpm
            )
        else:
            duration = 0
            water_amount = 0.0

        current_stage_config = crop.get_current_stage_config()
        stage_name = current_stage_config.name if current_stage_config else crop.current_stage

        return IrrigationDecision(
            zone_id=zone_data.zone_id,
            zone_name=zone_data.zone_name,
            crop_id=crop.crop_id,
            crop_name=crop.name,
            current_stage=crop.current_stage,
            current_stage_name=stage_name,
            current_day=crop.current_day,
            total_growth_days=crop.total_growth_days,
            growth_progress=crop.get_growth_progress(),
            current_humidity=current_humidity,
            min_humidity=stage_config.min_humidity,
            max_humidity=stage_config.max_humidity,
            adjusted_min_humidity=adjusted_min,
            adjusted_max_humidity=adjusted_max,
            water_requirement_mm=water_requirement,
            water_deficit=water_deficit,
            action=action,
            need_irrigation=need_irrigation,
            duration_seconds=duration,
            water_amount_liters=water_amount,
            reason=reason,
            weather_factor=weather_factor,
            growth_stage_factor=stage_factor,
            rainfall=rainfall,
            decision_id=str(uuid.uuid4()),
        )

    def _determine_action(
        self,
        current_humidity: float,
        adjusted_min: float,
        adjusted_max: float,
        water_deficit: float,
        rainfall: Optional[float],
    ) -> tuple[DecisionAction, bool, str]:
        if rainfall is not None and rainfall > 10:
            return (
                DecisionAction.MAINTAIN,
                False,
                f"降雨量充足({rainfall:.1f}mm)，无需灌溉",
            )

        if current_humidity < adjusted_min:
            reason = (
                f"土壤湿度({current_humidity:.1f}%)低于调整后阈值({adjusted_min:.1f}%)，"
                f"缺水率: {water_deficit:.1f}%"
            )
            return DecisionAction.OPEN, True, reason

        elif current_humidity > adjusted_max:
            reason = (
                f"土壤湿度({current_humidity:.1f}%)高于调整后上限({adjusted_max:.1f}%)，"
                f"应停止灌溉"
            )
            return DecisionAction.CLOSE, False, reason

        else:
            reason = (
                f"土壤湿度({current_humidity:.1f}%)在适宜范围内"
                f"({adjusted_min:.1f}%-{adjusted_max:.1f}%)"
            )
            return DecisionAction.MAINTAIN, False, reason

    def _make_no_crop_decision(self, zone_data: ZoneSensorData) -> IrrigationDecision:
        logger.warning(f"Zone {zone_data.zone_id} has no crop associated")
        return IrrigationDecision(
            zone_id=zone_data.zone_id,
            zone_name=zone_data.zone_name,
            min_humidity=50.0,
            max_humidity=80.0,
            adjusted_min_humidity=50.0,
            adjusted_max_humidity=80.0,
            water_requirement_mm=0.0,
            action=DecisionAction.MAINTAIN,
            need_irrigation=False,
            duration_seconds=0,
            water_amount_liters=0.0,
            reason="该区域未关联作物，无法进行灌溉决策",
            decision_id=str(uuid.uuid4()),
        )

    def _make_no_stage_decision(
        self,
        zone_data: ZoneSensorData,
        crop: CropVariety,
    ) -> IrrigationDecision:
        logger.warning(f"Crop {crop.name} has no valid stage config")
        return IrrigationDecision(
            zone_id=zone_data.zone_id,
            zone_name=zone_data.zone_name,
            crop_id=crop.crop_id,
            crop_name=crop.name,
            current_day=crop.current_day,
            total_growth_days=crop.total_growth_days,
            growth_progress=crop.get_growth_progress(),
            min_humidity=50.0,
            max_humidity=80.0,
            adjusted_min_humidity=50.0,
            adjusted_max_humidity=80.0,
            water_requirement_mm=0.0,
            action=DecisionAction.MAINTAIN,
            need_irrigation=False,
            duration_seconds=0,
            water_amount_liters=0.0,
            reason="无效的生育期配置，使用默认阈值",
            decision_id=str(uuid.uuid4()),
        )

    def _make_no_sensor_decision(
        self,
        zone_data: ZoneSensorData,
        crop: CropVariety,
        stage_config,
        adjusted_min: float,
        adjusted_max: float,
        water_requirement: float,
        stage_factor: float,
        weather_factor: float,
        rainfall: Optional[float],
    ) -> IrrigationDecision:
        logger.warning(f"Zone {zone_data.zone_id} has no humidity sensor data")
        current_stage_config = crop.get_current_stage_config()
        stage_name = current_stage_config.name if current_stage_config else crop.current_stage

        return IrrigationDecision(
            zone_id=zone_data.zone_id,
            zone_name=zone_data.zone_name,
            crop_id=crop.crop_id,
            crop_name=crop.name,
            current_stage=crop.current_stage,
            current_stage_name=stage_name,
            current_day=crop.current_day,
            total_growth_days=crop.total_growth_days,
            growth_progress=crop.get_growth_progress(),
            current_humidity=None,
            min_humidity=stage_config.min_humidity,
            max_humidity=stage_config.max_humidity,
            adjusted_min_humidity=adjusted_min,
            adjusted_max_humidity=adjusted_max,
            water_requirement_mm=water_requirement,
            action=DecisionAction.MAINTAIN,
            need_irrigation=False,
            duration_seconds=0,
            water_amount_liters=0.0,
            reason="无可用的土壤湿度数据，无法做出灌溉决策",
            weather_factor=weather_factor,
            growth_stage_factor=stage_factor,
            rainfall=rainfall,
            decision_id=str(uuid.uuid4()),
        )

    def _calculate_area_from_zone(self, zone_id: str) -> float:
        return 100.0
