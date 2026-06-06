from datetime import datetime
from typing import Dict, Optional, List
from enum import Enum
from pydantic import BaseModel, Field


class DecisionAction(str, Enum):
    OPEN = "open"
    CLOSE = "close"
    MAINTAIN = "maintain"
    FERTILIZE = "fertilize"
    STOP_FERTILIZING = "stop_fertilizing"


class WaterRequirementPoint(BaseModel):
    day: int
    stage: str
    stage_name: str
    water_requirement_mm: float
    min_humidity: float
    max_humidity: float
    growth_progress: float


class FertilizerRequirementPoint(BaseModel):
    day: int
    stage: str
    stage_name: str
    n_ratio: float
    p_ratio: float
    k_ratio: float
    n_amount_kg_ha: float
    p_amount_kg_ha: float
    k_amount_kg_ha: float
    optimal_ec: float
    optimal_ph: float
    growth_progress: float


class IrrigationDecision(BaseModel):
    zone_id: str
    zone_name: str
    crop_id: Optional[str] = None
    crop_name: Optional[str] = None
    current_stage: Optional[str] = None
    current_stage_name: Optional[str] = None
    current_day: int = 0
    total_growth_days: int = 0
    growth_progress: float = 0.0

    current_humidity: Optional[float] = None
    min_humidity: float
    max_humidity: float
    adjusted_min_humidity: float
    adjusted_max_humidity: float

    water_requirement_mm: float
    water_deficit: Optional[float] = None

    action: DecisionAction
    need_irrigation: bool
    duration_seconds: int
    water_amount_liters: float
    reason: str

    weather_factor: float = 1.0
    growth_stage_factor: float = 1.0
    rainfall: Optional[float] = None

    timestamp: datetime = Field(default_factory=datetime.now)
    decision_id: Optional[str] = None


class FertilizerDecision(BaseModel):
    zone_id: str
    zone_name: str
    crop_id: Optional[str] = None
    crop_name: Optional[str] = None
    current_stage: Optional[str] = None
    current_stage_name: Optional[str] = None
    current_day: int = 0
    total_growth_days: int = 0
    growth_progress: float = 0.0

    current_ec: Optional[float] = None
    current_ph: Optional[float] = None
    optimal_ec: float
    optimal_ph: float
    min_ec: float
    max_ec: float

    n_ratio: float
    p_ratio: float
    k_ratio: float
    n_amount_kg_ha: float
    p_amount_kg_ha: float
    k_amount_kg_ha: float

    ec_deficit: Optional[float] = None
    action: DecisionAction
    need_fertilizer: bool
    fertilizer_amount_kg: float
    reason: str

    timestamp: datetime = Field(default_factory=datetime.now)
    decision_id: Optional[str] = None


class DecisionResult(BaseModel):
    irrigation: Optional[IrrigationDecision] = None
    fertilizer: Optional[FertilizerDecision] = None
    zone_id: str
    zone_name: str
    generated_at: datetime = Field(default_factory=datetime.now)
    callback_success: Optional[bool] = None
    callback_message: Optional[str] = None
