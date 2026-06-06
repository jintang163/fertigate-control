from datetime import date, datetime
from typing import Dict, Optional, List
from enum import Enum
from pydantic import BaseModel, Field


class GrowthStage(str, Enum):
    SEEDLING = "seedling"
    VEGETATIVE = "vegetative"
    TILLERING = "tillering"
    JOINTING = "jointing"
    HEADING = "heading"
    FLOWERING = "flowering"
    FRUITING = "fruiting"
    FILLING = "filling"
    RIPENING = "ripening"
    DORMANT = "dormant"


class GrowthStageConfig(BaseModel):
    name: str
    duration_days: int
    min_humidity: float = Field(ge=0, le=100)
    max_humidity: float = Field(ge=0, le=100)
    optimal_ec: float = Field(gt=0)
    optimal_ph: float = Field(gt=0, lt=14)
    water_requirement: float = Field(ge=0)
    n_ratio: float = Field(ge=0)
    p_ratio: float = Field(ge=0)
    k_ratio: float = Field(ge=0)
    start_day: Optional[int] = None
    end_day: Optional[int] = None


class CropVariety(BaseModel):
    crop_id: Optional[str] = None
    name: str
    variety: str
    total_growth_days: int
    planting_date: Optional[date] = None
    growth_stages: Dict[str, GrowthStageConfig]
    current_stage: Optional[str] = None
    current_day: int = 0
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now)

    def model_post_init(self) -> None:
        self._calculate_stage_boundaries()

    def _calculate_stage_boundaries(self) -> None:
        current_day = 0
        for stage_key, stage_config in self.growth_stages.items():
            stage_config.start_day = current_day
            current_day += stage_config.duration_days
            stage_config.end_day = current_day - 1

    def get_stage_by_day(self, day: int) -> Optional[str]:
        for stage_key, stage_config in self.growth_stages.items():
            if stage_config.start_day <= day <= stage_config.end_day:
                return stage_key
        return None

    def get_current_stage_config(self) -> Optional[GrowthStageConfig]:
        if not self.current_stage:
            self.current_stage = self.get_stage_by_day(self.current_day)
        return self.growth_stages.get(self.current_stage)

    def advance_day(self, days: int = 1) -> str:
        self.current_day = min(self.current_day + days, self.total_growth_days - 1)
        new_stage = self.get_stage_by_day(self.current_day)
        if new_stage and new_stage != self.current_stage:
            self.current_stage = new_stage
        self.updated_at = datetime.now()
        return self.current_stage or "unknown"

    def get_days_remaining(self) -> int:
        return max(0, self.total_growth_days - self.current_day - 1)

    def get_growth_progress(self) -> float:
        return round(self.current_day / max(self.total_growth_days, 1), 4)
