"""Environment-driven configuration. No business rules here."""

import os

CONTROL_BASE_URL: str = os.getenv("CONTROL_BASE_URL", "http://localhost:8080")
RUNTIME_TOKEN: str = os.getenv("RUNTIME_TOKEN", "platform-runtime-token")
REDIS_URL: str = os.getenv("REDIS_URL", "redis://localhost:6379/0")
