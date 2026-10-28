"""Server sozlamalari. Hammasi muhit o'zgaruvchilaridan o'qiladi; maxfiy qiymatlar kodda yo'q."""
from __future__ import annotations

import os
import secrets
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class Settings:
    db_path: str = field(default_factory=lambda: os.environ.get("HAMROH_DB", "hamroh.sqlite3"))
    jwt_secret: str = field(default_factory=lambda: os.environ.get("HAMROH_JWT_SECRET") or secrets.token_hex(32))
    jwt_ttl_days: int = 90
    # SMS: "log" — kod faqat server logiga yoziladi (ishlab chiqish); "eskiz" — Eskiz.uz orqali yuboriladi.
    sms_mode: str = field(default_factory=lambda: os.environ.get("HAMROH_SMS_MODE", "log"))
    eskiz_email: str | None = field(default_factory=lambda: os.environ.get("ESKIZ_EMAIL"))
    eskiz_password: str | None = field(default_factory=lambda: os.environ.get("ESKIZ_PASSWORD"))
    telegram_token: str | None = field(default_factory=lambda: os.environ.get("TELEGRAM_BOT_TOKEN"))
    telegram_bot_name: str = field(default_factory=lambda: os.environ.get("TELEGRAM_BOT_NAME", "HamrohAIBot"))
    telegram_webhook_secret: str = field(default_factory=lambda: os.environ.get("TELEGRAM_WEBHOOK_SECRET") or secrets.token_hex(16))
    public_base_url: str = field(default_factory=lambda: os.environ.get("HAMROH_PUBLIC_URL", "http://localhost:8000"))
    owner_pub_path: Path = field(default_factory=lambda: Path(os.environ.get("HAMROH_OWNER_PUB", "owner.pub")))
    owner_statement_path: Path = field(default_factory=lambda: Path(os.environ.get("HAMROH_OWNER_STATEMENT", "owner_statement.txt")))
    rate_limit_per_min: int = 60
    # FR-3.3
    confirm_reporters: int = 3
    confirm_radius_m: float = 25.0
    confirm_window_days: int = 30
    # FR-3.5
    gas_stale_s: int = 2 * 3600
    # FR-8.1
    live_trip_ttl_after_end_s: int = 3600
    live_trip_max_s: int = 12 * 3600
    # Gemini AI
    gemini_api_key: str = field(default_factory=lambda: os.environ.get("GEMINI_API_KEY", ""))


settings = Settings()
