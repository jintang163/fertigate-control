import logging
import time
import json
from typing import List, Dict, Any, Optional, Tuple
import requests
from models.config import SpringBootConfig
from models.decision import IrrigationDecision, FertilizerDecision, DecisionResult
from models.growth_stage import CropVariety

logger = logging.getLogger(__name__)


class SpringBootClient:
    def __init__(self, config: SpringBootConfig):
        self.config = config
        self.base_url = config.base_url.rstrip('/')
        self.session = requests.Session()
        self.session.headers.update({
            'Content-Type': 'application/json',
            'Accept': 'application/json',
        })

    def _request_with_retry(
        self,
        method: str,
        endpoint: str,
        **kwargs,
    ) -> Optional[requests.Response]:
        url = f"{self.base_url}/{endpoint.lstrip('/')}"
        last_error = None

        for attempt in range(self.config.retry_attempts):
            try:
                response = self.session.request(
                    method,
                    url,
                    timeout=self.config.timeout,
                    **kwargs,
                )
                response.raise_for_status()
                return response
            except requests.RequestException as e:
                last_error = e
                wait_time = self.config.retry_delay * (2 ** attempt)
                logger.warning(
                    f"Request {method} {url} failed (attempt {attempt + 1}/{self.config.retry_attempts}): {e}. "
                    f"Retrying in {wait_time}s..."
                )
                if attempt < self.config.retry_attempts - 1:
                    time.sleep(wait_time)

        logger.error(f"Request {method} {url} failed permanently: {last_error}")
        return None

    def get_zones(self) -> List[Dict[str, Any]]:
        response = self._request_with_retry('GET', '/zones')
        if response and response.status_code == 200:
            data = response.json()
            logger.info(f"Fetched {len(data)} zones from Spring Boot")
            return data
        return []

    def get_zone_by_id(self, zone_id: str) -> Optional[Dict[str, Any]]:
        response = self._request_with_retry('GET', f'/zones/{zone_id}')
        if response and response.status_code == 200:
            return response.json()
        return None

    def get_crops(self) -> List[Dict[str, Any]]:
        response = self._request_with_retry('GET', '/crops')
        if response and response.status_code == 200:
            data = response.json()
            logger.info(f"Fetched {len(data)} crops from Spring Boot")
            return data
        return []

    def get_crop_by_id(self, crop_id: str) -> Optional[Dict[str, Any]]:
        response = self._request_with_retry('GET', f'/crops/{crop_id}')
        if response and response.status_code == 200:
            return response.json()
        return None

    def get_sensor_data(self, device_code: Optional[str] = None) -> List[Dict[str, Any]]:
        endpoint = '/sensor-data/latest'
        if device_code:
            endpoint += f'?deviceCode={device_code}'
        response = self._request_with_retry('GET', endpoint)
        if response and response.status_code == 200:
            return response.json()
        return []

    def get_sensor_data_by_zone(self, zone_id: str) -> List[Dict[str, Any]]:
        response = self._request_with_retry('GET', f'/sensor-data/zone/{zone_id}/latest')
        if response and response.status_code == 200:
            return response.json()
        return []

    def get_threshold_strategies(self, zone_id: Optional[str] = None) -> List[Dict[str, Any]]:
        endpoint = '/threshold-strategies'
        if zone_id:
            endpoint += f'?zoneId={zone_id}'
        response = self._request_with_retry('GET', endpoint)
        if response and response.status_code == 200:
            return response.json()
        return []

    def get_growth_stage_records(self, crop_id: Optional[str] = None, zone_id: Optional[str] = None) -> List[Dict[str, Any]]:
        endpoint = '/growth-stage'
        params = {}
        if crop_id:
            params['cropId'] = crop_id
        if zone_id:
            params['zoneId'] = zone_id
        response = self._request_with_retry('GET', endpoint, params=params)
        if response and response.status_code == 200:
            return response.json()
        return []

    def callback_irrigation_decision(self, decision: IrrigationDecision) -> Tuple[bool, str]:
        payload = self._serialize_irrigation_decision(decision)
        logger.info(f"Callback irrigation decision for zone {decision.zone_id}: {decision.action}")

        response = self._request_with_retry(
            'POST',
            '/irrigation/decision/callback',
            json=payload,
        )

        if response and response.status_code in (200, 201):
            result = response.json()
            logger.info(f"Irrigation decision callback success: {result}")
            return True, str(result)
        else:
            error_msg = f"Irrigation decision callback failed"
            if response:
                error_msg += f" (HTTP {response.status_code}: {response.text})"
            logger.error(error_msg)
            return False, error_msg

    def callback_fertilizer_decision(self, decision: FertilizerDecision) -> Tuple[bool, str]:
        payload = self._serialize_fertilizer_decision(decision)
        logger.info(f"Callback fertilizer decision for zone {decision.zone_id}: {decision.action}")

        response = self._request_with_retry(
            'POST',
            '/irrigation/fertilizer/callback',
            json=payload,
        )

        if response and response.status_code in (200, 201):
            result = response.json()
            logger.info(f"Fertilizer decision callback success: {result}")
            return True, str(result)
        else:
            error_msg = f"Fertilizer decision callback failed"
            if response:
                error_msg += f" (HTTP {response.status_code}: {response.text})"
            logger.error(error_msg)
            return False, error_msg

    def callback_decision_result(self, result: DecisionResult) -> Tuple[bool, str]:
        payload = {
            'zoneId': result.zone_id,
            'zoneName': result.zone_name,
            'generatedAt': result.generated_at.isoformat(),
            'irrigation': self._serialize_irrigation_decision(result.irrigation) if result.irrigation else None,
            'fertilizer': self._serialize_fertilizer_decision(result.fertilizer) if result.fertilizer else None,
        }

        logger.info(f"Callback decision result for zone {result.zone_id}")

        response = self._request_with_retry(
            'POST',
            '/irrigation/decision/result',
            json=payload,
        )

        if response and response.status_code in (200, 201):
            callback_result = response.json()
            logger.info(f"Decision result callback success: {callback_result}")
            return True, str(callback_result)
        else:
            error_msg = f"Decision result callback failed"
            if response:
                error_msg += f" (HTTP {response.status_code}: {response.text})"
            logger.error(error_msg)
            return False, error_msg

    def control_valve(
        self,
        valve_id: str,
        open_valve: bool,
        reason: str,
        duration_seconds: Optional[int] = None,
    ) -> Tuple[bool, str]:
        params = {
            'open': str(open_valve).lower(),
            'reason': reason,
        }
        if duration_seconds:
            params['durationSeconds'] = duration_seconds

        logger.info(f"Control valve {valve_id}: open={open_valve}, reason={reason}")

        response = self._request_with_retry(
            'POST',
            f'/irrigation/valve/{valve_id}/control',
            params=params,
        )

        if response and response.status_code == 200:
            result = response.json()
            logger.info(f"Valve control success: {result}")
            return True, str(result)
        else:
            error_msg = f"Valve control failed for {valve_id}"
            if response:
                error_msg += f" (HTTP {response.status_code}: {response.text})"
            logger.error(error_msg)
            return False, error_msg

    def control_fertilizer_pump(
        self,
        pump_id: str,
        enable: bool,
        fertilizer_amount: float,
        n_ratio: float,
        p_ratio: float,
        k_ratio: float,
        reason: str,
    ) -> Tuple[bool, str]:
        payload = {
            'pumpId': pump_id,
            'enable': enable,
            'fertilizerAmount': fertilizer_amount,
            'nRatio': n_ratio,
            'pRatio': p_ratio,
            'kRatio': k_ratio,
            'reason': reason,
        }

        logger.info(f"Control fertilizer pump {pump_id}: enable={enable}, amount={fertilizer_amount}kg")

        response = self._request_with_retry(
            'POST',
            '/irrigation/fertilizer-pump/control',
            json=payload,
        )

        if response and response.status_code == 200:
            result = response.json()
            logger.info(f"Fertilizer pump control success: {result}")
            return True, str(result)
        else:
            error_msg = f"Fertilizer pump control failed for {pump_id}"
            if response:
                error_msg += f" (HTTP {response.status_code}: {response.text})"
            logger.error(error_msg)
            return False, error_msg

    def _serialize_irrigation_decision(self, decision: IrrigationDecision) -> Dict[str, Any]:
        return {
            'decisionId': decision.decision_id,
            'zoneId': decision.zone_id,
            'zoneName': decision.zone_name,
            'cropId': decision.crop_id,
            'cropName': decision.crop_name,
            'currentStage': decision.current_stage,
            'currentStageName': decision.current_stage_name,
            'currentDay': decision.current_day,
            'totalGrowthDays': decision.total_growth_days,
            'growthProgress': decision.growth_progress,
            'currentHumidity': decision.current_humidity,
            'minHumidity': decision.min_humidity,
            'maxHumidity': decision.max_humidity,
            'adjustedMinHumidity': decision.adjusted_min_humidity,
            'adjustedMaxHumidity': decision.adjusted_max_humidity,
            'waterRequirementMm': decision.water_requirement_mm,
            'waterDeficit': decision.water_deficit,
            'action': decision.action.value,
            'needIrrigation': decision.need_irrigation,
            'durationSeconds': decision.duration_seconds,
            'waterAmountLiters': decision.water_amount_liters,
            'reason': decision.reason,
            'weatherFactor': decision.weather_factor,
            'growthStageFactor': decision.growth_stage_factor,
            'rainfall': decision.rainfall,
            'timestamp': decision.timestamp.isoformat(),
        }

    def _serialize_fertilizer_decision(self, decision: FertilizerDecision) -> Dict[str, Any]:
        return {
            'decisionId': decision.decision_id,
            'zoneId': decision.zone_id,
            'zoneName': decision.zone_name,
            'cropId': decision.crop_id,
            'cropName': decision.crop_name,
            'currentStage': decision.current_stage,
            'currentStageName': decision.current_stage_name,
            'currentDay': decision.current_day,
            'totalGrowthDays': decision.total_growth_days,
            'growthProgress': decision.growth_progress,
            'currentEc': decision.current_ec,
            'currentPh': decision.current_ph,
            'optimalEc': decision.optimal_ec,
            'optimalPh': decision.optimal_ph,
            'minEc': decision.min_ec,
            'maxEc': decision.max_ec,
            'nRatio': decision.n_ratio,
            'pRatio': decision.p_ratio,
            'kRatio': decision.k_ratio,
            'nAmountKgHa': decision.n_amount_kg_ha,
            'pAmountKgHa': decision.p_amount_kg_ha,
            'kAmountKgHa': decision.k_amount_kg_ha,
            'ecDeficit': decision.ec_deficit,
            'action': decision.action.value,
            'needFertilizer': decision.need_fertilizer,
            'fertilizerAmountKg': decision.fertilizer_amount_kg,
            'reason': decision.reason,
            'timestamp': decision.timestamp.isoformat(),
        }

    def sync_crops_from_spring_boot(self, crop_manager) -> int:
        crops_data = self.get_crops()
        synced_count = 0

        for crop_data in crops_data:
            try:
                crop = crop_manager.update_crop_from_spring_boot(crop_data)
                if crop:
                    synced_count += 1
            except Exception as e:
                logger.error(f"Failed to sync crop {crop_data.get('id')}: {e}")

        logger.info(f"Synced {synced_count}/{len(crops_data)} crops from Spring Boot")
        return synced_count

    def get_zone_area(self, zone_id: str) -> float:
        zone_data = self.get_zone_by_id(zone_id)
        if zone_data and 'area' in zone_data and zone_data['area'] is not None:
            try:
                return float(zone_data['area'])
            except (ValueError, TypeError):
                pass
        return 100.0

    def close(self) -> None:
        self.session.close()
        logger.info("Spring Boot client session closed")
