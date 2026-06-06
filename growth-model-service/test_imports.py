import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

print("=== Testing Module Imports ===")

try:
    from models import CropVariety, GrowthStageConfig, WaterRequirementPoint
    print("OK - models module imported successfully")
except Exception as e:
    print(f"ERROR - models import failed: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

try:
    from services import ConfigLoader, CropManager, WaterCurveCalculator
    print("OK - services module imported successfully")
except Exception as e:
    print(f"ERROR - services import failed: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

try:
    from services import FertilizerCurveCalculator, IrrigationDecisionEngine
    print("OK - decision engines imported successfully")
except Exception as e:
    print(f"ERROR - decision engines import failed: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

try:
    from services import FertilizerDecisionEngine, SpringBootClient
    print("OK - spring boot client imported successfully")
except Exception as e:
    print(f"ERROR - spring boot client import failed: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

try:
    from services import DecisionScheduler
    print("OK - decision scheduler imported successfully")
except Exception as e:
    print(f"ERROR - decision scheduler import failed: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

print("\n=== Testing Basic Functionality ===")

try:
    from services.config_loader import ConfigLoader
    loader = ConfigLoader('config.yaml')
    config = loader.load()
    print(f"OK - Config loaded: {config.name}, port={config.port}")
except Exception as e:
    print(f"ERROR - Config load failed: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

try:
    from services.crop_manager import CropManager
    cm = CropManager(config.crops_config)
    crops = cm.get_all_crops()
    print(f"OK - Crop manager loaded {len(crops)} crops: {[c.name for c in crops]}")
except Exception as e:
    print(f"ERROR - Crop manager failed: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

try:
    from services.water_curve import WaterCurveCalculator
    wc = WaterCurveCalculator()
    tomato = cm.get_crop('tomato')
    water_req = wc.calculate_daily_requirement(tomato, 30)
    print(f"OK - Water curve: tomato day 30 = {water_req} mm/day")
except Exception as e:
    print(f"ERROR - Water curve failed: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

try:
    from services.fertilizer_curve import FertilizerCurveCalculator
    fc = FertilizerCurveCalculator()
    n, p, k = fc.calculate_daily_requirement(tomato, 30)
    print(f"OK - Fertilizer curve: tomato day 30 = N:{n} P:{p} K:{k}")
except Exception as e:
    print(f"ERROR - Fertilizer curve failed: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

try:
    from models.sensor_data import ZoneSensorData, SensorData
    from datetime import datetime

    sensor_data = [
        SensorData(device_code="S1", timestamp=datetime.now(), humidity=45.0, ec=1.2, ph=6.0, temperature=25.0),
    ]
    zone_data = ZoneSensorData(
        zone_id="zone-001",
        zone_name="Test Zone",
        crop_id="tomato",
        sensors=sensor_data,
    )
    zone_data.aggregate()
    print(f"OK - Sensor data aggregation: humidity={zone_data.aggregated_humidity}")
except Exception as e:
    print(f"ERROR - Sensor data failed: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

try:
    from services.irrigation_decision import IrrigationDecisionEngine
    from models.config import DecisionConfig

    irr_engine = IrrigationDecisionEngine(wc, DecisionConfig())
    decision = irr_engine.make_decision(zone_data, tomato, 100.0)
    print(f"OK - Irrigation decision: action={decision.action}, need={decision.need_irrigation}, duration={decision.duration_seconds}s")
    print(f"     Reason: {decision.reason}")
except Exception as e:
    print(f"ERROR - Irrigation decision failed: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

try:
    from services.fertilizer_decision import FertilizerDecisionEngine

    fert_engine = FertilizerDecisionEngine(fc, DecisionConfig())
    fert_decision = fert_engine.make_decision(zone_data, tomato, 0.01)
    print(f"OK - Fertilizer decision: action={fert_decision.action}, need={fert_decision.need_fertilizer}, amount={fert_decision.fertilizer_amount_kg}kg")
    print(f"     Reason: {fert_decision.reason}")
except Exception as e:
    print(f"ERROR - Fertilizer decision failed: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

try:
    water_curve = wc.generate_curve(tomato, 0, 5)
    print(f"OK - Generated water curve with {len(water_curve)} points")
except Exception as e:
    print(f"ERROR - Water curve generation failed: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

try:
    fert_curve = fc.generate_curve(tomato, 0, 5, 0.01)
    print(f"OK - Generated fertilizer curve with {len(fert_curve)} points")
except Exception as e:
    print(f"ERROR - Fertilizer curve generation failed: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

print("\n=== All tests passed! ===")
