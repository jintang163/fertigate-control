import logging
import json
import os
import time
from datetime import datetime
from typing import Dict, Any, List, Optional
from threading import Lock

from sqlitedict import SqliteDict

logger = logging.getLogger(__name__)


class CacheManager:
    def __init__(self, config: Dict[str, Any]):
        self.config = config
        self.enabled = config.get('enabled', True)
        self.db_path = config.get('db_path', './data/cache.db')
        self.max_size_mb = config.get('max_size_mb', 100)
        self.flush_interval = config.get('flush_interval_seconds', 10)
        
        self._lock = Lock()
        self._cache_db = None
        self._last_flush_time = time.time()
        
        if self.enabled:
            self._init_cache()

    def _init_cache(self) -> None:
        try:
            os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
            self._cache_db = SqliteDict(
                self.db_path,
                tablename='sensor_data',
                autocommit=True,
                journal_mode='WAL'
            )
            logger.info(f"Cache initialized at {self.db_path}")
            self._check_cache_size()
        except Exception as e:
            logger.error(f"Failed to initialize cache: {e}")
            self.enabled = False

    def _check_cache_size(self) -> None:
        try:
            if os.path.exists(self.db_path):
                size_mb = os.path.getsize(self.db_path) / (1024 * 1024)
                logger.debug(f"Current cache size: {size_mb:.2f} MB")
                
                if size_mb > self.max_size_mb:
                    logger.warning(f"Cache size {size_mb:.2f} MB exceeds limit {self.max_size_mb} MB")
                    self._cleanup_old_entries()
        except Exception as e:
            logger.error(f"Error checking cache size: {e}")

    def _cleanup_old_entries(self) -> None:
        try:
            if not self._cache_db:
                return
            
            keys = list(self._cache_db.keys())
            if len(keys) > 1000:
                keys_to_delete = keys[:len(keys) // 2]
                for key in keys_to_delete:
                    del self._cache_db[key]
                logger.info(f"Cleaned up {len(keys_to_delete)} old cache entries")
        except Exception as e:
            logger.error(f"Error cleaning up cache: {e}")

    def store(self, data: Dict[str, Any]) -> bool:
        if not self.enabled or not self._cache_db:
            return False
        
        try:
            with self._lock:
                timestamp = datetime.utcnow().isoformat() + 'Z'
                key = f"{data.get('device_code', 'unknown')}_{int(time.time() * 1000)}"
                
                cache_entry = {
                    'data': data,
                    'timestamp': timestamp,
                    'synced': False
                }
                
                self._cache_db[key] = cache_entry
                logger.debug(f"Cached data for {data.get('device_code', 'unknown')}")
                
                self._check_cache_size()
                return True
        except Exception as e:
            logger.error(f"Error storing data in cache: {e}")
            return False

    def store_batch(self, data_list: List[Dict[str, Any]]) -> int:
        if not self.enabled or not self._cache_db:
            return 0
        
        count = 0
        for data in data_list:
            if self.store(data):
                count += 1
        
        return count

    def get_unsynced(self, limit: int = 100) -> List[Dict[str, Any]]:
        if not self.enabled or not self._cache_db:
            return []
        
        try:
            unsynced = []
            with self._lock:
                for key, value in self._cache_db.items():
                    if not value.get('synced', False):
                        unsynced.append({
                            'key': key,
                            'data': value['data'],
                            'timestamp': value['timestamp']
                        })
                        if len(unsynced) >= limit:
                            break
            
            logger.debug(f"Found {len(unsynced)} unsynced cache entries")
            return unsynced
        except Exception as e:
            logger.error(f"Error getting unsynced data: {e}")
            return []

    def mark_synced(self, keys: List[str]) -> int:
        if not self.enabled or not self._cache_db:
            return 0
        
        try:
            count = 0
            with self._lock:
                for key in keys:
                    if key in self._cache_db:
                        entry = self._cache_db[key]
                        entry['synced'] = True
                        entry['synced_at'] = datetime.utcnow().isoformat() + 'Z'
                        self._cache_db[key] = entry
                        count += 1
            
            logger.debug(f"Marked {count} entries as synced")
            return count
        except Exception as e:
            logger.error(f"Error marking entries as synced: {e}")
            return 0

    def cleanup_synced(self, older_than_hours: int = 24) -> int:
        if not self.enabled or not self._cache_db:
            return 0
        
        try:
            count = 0
            cutoff_time = time.time() - (older_than_hours * 3600)
            
            with self._lock:
                keys_to_delete = []
                for key, value in self._cache_db.items():
                    if value.get('synced', False):
                        synced_at = value.get('synced_at')
                        if synced_at:
                            try:
                                synced_timestamp = datetime.fromisoformat(
                                    synced_at.replace('Z', '+00:00')
                                ).timestamp()
                                if synced_timestamp < cutoff_time:
                                    keys_to_delete.append(key)
                            except:
                                pass
                
                for key in keys_to_delete:
                    del self._cache_db[key]
                    count += 1
            
            if count > 0:
                logger.info(f"Cleaned up {count} synced cache entries")
            
            return count
        except Exception as e:
            logger.error(f"Error cleaning up synced entries: {e}")
            return 0

    def get_stats(self) -> Dict[str, Any]:
        if not self.enabled or not self._cache_db:
            return {
                'enabled': False,
                'total_entries': 0,
                'unsynced_entries': 0
            }
        
        try:
            total = len(self._cache_db)
            unsynced = len([v for v in self._cache_db.values() if not v.get('synced', False)])
            
            size_mb = 0
            if os.path.exists(self.db_path):
                size_mb = os.path.getsize(self.db_path) / (1024 * 1024)
            
            return {
                'enabled': True,
                'total_entries': total,
                'unsynced_entries': unsynced,
                'size_mb': round(size_mb, 2)
            }
        except Exception as e:
            logger.error(f"Error getting cache stats: {e}")
            return {'enabled': False, 'error': str(e)}

    def flush(self) -> None:
        with self._lock:
            self._last_flush_time = time.time()
            if self._cache_db:
                self._cache_db.commit()

    def should_flush(self) -> bool:
        return (time.time() - self._last_flush_time) >= self.flush_interval

    def close(self) -> None:
        if self._cache_db:
            try:
                self._cache_db.close()
                logger.info("Cache closed")
            except Exception as e:
                logger.error(f"Error closing cache: {e}")
