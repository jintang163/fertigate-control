import logging
from typing import Dict, List, Optional
from datetime import datetime
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.interval import IntervalTrigger

from models.config import ServiceConfig
from models.decision import DecisionResult
from models.sensor_data import ZoneSensorData, SensorData
from .crop_manager import CropManager
from .water_curve import WaterCurveCalculator
from .fertilizer_curve import FertilizerCurveCalculator
from .irrigation_decision import IrrigationDecisionEngine
from .fertilizer_decision import FertilizerDecisionEngine
from .spring_boot_client import SpringBootClient

logger = logging.getLogger(__name__)


class DecisionScheduler:
    def __init__(
        self,
        config: ServiceConfig,
        crop_manager: CropManager,
        water_curve: WaterCurveCalculator,
        fertilizer_curve: FertilizerCurveCalculator,
        spring_boot_client: SpringBootClient,
    ):
        self.config = config
        self.crop_manager = crop_manager
        self.water_curve = water_curve
        self.fertilizer_curve = fertilizer_curve
        self.spring_boot_client = spring_boot_client

        self.irrigation_engine = IrrigationDecisionEngine(
            water_curve, config.decision
        )
        self.fertilizer_engine = FertilizerDecisionEngine(
            fertilizer_curve, config.decision
        )

        self.scheduler: Optional[BackgroundScheduler] = None
        self._last_decisions: Dict[str, DecisionResult] = {}
        self.running = False

    def start(self) -> None:
        if self.running:
            logger.warning("Decision scheduler is already running")
            return

        self.spring_boot_client.sync_crops_from_spring_boot(self.crop_manager)

        self.scheduler = BackgroundScheduler()

        interval = self.config.decision.check_interval_seconds
        self.scheduler.add_job(
            self._check_and_make_decisions,
            trigger=IntervalTrigger(seconds=interval),
            id='decision_check',
            replace_existing=True,
        )

        self.scheduler.add_job(
            self._sync_crops,
            trigger=IntervalTrigger(minutes=30),
            id='crop_sync',
            replace_existing=True,
        )

        self.scheduler.start()
        self.running = True
        logger.info(
            f"Decision scheduler started with {interval}s check interval"
        )

        self._check_and_make_decisions()

    def stop(self) -> None:
        if self.scheduler:
            self.scheduler.shutdown(wait=False)
            self.scheduler = None
        self.running = False
        logger.info("Decision scheduler stopped")

    def _sync_crops(self) -> None:
        try:
            self.spring_boot_client.sync_crops_from_spring_boot(self.crop_manager)
        except Exception as e:
            logger.error(f"Error syncing crops: {e}")

    def _check_and_make_decisions(self) -> None:
        logger.info("Starting decision check cycle...")
        try:
            zones = self.spring_boot_client.get_zones()
            logger.info(f"Processing {len(zones)} zones")

            for zone_data in zones:
                try:
                    result = self._process_zone(zone_data)
                    if result:
                        self._last_decisions[result.zone_id] = result

                        if self.config.decision.enable_auto_callback:
                            self._callback_decision(result)

                except Exception as e:
                    logger.error(
                        f"Error processing zone {zone_data.get('id')}: {e}",
                        exc_info=True,
                    )

        except Exception as e:
            logger.error(f"Error in decision check cycle: {e}", exc_info=True)

        logger.info("Decision check cycle completed")

    def _process_zone(self, zone_data: Dict) -> Optional[DecisionResult]:
        zone_id = str(zone_data.get('id', ''))
        zone_name = zone_data.get('name', 'Unknown')

        crop_id = None
        crop_data = zone_data.get('crop')
        if crop_data:
            crop_id = str(crop_data.get('id', ''))

        sensor_data_list = self._fetch_zone_sensor_data(zone_id)
        zone_sensor_data = ZoneSensorData(
            zone_id=zone_id,
            zone_name=zone_name,
            crop_id=crop_id,
            sensors=sensor_data_list,
        )
        zone_sensor_data.aggregate()

        crop = None
        if crop_id:
            crop = self.crop_manager.get_crop_by_id(crop_id)
            if not crop:
                crop_data_from_api = self.spring_boot_client.get_crop_by_id(crop_id)
                if crop_data_from_api:
                    crop = self.crop_manager.update_crop_from_spring_boot(crop_data_from_api)

        area_m2 = self.spring_boot_client.get_zone_area(zone_id)
        area_ha = area_m2 / 10000.0

        irrigation_decision = self.irrigation_engine.make_decision(
            zone_sensor_data,
            crop,
            area_m2=area_m2,
        )

        fertilizer_decision = self.fertilizer_engine.make_decision(
            zone_sensor_data,
            crop,
            area_ha=area_ha,
        )

        result = DecisionResult(
            zone_id=zone_id,
            zone_name=zone_name,
            irrigation=irrigation_decision,
            fertilizer=fertilizer_decision,
            generated_at=datetime.now(),
        )

        logger.info(
            f"Zone {zone_name}: "
            f"Irrigation={irrigation_decision.action.value} "
            f"(need={irrigation_decision.need_irrigation}, "
            f"duration={irrigation_decision.duration_seconds}s), "
            f"Fertilizer={fertilizer_decision.action.value} "
            f"(need={fertilizer_decision.need_fertilizer}, "
            f"amount={fertilizer_decision.fertilizer_amount_kg}kg)"
        )

        return result

    def _fetch_zone_sensor_data(self, zone_id: str) -> List[SensorData]:
        raw_data = self.spring_boot_client.get_sensor_data_by_zone(zone_id)
        sensor_list: List[SensorData] = []

        for data in raw_data:
            try:
                sensor = SensorData(
                    device_code=data.get('deviceCode', ''),
                    timestamp=self._parse_datetime(data.get('timestamp')),
                    humidity=data.get('humidity'),
                    ec=data.get('ec'),
                    ph=data.get('ph'),
                    temperature=data.get('temperature'),
                    air_temperature=data.get('airTemperature'),
                    air_humidity=data.get('airHumidity'),
                    light=data.get('light'),
                    rainfall=data.get('rainfall'),
                    wind_speed=data.get('windSpeed'),
                    raw_data=data,
                )
                sensor_list.append(sensor)
            except Exception as e:
                logger.warning(f"Error parsing sensor data: {e}")

        if not sensor_list:
            logger.debug(f"No sensor data found for zone {zone_id}")

        return sensor_list

    def _callback_decision(self, result: DecisionResult) -> None:
        try:
            success, message = self.spring_boot_client.callback_decision_result(result)
            result.callback_success = success
            result.callback_message = message

            if success and result.irrigation and result.irrigation.need_irrigation:
                logger.info(
                    f"Auto-execute irrigation for zone {result.zone_id}: "
                    f"duration={result.irrigation.duration_seconds}s"
                )

            if success and result.fertilizer and result.fertilizer.need_fertilizer:
                logger.info(
                    f"Auto-execute fertilizer for zone {result.zone_id}: "
                    f"amount={result.fertilizer.fertilizer_amount_kg}kg"
                )

        except Exception as e:
            logger.error(f"Error during decision callback: {e}")
            result.callback_success = False
            result.callback_message = str(e)

    def make_manual_decision(
        self,
        zone_id: str,
        zone_name: str,
        crop_id: Optional[str] = None,
        sensor_data: Optional[List[SensorData]] = None,
    ) -> Optional[DecisionResult]:
        zone_sensor_data = ZoneSensorData(
            zone_id=zone_id,
            zone_name=zone_name,
            crop_id=crop_id,
            sensors=sensor_data or [],
        )
        zone_sensor_data.aggregate()

        crop = None
        if crop_id:
            crop = self.crop_manager.get_crop_by_id(crop_id)

        area_m2 = self.spring_boot_client.get_zone_area(zone_id)
        area_ha = area_m2 / 10000.0

        irrigation_decision = self.irrigation_engine.make_decision(
            zone_sensor_data,
            crop,
            area_m2=area_m2,
        )

        fertilizer_decision = self.fertilizer_engine.make_decision(
            zone_sensor_data,
            crop,
            area_ha=area_ha,
        )

        result = DecisionResult(
            zone_id=zone_id,
            zone_name=zone_name,
            irrigation=irrigation_decision,
            fertilizer=fertilizer_decision,
            generated_at=datetime.now(),
        )

        self._last_decisions[zone_id] = result
        return result

    def get_last_decision(self, zone_id: str) -> Optional[DecisionResult]:
        return self._last_decisions.get(zone_id)

    def get_all_last_decisions(self) -> Dict[str, DecisionResult]:
        return dict(self._last_decisions)

    def trigger_immediate_check(self) -> None:
        logger.info("Manual immediate decision check triggered")
        self._check_and_make_decisions()

    @staticmethod
    def _parse_datetime(value: Optional[str]) -> datetime:
        if not value:
            return datetime.now()
        try:
            if value.endswith('Z'):
                value = value[:-1]
            return datetime.fromisoformat(value)
        except (ValueError, TypeError):
            return datetime.now()
