import logging
import numpy as np
from typing import List, Optional, Tuple
from models.growth_stage import CropVariety
from models.decision import FertilizerRequirementPoint

logger = logging.getLogger(__name__)


class FertilizerCurveCalculator:
    def __init__(self):
        self._interpolation_method = 'sigmoid'
        self._base_daily_rate = 2.5

    def calculate_daily_requirement(
        self,
        crop: CropVariety,
        day: Optional[int] = None,
    ) -> Tuple[float, float, float]:
        if day is None:
            day = crop.current_day

        stage_config = crop.get_current_stage_config()
        if not stage_config:
            logger.warning(f"No stage config found for day {day}")
            return 0.0, 0.0, 0.0

        stage_key = crop.get_stage_by_day(day)
        if not stage_key or stage_key not in crop.growth_stages:
            return (
                stage_config.n_ratio,
                stage_config.p_ratio,
                stage_config.k_ratio,
            )

        stage = crop.growth_stages[stage_key]
        day_in_stage = day - (stage.start_day or 0)
        total_days_in_stage = stage.duration_days

        curve_factor = self._get_curve_factor(day_in_stage, total_days_in_stage)

        return (
            round(stage_config.n_ratio * curve_factor, 3),
            round(stage_config.p_ratio * curve_factor, 3),
            round(stage_config.k_ratio * curve_factor, 3),
        )

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

    def _calculate_absolute_amounts(
        self,
        n_ratio: float,
        p_ratio: float,
        k_ratio: float,
        area_ha: float = 1.0,
    ) -> Tuple[float, float, float]:
        total_ratio = n_ratio + p_ratio + k_ratio
        if total_ratio <= 0:
            return 0.0, 0.0, 0.0

        daily_total = self._base_daily_rate * area_ha
        n_amount = round(daily_total * (n_ratio / total_ratio), 3)
        p_amount = round(daily_total * (p_ratio / total_ratio), 3)
        k_amount = round(daily_total * (k_ratio / total_ratio), 3)

        return n_amount, p_amount, k_amount

    def generate_curve(
        self,
        crop: CropVariety,
        start_day: int = 0,
        end_day: Optional[int] = None,
        area_ha: float = 1.0,
    ) -> List[FertilizerRequirementPoint]:
        if end_day is None:
            end_day = crop.total_growth_days - 1

        curve = []
        for day in range(start_day, end_day + 1):
            stage_key = crop.get_stage_by_day(day)
            if not stage_key or stage_key not in crop.growth_stages:
                continue

            stage_config = crop.growth_stages[stage_key]
            n_ratio, p_ratio, k_ratio = self.calculate_daily_requirement(crop, day)
            n_amount, p_amount, k_amount = self._calculate_absolute_amounts(
                n_ratio, p_ratio, k_ratio, area_ha
            )
            growth_progress = round(day / max(crop.total_growth_days, 1), 4)

            curve.append(FertilizerRequirementPoint(
                day=day,
                stage=stage_key,
                stage_name=stage_config.name,
                n_ratio=n_ratio,
                p_ratio=p_ratio,
                k_ratio=k_ratio,
                n_amount_kg_ha=n_amount,
                p_amount_kg_ha=p_amount,
                k_amount_kg_ha=k_amount,
                optimal_ec=stage_config.optimal_ec,
                optimal_ph=stage_config.optimal_ph,
                growth_progress=growth_progress,
            ))

        return curve

    def calculate_stage_curve(
        self,
        crop: CropVariety,
        stage_key: str,
        area_ha: float = 1.0,
    ) -> List[FertilizerRequirementPoint]:
        if stage_key not in crop.growth_stages:
            logger.warning(f"Stage {stage_key} not found in crop {crop.name}")
            return []

        stage = crop.growth_stages[stage_key]
        start_day = stage.start_day or 0
        end_day = stage.end_day or start_day

        return self.generate_curve(crop, start_day, end_day, area_ha)

    def calculate_total_fertilizer_requirement(
        self,
        crop: CropVariety,
        start_day: int = 0,
        end_day: Optional[int] = None,
        area_ha: float = 1.0,
    ) -> Tuple[float, float, float]:
        curve = self.generate_curve(crop, start_day, end_day, area_ha)
        total_n = sum(p.n_amount_kg_ha for p in curve)
        total_p = sum(p.p_amount_kg_ha for p in curve)
        total_k = sum(p.k_amount_kg_ha for p in curve)
        return round(total_n, 2), round(total_p, 2), round(total_k, 2)

    def calculate_remaining_fertilizer_requirement(
        self,
        crop: CropVariety,
        area_ha: float = 1.0,
    ) -> Tuple[float, float, float]:
        return self.calculate_total_fertilizer_requirement(
            crop, crop.current_day, crop.total_growth_days - 1, area_ha
        )

    def get_current_fertilizer_amounts(
        self,
        crop: CropVariety,
        area_ha: float = 1.0,
        day: Optional[int] = None,
    ) -> Tuple[float, float, float]:
        n_ratio, p_ratio, k_ratio = self.calculate_daily_requirement(crop, day)
        return self._calculate_absolute_amounts(n_ratio, p_ratio, k_ratio, area_ha)

    def calculate_ec_factor(self, current_ec: float, optimal_ec: float) -> float:
        if optimal_ec <= 0:
            return 1.0

        ec_ratio = current_ec / optimal_ec

        if ec_ratio < 0.5:
            return 1.5
        elif ec_ratio < 0.8:
            return 1.2
        elif ec_ratio < 1.0:
            return 1.0
        elif ec_ratio < 1.2:
            return 0.8
        elif ec_ratio < 1.5:
            return 0.5
        else:
            return 0.0

    def calculate_ph_factor(self, current_ph: float, optimal_ph: float) -> float:
        ph_deviation = abs(current_ph - optimal_ph)

        if ph_deviation < 0.3:
            return 1.0
        elif ph_deviation < 0.5:
            return 0.9
        elif ph_deviation < 0.8:
            return 0.7
        elif ph_deviation < 1.0:
            return 0.5
        else:
            return 0.3

    def calculate_fertilizer_amount(
        self,
        crop: CropVariety,
        current_ec: Optional[float],
        current_ph: Optional[float],
        area_ha: float = 1.0,
        day: Optional[int] = None,
    ) -> Tuple[float, float, float, float]:
        stage_config = crop.get_current_stage_config()
        if not stage_config:
            return 0.0, 0.0, 0.0, 0.0

        n_amount, p_amount, k_amount = self.get_current_fertilizer_amounts(
            crop, area_ha, day
        )

        ec_factor = self.calculate_ec_factor(
            current_ec if current_ec is not None else stage_config.optimal_ec,
            stage_config.optimal_ec,
        )

        ph_factor = self.calculate_ph_factor(
            current_ph if current_ph is not None else stage_config.optimal_ph,
            stage_config.optimal_ph,
        )

        combined_factor = ec_factor * ph_factor

        adjusted_n = round(n_amount * combined_factor, 3)
        adjusted_p = round(p_amount * combined_factor, 3)
        adjusted_k = round(k_amount * combined_factor, 3)
        total = round(adjusted_n + adjusted_p + adjusted_k, 3)

        return adjusted_n, adjusted_p, adjusted_k, total

    def calculate_ec_deficit(
        self,
        current_ec: float,
        optimal_ec: float,
    ) -> float:
        deficit = optimal_ec - current_ec
        return round(max(0, deficit), 3)

    def get_ec_thresholds(self, optimal_ec: float) -> Tuple[float, float]:
        min_ec = round(optimal_ec * 0.7, 2)
        max_ec = round(optimal_ec * 1.3, 2)
        return min_ec, max_ec
