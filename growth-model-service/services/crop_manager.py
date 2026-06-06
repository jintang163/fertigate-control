import logging
from datetime import date
from typing import Dict, List, Optional, Any
from models.growth_stage import CropVariety, GrowthStageConfig

logger = logging.getLogger(__name__)


class CropManager:
    def __init__(self, crops_config: Dict[str, Any] | None = None):
        self._crops: Dict[str, CropVariety] = {}
        self._crops_config = crops_config or {}
        self._load_default_crops()

    def _load_default_crops(self) -> None:
        for crop_key, crop_data in self._crops_config.items():
            try:
                growth_stages = {}
                for stage_key, stage_data in crop_data.get('growth_stages', {}).items():
                    growth_stages[stage_key] = GrowthStageConfig(**stage_data)

                crop = CropVariety(
                    name=crop_key,
                    variety=crop_data.get('variety', crop_key),
                    total_growth_days=crop_data.get('total_growth_days', 100),
                    growth_stages=growth_stages,
                )
                crop.model_post_init()
                self._crops[crop_key] = crop
                logger.info(f"Loaded crop variety: {crop.name} ({crop.variety})")
            except Exception as e:
                logger.error(f"Failed to load crop {crop_key}: {e}")

    def register_crop(self, crop: CropVariety) -> None:
        crop.model_post_init()
        self._crops[crop.name] = crop
        logger.info(f"Registered crop variety: {crop.name}")

    def get_crop(self, crop_name: str) -> Optional[CropVariety]:
        return self._crops.get(crop_name)

    def get_all_crops(self) -> List[CropVariety]:
        return list(self._crops.values())

    def list_crop_names(self) -> List[str]:
        return list(self._crops.keys())

    def get_crop_by_id(self, crop_id: str) -> Optional[CropVariety]:
        for crop in self._crops.values():
            if crop.crop_id == crop_id:
                return crop
        return None

    def set_crop_current_day(self, crop_name: str, day: int) -> Optional[CropVariety]:
        crop = self._crops.get(crop_name)
        if not crop:
            logger.warning(f"Crop not found: {crop_name}")
            return None

        crop.current_day = max(0, min(day, crop.total_growth_days - 1))
        crop.current_stage = crop.get_stage_by_day(crop.current_day)
        logger.info(f"Set {crop_name} to day {crop.current_day}, stage: {crop.current_stage}")
        return crop

    def advance_crop_day(self, crop_name: str, days: int = 1) -> Optional[str]:
        crop = self._crops.get(crop_name)
        if not crop:
            return None
        return crop.advance_day(days)

    def create_crop_instance(
        self,
        crop_name: str,
        crop_id: str,
        planting_date: Optional[date] = None,
        start_day: int = 0,
    ) -> Optional[CropVariety]:
        template = self._crops.get(crop_name)
        if not template:
            logger.warning(f"Crop template not found: {crop_name}")
            return None

        growth_stages = {}
        for stage_key, stage_config in template.growth_stages.items():
            growth_stages[stage_key] = GrowthStageConfig(
                name=stage_config.name,
                duration_days=stage_config.duration_days,
                min_humidity=stage_config.min_humidity,
                max_humidity=stage_config.max_humidity,
                optimal_ec=stage_config.optimal_ec,
                optimal_ph=stage_config.optimal_ph,
                water_requirement=stage_config.water_requirement,
                n_ratio=stage_config.n_ratio,
                p_ratio=stage_config.p_ratio,
                k_ratio=stage_config.k_ratio,
            )

        instance = CropVariety(
            crop_id=crop_id,
            name=template.name,
            variety=template.variety,
            total_growth_days=template.total_growth_days,
            planting_date=planting_date,
            growth_stages=growth_stages,
            current_day=start_day,
        )
        instance.model_post_init()
        instance.current_stage = instance.get_stage_by_day(start_day)

        self._crops[crop_id] = instance
        logger.info(f"Created crop instance: {crop_id} ({instance.name}) at day {start_day}")
        return instance

    def get_current_stage_config(self, crop_name: str) -> Optional[GrowthStageConfig]:
        crop = self._crops.get(crop_name)
        if not crop:
            return None
        return crop.get_current_stage_config()

    def get_stage_config(self, crop_name: str, stage_key: str) -> Optional[GrowthStageConfig]:
        crop = self._crops.get(crop_name)
        if not crop:
            return None
        return crop.growth_stages.get(stage_key)

    def update_crop_from_spring_boot(self, crop_data: Dict[str, Any]) -> Optional[CropVariety]:
        crop_id = str(crop_data.get('id', ''))
        crop_name = crop_data.get('name', '').lower()
        growth_stage = crop_data.get('growthStage', crop_data.get('growth_stage', ''))

        existing = self.get_crop_by_id(crop_id)
        if existing:
            existing.growth_stage = growth_stage
            existing.updated_at = date.today()
            logger.info(f"Updated existing crop instance: {crop_id}")
            return existing

        if crop_name in self._crops:
            planting_date = None
            if crop_data.get('plantingDate') or crop_data.get('planting_date'):
                pd = crop_data.get('plantingDate') or crop_data.get('planting_date')
                if isinstance(pd, str):
                    try:
                        planting_date = date.fromisoformat(pd[:10])
                    except:
                        pass

            return self.create_crop_instance(
                crop_name=crop_name,
                crop_id=crop_id,
                planting_date=planting_date,
            )

        logger.warning(f"No template found for crop: {crop_name}")
        return None
