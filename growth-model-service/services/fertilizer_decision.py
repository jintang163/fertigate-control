import logging
import uuid
from typing import Optional
from models.growth_stage import CropVariety
from models.sensor_data import ZoneSensorData
from models.decision import FertilizerDecision, DecisionAction
from models.config import DecisionConfig
from .fertilizer_curve import FertilizerCurveCalculator

logger = logging.getLogger(__name__)


class FertilizerDecisionEngine:
    def __init__(
        self,
        fertilizer_curve: FertilizerCurveCalculator,
        decision_config: Optional[DecisionConfig] = None,
    ):
        self.fertilizer_curve = fertilizer_curve
        self.decision_config = decision_config or DecisionConfig()

    def make_decision(
        self,
        zone_data: ZoneSensorData,
        crop: Optional[CropVariety] = None,
        area_ha: float = 0.01,
    ) -> FertilizerDecision:
        zone_data.aggregate()

        if crop is None:
            return self._make_no_crop_decision(zone_data)

        stage_config = crop.get_current_stage_config()
        if not stage_config:
            return self._make_no_stage_decision(zone_data, crop)

        current_ec = zone_data.aggregated_ec
        current_ph = zone_data.aggregated_ph

        n_ratio, p_ratio, k_ratio = self.fertilizer_curve.calculate_daily_requirement(crop)
        n_amount, p_amount, k_amount, total_amount = self.fertilizer_curve.calculate_fertilizer_amount(
            crop, current_ec, current_ph, area_ha
        )

        optimal_ec = stage_config.optimal_ec
        optimal_ph = stage_config.optimal_ph
        min_ec, max_ec = self.fertilizer_curve.get_ec_thresholds(optimal_ec)
        ec_deficit = self.fertilizer_curve.calculate_ec_deficit(
            current_ec if current_ec is not None else optimal_ec, optimal_ec
        )

        action, need_fertilizer, reason = self._determine_action(
            current_ec,
            current_ph,
            min_ec,
            max_ec,
            optimal_ec,
            optimal_ph,
            ec_deficit,
            total_amount,
        )

        current_stage_config = crop.get_current_stage_config()
        stage_name = current_stage_config.name if current_stage_config else crop.current_stage

        return FertilizerDecision(
            zone_id=zone_data.zone_id,
            zone_name=zone_data.zone_name,
            crop_id=crop.crop_id,
            crop_name=crop.name,
            current_stage=crop.current_stage,
            current_stage_name=stage_name,
            current_day=crop.current_day,
            total_growth_days=crop.total_growth_days,
            growth_progress=crop.get_growth_progress(),
            current_ec=current_ec,
            current_ph=current_ph,
            optimal_ec=optimal_ec,
            optimal_ph=optimal_ph,
            min_ec=min_ec,
            max_ec=max_ec,
            n_ratio=n_ratio,
            p_ratio=p_ratio,
            k_ratio=k_ratio,
            n_amount_kg_ha=n_amount,
            p_amount_kg_ha=p_amount,
            k_amount_kg_ha=k_amount,
            ec_deficit=ec_deficit,
            action=action,
            need_fertilizer=need_fertilizer,
            fertilizer_amount_kg=round(total_amount, 3),
            reason=reason,
            decision_id=str(uuid.uuid4()),
        )

    def _determine_action(
        self,
        current_ec: Optional[float],
        current_ph: Optional[float],
        min_ec: float,
        max_ec: float,
        optimal_ec: float,
        optimal_ph: float,
        ec_deficit: float,
        total_amount: float,
    ) -> tuple[DecisionAction, bool, str]:
        if current_ec is None:
            return (
                DecisionAction.MAINTAIN,
                False,
                "无可用的EC数据，无法做出施肥决策",
            )

        if current_ec < min_ec:
            reason = (
                f"EC值({current_ec:.2f}mS/cm)低于下限({min_ec:.2f}mS/cm)，"
                f"缺肥量: {ec_deficit:.2f}mS/cm，"
                f"需施肥N:P:K = {self._format_ratio(total_amount)}"
            )
            return DecisionAction.FERTILIZE, True, reason

        elif current_ec > max_ec:
            reason = (
                f"EC值({current_ec:.2f}mS/cm)高于上限({max_ec:.2f}mS/cm)，"
                f"应停止施肥"
            )
            return DecisionAction.STOP_FERTILIZING, False, reason

        elif total_amount > 0:
            reason = (
                f"EC值({current_ec:.2f}mS/cm)在适宜范围内({min_ec:.2f}-{max_ec:.2f})，"
                f"按生长需求追肥N:P:K = {self._format_ratio(total_amount)}"
            )
            return DecisionAction.FERTILIZE, True, reason

        else:
            reason = (
                f"EC值({current_ec:.2f}mS/cm)在适宜范围内({min_ec:.2f}-{max_ec:.2f})，"
                f"无需施肥"
            )
            return DecisionAction.MAINTAIN, False, reason

    def _format_ratio(self, total_amount: float) -> str:
        if total_amount <= 0:
            return "0:0:0"
        n_pct = round(total_amount * 0.2, 1)
        p_pct = round(total_amount * 0.3, 1)
        k_pct = round(total_amount * 0.5, 1)
        return f"{n_pct}:{p_pct}:{k_pct} kg"

    def _make_no_crop_decision(self, zone_data: ZoneSensorData) -> FertilizerDecision:
        logger.warning(f"Zone {zone_data.zone_id} has no crop associated")
        return FertilizerDecision(
            zone_id=zone_data.zone_id,
            zone_name=zone_data.zone_name,
            optimal_ec=1.8,
            optimal_ph=6.5,
            min_ec=1.26,
            max_ec=2.34,
            n_ratio=0.0,
            p_ratio=0.0,
            k_ratio=0.0,
            n_amount_kg_ha=0.0,
            p_amount_kg_ha=0.0,
            k_amount_kg_ha=0.0,
            action=DecisionAction.MAINTAIN,
            need_fertilizer=False,
            fertilizer_amount_kg=0.0,
            reason="该区域未关联作物，无法进行施肥决策",
            decision_id=str(uuid.uuid4()),
        )

    def _make_no_stage_decision(
        self,
        zone_data: ZoneSensorData,
        crop: CropVariety,
    ) -> FertilizerDecision:
        logger.warning(f"Crop {crop.name} has no valid stage config")
        return FertilizerDecision(
            zone_id=zone_data.zone_id,
            zone_name=zone_data.zone_name,
            crop_id=crop.crop_id,
            crop_name=crop.name,
            current_day=crop.current_day,
            total_growth_days=crop.total_growth_days,
            growth_progress=crop.get_growth_progress(),
            optimal_ec=1.8,
            optimal_ph=6.5,
            min_ec=1.26,
            max_ec=2.34,
            n_ratio=0.0,
            p_ratio=0.0,
            k_ratio=0.0,
            n_amount_kg_ha=0.0,
            p_amount_kg_ha=0.0,
            k_amount_kg_ha=0.0,
            action=DecisionAction.MAINTAIN,
            need_fertilizer=False,
            fertilizer_amount_kg=0.0,
            reason="无效的生育期配置，使用默认阈值",
            decision_id=str(uuid.uuid4()),
        )
