#!/usr/bin/env python3
import logging
import os
import signal
import sys
import argparse
from typing import List, Optional, Dict, Any
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Query, Body
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from models import (
    CropVariety,
    GrowthStageConfig,
    WaterRequirementPoint,
    FertilizerRequirementPoint,
    IrrigationDecision,
    FertilizerDecision,
    DecisionResult,
    SensorData,
    ServiceConfig,
)
from services import (
    ConfigLoader,
    CropManager,
    WaterCurveCalculator,
    FertilizerCurveCalculator,
    IrrigationDecisionEngine,
    FertilizerDecisionEngine,
    SpringBootClient,
    DecisionScheduler,
)

__version__ = "1.0.0"

os.makedirs('logs', exist_ok=True)

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler('logs/growth-model-service.log')
    ]
)

logger = logging.getLogger(__name__)


class AppState:
    def __init__(self):
        self.config: Optional[ServiceConfig] = None
        self.config_loader: Optional[ConfigLoader] = None
        self.crop_manager: Optional[CropManager] = None
        self.water_curve: Optional[WaterCurveCalculator] = None
        self.fertilizer_curve: Optional[FertilizerCurveCalculator] = None
        self.irrigation_engine: Optional[IrrigationDecisionEngine] = None
        self.fertilizer_engine: Optional[FertilizerDecisionEngine] = None
        self.spring_boot_client: Optional[SpringBootClient] = None
        self.scheduler: Optional[DecisionScheduler] = None


app_state = AppState()


@asynccontextmanager
async def lifespan(app: FastAPI):
    parser = argparse.ArgumentParser(description='Growth Model Service for Fertigate Control System')
    parser.add_argument('--config', default='config.yaml', help='Path to configuration file')
    parser.add_argument('--no-scheduler', action='store_true', help='Disable automatic decision scheduler')
    args = parser.parse_args()

    logger.info(f"Starting Growth Model Service v{__version__}")

    app_state.config_loader = ConfigLoader(args.config)
    app_state.config = app_state.config_loader.load()

    logging.getLogger().setLevel(app_state.config.log_level)

    app_state.crop_manager = CropManager(app_state.config.crops_config)
    logger.info(f"Loaded {len(app_state.crop_manager.get_all_crops())} crop varieties")

    app_state.water_curve = WaterCurveCalculator()
    app_state.fertilizer_curve = FertilizerCurveCalculator()

    app_state.irrigation_engine = IrrigationDecisionEngine(
        app_state.water_curve, app_state.config.decision
    )
    app_state.fertilizer_engine = FertilizerDecisionEngine(
        app_state.fertilizer_curve, app_state.config.decision
    )

    app_state.spring_boot_client = SpringBootClient(app_state.config.spring_boot)

    if not args.no_scheduler and app_state.config.decision.enable_auto_callback:
        app_state.scheduler = DecisionScheduler(
            app_state.config,
            app_state.crop_manager,
            app_state.water_curve,
            app_state.fertilizer_curve,
            app_state.spring_boot_client,
        )
        app_state.scheduler.start()
        logger.info("Automatic decision scheduler started")

    def _signal_handler(signum, frame):
        logger.info(f"Received signal {signum}, shutting down...")
        shutdown()
        sys.exit(0)

    signal.signal(signal.SIGINT, _signal_handler)
    signal.signal(signal.SIGTERM, _signal_handler)

    yield

    shutdown()


def shutdown():
    logger.info("Shutting down Growth Model Service...")
    if app_state.scheduler:
        app_state.scheduler.stop()
    if app_state.spring_boot_client:
        app_state.spring_boot_client.close()
    logger.info("Growth Model Service shutdown complete")


app = FastAPI(
    title="Growth Model Service API",
    description="作物生长模型服务 - 提供作物品种与生育期管理、需水需肥曲线计算、智能灌溉施肥决策",
    version=__version__,
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class ZoneDecisionRequest(BaseModel):
    zone_id: str
    zone_name: str
    crop_id: Optional[str] = None
    area_m2: Optional[float] = None
    sensor_data: Optional[List[SensorData]] = None


class ManualDecisionRequest(BaseModel):
    zone_id: str
    zone_name: str
    crop_id: Optional[str] = None
    area_m2: Optional[float] = None
    humidity: Optional[float] = None
    ec: Optional[float] = None
    ph: Optional[float] = None
    temperature: Optional[float] = None
    rainfall: Optional[float] = None
    wind_speed: Optional[float] = None


@app.get("/")
async def root():
    return {
        "service": "growth-model-service",
        "version": __version__,
        "status": "running",
        "crops_loaded": len(app_state.crop_manager.get_all_crops()) if app_state.crop_manager else 0,
        "scheduler_running": app_state.scheduler.running if app_state.scheduler else False,
    }


@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "service": "growth-model-service",
        "version": __version__,
    }


@app.get("/crops", response_model=List[CropVariety])
async def list_crops():
    if not app_state.crop_manager:
        raise HTTPException(status_code=500, detail="Crop manager not initialized")
    return app_state.crop_manager.get_all_crops()


@app.get("/crops/{crop_name}", response_model=CropVariety)
async def get_crop(crop_name: str):
    if not app_state.crop_manager:
        raise HTTPException(status_code=500, detail="Crop manager not initialized")
    crop = app_state.crop_manager.get_crop(crop_name)
    if not crop:
        raise HTTPException(status_code=404, detail=f"Crop '{crop_name}' not found")
    return crop


@app.post("/crops/{crop_name}/advance-day")
async def advance_crop_day(crop_name: str, days: int = 1):
    if not app_state.crop_manager:
        raise HTTPException(status_code=500, detail="Crop manager not initialized")
    new_stage = app_state.crop_manager.advance_crop_day(crop_name, days)
    if not new_stage:
        raise HTTPException(status_code=404, detail=f"Crop '{crop_name}' not found")
    return {
        "crop_name": crop_name,
        "days_advanced": days,
        "new_stage": new_stage,
    }


@app.put("/crops/{crop_name}/set-day")
async def set_crop_day(crop_name: str, day: int = Query(..., ge=0)):
    if not app_state.crop_manager:
        raise HTTPException(status_code=500, detail="Crop manager not initialized")
    crop = app_state.crop_manager.set_crop_current_day(crop_name, day)
    if not crop:
        raise HTTPException(status_code=404, detail=f"Crop '{crop_name}' not found")
    return {
        "crop_name": crop_name,
        "current_day": crop.current_day,
        "current_stage": crop.current_stage,
    }


@app.get("/crops/{crop_name}/stages")
async def get_crop_stages(crop_name: str):
    if not app_state.crop_manager:
        raise HTTPException(status_code=500, detail="Crop manager not initialized")
    crop = app_state.crop_manager.get_crop(crop_name)
    if not crop:
        raise HTTPException(status_code=404, detail=f"Crop '{crop_name}' not found")

    stages = []
    for stage_key, stage_config in crop.growth_stages.items():
        stages.append({
            "stage_key": stage_key,
            "stage_name": stage_config.name,
            "start_day": stage_config.start_day,
            "end_day": stage_config.end_day,
            "duration_days": stage_config.duration_days,
            "min_humidity": stage_config.min_humidity,
            "max_humidity": stage_config.max_humidity,
            "optimal_ec": stage_config.optimal_ec,
            "optimal_ph": stage_config.optimal_ph,
            "water_requirement": stage_config.water_requirement,
            "n_ratio": stage_config.n_ratio,
            "p_ratio": stage_config.p_ratio,
            "k_ratio": stage_config.k_ratio,
        })
    return stages


@app.get("/crops/{crop_name}/water-curve", response_model=List[WaterRequirementPoint])
async def get_water_curve(
    crop_name: str,
    start_day: int = Query(0, ge=0),
    end_day: Optional[int] = None,
):
    if not app_state.crop_manager or not app_state.water_curve:
        raise HTTPException(status_code=500, detail="Services not initialized")

    crop = app_state.crop_manager.get_crop(crop_name)
    if not crop:
        raise HTTPException(status_code=404, detail=f"Crop '{crop_name}' not found")

    if end_day is None:
        end_day = crop.total_growth_days - 1

    return app_state.water_curve.generate_curve(crop, start_day, end_day)


@app.get("/crops/{crop_name}/water-requirement/today")
async def get_daily_water_requirement(crop_name: str, day: Optional[int] = None):
    if not app_state.crop_manager or not app_state.water_curve:
        raise HTTPException(status_code=500, detail="Services not initialized")

    crop = app_state.crop_manager.get_crop(crop_name)
    if not crop:
        raise HTTPException(status_code=404, detail=f"Crop '{crop_name}' not found")

    target_day = day if day is not None else crop.current_day
    water_req = app_state.water_curve.calculate_daily_requirement(crop, target_day)
    adjusted_min, adjusted_max = app_state.water_curve.calculate_adjusted_thresholds(crop, target_day)

    return {
        "crop_name": crop_name,
        "day": target_day,
        "stage": crop.get_stage_by_day(target_day),
        "water_requirement_mm": water_req,
        "adjusted_min_humidity": adjusted_min,
        "adjusted_max_humidity": adjusted_max,
    }


@app.get("/crops/{crop_name}/fertilizer-curve", response_model=List[FertilizerRequirementPoint])
async def get_fertilizer_curve(
    crop_name: str,
    start_day: int = Query(0, ge=0),
    end_day: Optional[int] = None,
    area_ha: float = Query(1.0, gt=0),
):
    if not app_state.crop_manager or not app_state.fertilizer_curve:
        raise HTTPException(status_code=500, detail="Services not initialized")

    crop = app_state.crop_manager.get_crop(crop_name)
    if not crop:
        raise HTTPException(status_code=404, detail=f"Crop '{crop_name}' not found")

    if end_day is None:
        end_day = crop.total_growth_days - 1

    return app_state.fertilizer_curve.generate_curve(crop, start_day, end_day, area_ha)


@app.get("/crops/{crop_name}/fertilizer-requirement/today")
async def get_daily_fertilizer_requirement(
    crop_name: str,
    day: Optional[int] = None,
    area_ha: float = Query(1.0, gt=0),
    current_ec: Optional[float] = None,
    current_ph: Optional[float] = None,
):
    if not app_state.crop_manager or not app_state.fertilizer_curve:
        raise HTTPException(status_code=500, detail="Services not initialized")

    crop = app_state.crop_manager.get_crop(crop_name)
    if not crop:
        raise HTTPException(status_code=404, detail=f"Crop '{crop_name}' not found")

    target_day = day if day is not None else crop.current_day
    n_ratio, p_ratio, k_ratio = app_state.fertilizer_curve.calculate_daily_requirement(crop, target_day)
    n_amount, p_amount, k_amount, total = app_state.fertilizer_curve.calculate_fertilizer_amount(
        crop, current_ec, current_ph, area_ha, target_day
    )

    stage_config = crop.get_current_stage_config()
    min_ec, max_ec = app_state.fertilizer_curve.get_ec_thresholds(
        stage_config.optimal_ec if stage_config else 1.8
    )

    return {
        "crop_name": crop_name,
        "day": target_day,
        "stage": crop.get_stage_by_day(target_day),
        "n_ratio": n_ratio,
        "p_ratio": p_ratio,
        "k_ratio": k_ratio,
        "n_amount_kg_ha": n_amount,
        "p_amount_kg_ha": p_amount,
        "k_amount_kg_ha": k_amount,
        "total_fertilizer_kg": total,
        "min_ec": min_ec,
        "max_ec": max_ec,
        "optimal_ec": stage_config.optimal_ec if stage_config else 1.8,
        "optimal_ph": stage_config.optimal_ph if stage_config else 6.5,
    }


@app.post("/decision/irrigation", response_model=IrrigationDecision)
async def make_irrigation_decision(request: ZoneDecisionRequest):
    if not app_state.crop_manager or not app_state.irrigation_engine:
        raise HTTPException(status_code=500, detail="Services not initialized")

    from models.sensor_data import ZoneSensorData

    crop = None
    if request.crop_id:
        crop = app_state.crop_manager.get_crop_by_id(request.crop_id)
        if not crop:
            crop = app_state.crop_manager.get_crop(request.crop_id.lower())

    zone_sensor_data = ZoneSensorData(
        zone_id=request.zone_id,
        zone_name=request.zone_name,
        crop_id=request.crop_id,
        sensors=request.sensor_data or [],
    )
    zone_sensor_data.aggregate()

    area_m2 = request.area_m2 or 100.0

    return app_state.irrigation_engine.make_decision(
        zone_sensor_data, crop, area_m2
    )


@app.post("/decision/fertilizer", response_model=FertilizerDecision)
async def make_fertilizer_decision(request: ZoneDecisionRequest):
    if not app_state.crop_manager or not app_state.fertilizer_engine:
        raise HTTPException(status_code=500, detail="Services not initialized")

    from models.sensor_data import ZoneSensorData

    crop = None
    if request.crop_id:
        crop = app_state.crop_manager.get_crop_by_id(request.crop_id)
        if not crop:
            crop = app_state.crop_manager.get_crop(request.crop_id.lower())

    zone_sensor_data = ZoneSensorData(
        zone_id=request.zone_id,
        zone_name=request.zone_name,
        crop_id=request.crop_id,
        sensors=request.sensor_data or [],
    )
    zone_sensor_data.aggregate()

    area_m2 = request.area_m2 or 100.0
    area_ha = area_m2 / 10000.0

    return app_state.fertilizer_engine.make_decision(
        zone_sensor_data, crop, area_ha
    )


@app.post("/decision/manual", response_model=DecisionResult)
async def make_manual_decision(request: ManualDecisionRequest):
    if not app_state.crop_manager or not app_state.irrigation_engine or not app_state.fertilizer_engine:
        raise HTTPException(status_code=500, detail="Services not initialized")

    from models.sensor_data import ZoneSensorData, SensorData
    from datetime import datetime

    sensor_data = []
    if any([request.humidity, request.ec, request.ph, request.temperature]):
        sensor_data.append(SensorData(
            device_code="MANUAL-INPUT",
            timestamp=datetime.now(),
            humidity=request.humidity,
            ec=request.ec,
            ph=request.ph,
            temperature=request.temperature,
            rainfall=request.rainfall,
            wind_speed=request.wind_speed,
        ))

    crop = None
    if request.crop_id:
        crop = app_state.crop_manager.get_crop_by_id(request.crop_id)
        if not crop:
            crop = app_state.crop_manager.get_crop(request.crop_id.lower())

    zone_sensor_data = ZoneSensorData(
        zone_id=request.zone_id,
        zone_name=request.zone_name,
        crop_id=request.crop_id,
        sensors=sensor_data,
    )
    zone_sensor_data.aggregate()

    area_m2 = request.area_m2 or 100.0
    area_ha = area_m2 / 10000.0

    irrigation_decision = app_state.irrigation_engine.make_decision(
        zone_sensor_data, crop, area_m2
    )
    fertilizer_decision = app_state.fertilizer_engine.make_decision(
        zone_sensor_data, crop, area_ha
    )

    return DecisionResult(
        zone_id=request.zone_id,
        zone_name=request.zone_name,
        irrigation=irrigation_decision,
        fertilizer=fertilizer_decision,
    )


@app.get("/decisions/latest")
async def get_latest_decisions():
    if not app_state.scheduler:
        raise HTTPException(status_code=500, detail="Decision scheduler not running")
    return app_state.scheduler.get_all_last_decisions()


@app.get("/decisions/{zone_id}/latest", response_model=DecisionResult)
async def get_zone_latest_decision(zone_id: str):
    if not app_state.scheduler:
        raise HTTPException(status_code=500, detail="Decision scheduler not running")
    decision = app_state.scheduler.get_last_decision(zone_id)
    if not decision:
        raise HTTPException(status_code=404, detail=f"No decision found for zone {zone_id}")
    return decision


@app.post("/decisions/trigger")
async def trigger_decision_check():
    if not app_state.scheduler:
        raise HTTPException(status_code=500, detail="Decision scheduler not running")
    app_state.scheduler.trigger_immediate_check()
    return {"status": "triggered", "message": "Decision check initiated"}


@app.post("/callback/irrigation")
async def callback_irrigation_decision(decision: IrrigationDecision):
    if not app_state.spring_boot_client:
        raise HTTPException(status_code=500, detail="Spring Boot client not initialized")
    success, message = app_state.spring_boot_client.callback_irrigation_decision(decision)
    return {"success": success, "message": message}


@app.post("/callback/fertilizer")
async def callback_fertilizer_decision(decision: FertilizerDecision):
    if not app_state.spring_boot_client:
        raise HTTPException(status_code=500, detail="Spring Boot client not initialized")
    success, message = app_state.spring_boot_client.callback_fertilizer_decision(decision)
    return {"success": success, "message": message}


@app.post("/sync/crops")
async def sync_crops_from_spring_boot():
    if not app_state.spring_boot_client or not app_state.crop_manager:
        raise HTTPException(status_code=500, detail="Services not initialized")
    synced = app_state.spring_boot_client.sync_crops_from_spring_boot(app_state.crop_manager)
    return {"synced_count": synced}


@app.get("/scheduler/status")
async def get_scheduler_status():
    return {
        "running": app_state.scheduler.running if app_state.scheduler else False,
        "check_interval_seconds": app_state.config.decision.check_interval_seconds if app_state.config else 60,
        "auto_callback_enabled": app_state.config.decision.enable_auto_callback if app_state.config else False,
        "last_decisions_count": len(app_state.scheduler.get_all_last_decisions()) if app_state.scheduler else 0,
    }


def main():
    import uvicorn

    parser = argparse.ArgumentParser(description='Growth Model Service for Fertigate Control System')
    parser.add_argument('--config', default='config.yaml', help='Path to configuration file')
    parser.add_argument('--host', default=None, help='Host to bind to')
    parser.add_argument('--port', type=int, default=None, help='Port to bind to')
    parser.add_argument('--no-scheduler', action='store_true', help='Disable automatic decision scheduler')
    args = parser.parse_args()

    config_loader = ConfigLoader(args.config)
    config = config_loader.load()

    host = args.host or config.host
    port = args.port or config.port

    logger.info(f"Starting Growth Model Service on {host}:{port}")
    logger.info(f"Spring Boot API: {config.spring_boot.base_url}")
    logger.info(f"Auto callback: {'enabled' if config.decision.enable_auto_callback else 'disabled'}")
    if not args.no_scheduler:
        logger.info(f"Decision check interval: {config.decision.check_interval_seconds}s")

    uvicorn.run(
        "main:app",
        host=host,
        port=port,
        reload=False,
        log_level=config.log_level.lower(),
    )


if __name__ == '__main__':
    main()
