import logging
import struct
import time
from typing import Dict, Any, Optional, Tuple

from pymodbus.client import ModbusSerialClient, ModbusTcpClient
from pymodbus.exceptions import ModbusException

logger = logging.getLogger(__name__)


class ModbusClient:
    def __init__(self, config: Dict[str, Any]):
        self.config = config
        self.client = None
        self._connect()

    def _connect(self) -> None:
        try:
            if self.config.get('connection_type', 'serial') == 'serial':
                self.client = ModbusSerialClient(
                    port=self.config['serial_port'],
                    baudrate=self.config['baudrate'],
                    bytesize=self.config['bytesize'],
                    parity=self.config['parity'],
                    stopbits=self.config['stopbits'],
                    timeout=self.config['timeout']
                )
            else:
                self.client = ModbusTcpClient(
                    host=self.config['host'],
                    port=self.config['port'],
                    timeout=self.config['timeout']
                )
            
            if self.client.connect():
                logger.info("Modbus client connected successfully")
            else:
                logger.error("Failed to connect to Modbus device")
        except Exception as e:
            logger.error(f"Error connecting to Modbus: {e}")
            self.client = None

    def is_connected(self) -> bool:
        return self.client is not None and self.client.connected

    def _reconnect(self) -> bool:
        logger.info("Attempting to reconnect to Modbus device...")
        if self.client:
            self.client.close()
        time.sleep(1)
        self._connect()
        return self.is_connected()

    def _with_retry(self, func, *args, max_retries: int = 3, **kwargs):
        for attempt in range(max_retries):
            try:
                if not self.is_connected():
                    if not self._reconnect():
                        time.sleep(1)
                        continue
                result = func(*args, **kwargs)
                return result
            except ModbusException as e:
                logger.warning(f"Modbus operation failed (attempt {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    time.sleep(0.5)
                    self._reconnect()
            except Exception as e:
                logger.error(f"Unexpected error in Modbus operation: {e}")
                if attempt < max_retries - 1:
                    time.sleep(0.5)
        logger.error(f"Modbus operation failed after {max_retries} attempts")
        return None

    def read_holding_registers(self, address: int, count: int, unit_id: int) -> Optional[list]:
        def _read():
            return self.client.read_holding_registers(address=address, count=count, unit=unit_id)
        
        response = self._with_retry(_read)
        if response and not response.isError():
            return response.registers
        return None

    def read_input_registers(self, address: int, count: int, unit_id: int) -> Optional[list]:
        def _read():
            return self.client.read_input_registers(address=address, count=count, unit=unit_id)
        
        response = self._with_retry(_read)
        if response and not response.isError():
            return response.registers
        return None

    def write_register(self, address: int, value: int, unit_id: int) -> bool:
        def _write():
            return self.client.write_register(address=address, value=value, unit=unit_id)
        
        response = self._with_retry(_write)
        return response is not None and not response.isError()

    def write_registers(self, address: int, values: list, unit_id: int) -> bool:
        def _write():
            return self.client.write_registers(address=address, values=values, unit=unit_id)
        
        response = self._with_retry(_write)
        return response is not None and not response.isError()

    def read_coil(self, address: int, unit_id: int) -> Optional[bool]:
        def _read():
            return self.client.read_coils(address=address, count=1, unit=unit_id)
        
        response = self._with_retry(_read)
        if response and not response.isError():
            return response.bits[0]
        return None

    def write_coil(self, address: int, value: bool, unit_id: int) -> bool:
        def _write():
            return self.client.write_coil(address=address, value=value, unit=unit_id)
        
        response = self._with_retry(_write)
        return response is not None and not response.isError()

    def decode_float(self, registers: list, scale: float = 1.0) -> float:
        if len(registers) >= 2:
            combined = (registers[0] << 16) | registers[1]
            value = struct.unpack('!f', struct.pack('!I', combined))[0]
            return round(value * scale, 4)
        return 0.0

    def decode_uint32(self, registers: list, scale: float = 1.0) -> float:
        if len(registers) >= 2:
            value = (registers[0] << 16) | registers[1]
            return round(value * scale, 4)
        return 0.0

    def decode_int32(self, registers: list, scale: float = 1.0) -> float:
        if len(registers) >= 2:
            value = (registers[0] << 16) | registers[1]
            if value & 0x80000000:
                value -= 0x100000000
            return round(value * scale, 4)
        return 0.0

    def decode_uint16(self, registers: list, scale: float = 1.0) -> float:
        if registers:
            return round(registers[0] * scale, 4)
        return 0.0

    def decode_value(self, registers: list, data_type: str = 'uint32', scale: float = 1.0) -> float:
        decoders = {
            'float': self.decode_float,
            'uint32': self.decode_uint32,
            'int32': self.decode_int32,
            'uint16': self.decode_uint16,
        }
        decoder = decoders.get(data_type, self.decode_uint32)
        return decoder(registers, scale)

    def close(self) -> None:
        if self.client:
            self.client.close()
            logger.info("Modbus client closed")
