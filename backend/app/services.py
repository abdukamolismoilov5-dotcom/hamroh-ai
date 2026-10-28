"""Tashqi xizmatlar: SMS (Eskiz.uz) va Telegram Bot API. Testlarda soxta (fake) klasslar bilan almashtiriladi."""
from __future__ import annotations

import logging
from typing import Protocol

import httpx

from .config import Settings

log = logging.getLogger("hamroh")


class SmsSender(Protocol):
    def send(self, phone: str, text: str) -> bool: ...


class Messenger(Protocol):
    def send_message(self, chat_id: int, text: str) -> bool: ...
    def send_location(self, chat_id: int, lat: float, lon: float) -> bool: ...


class LogSms:
    """Ishlab chiqish rejimi: kod logga yoziladi, haqiqiy SMS yuborilmaydi."""

    def __init__(self):
        self.sent: list[tuple[str, str]] = []

    def send(self, phone: str, text: str) -> bool:
        self.sent.append((phone, text))
        log.warning("SMS (log rejimi) %s***: %s", phone[:6], text)
        return True


class EskizSms:
    """Eskiz.uz SMS shlyuzi (https://notify.eskiz.uz)."""

    BASE = "https://notify.eskiz.uz/api"

    def __init__(self, email: str, password: str):
        self.email, self.password = email, password
        self._token: str | None = None

    def _auth(self) -> str:
        r = httpx.post(f"{self.BASE}/auth/login", data={"email": self.email, "password": self.password}, timeout=10)
        r.raise_for_status()
        self._token = r.json()["data"]["token"]
        return self._token

    def send(self, phone: str, text: str) -> bool:
        token = self._token or self._auth()
        for _ in range(2):
            r = httpx.post(f"{self.BASE}/message/sms/send",
                           data={"mobile_phone": phone.lstrip("+"), "message": text, "from": "4546"},
                           headers={"Authorization": f"Bearer {token}"}, timeout=10)
            if r.status_code == 401:
                token = self._auth()
                continue
            return r.is_success
        return False


class TelegramMessenger:
    def __init__(self, token: str):
        self.base = f"https://api.telegram.org/bot{token}"

    def _call(self, method: str, payload: dict) -> bool:
        try:
            r = httpx.post(f"{self.base}/{method}", json=payload, timeout=10)
            return r.is_success and r.json().get("ok", False)
        except httpx.HTTPError as e:
            log.error("Telegram xatosi: %s", e)
            return False

    def send_message(self, chat_id: int, text: str) -> bool:
        return self._call("sendMessage", {"chat_id": chat_id, "text": text, "disable_web_page_preview": False})

    def send_location(self, chat_id: int, lat: float, lon: float) -> bool:
        return self._call("sendLocation", {"chat_id": chat_id, "latitude": lat, "longitude": lon})


class NullMessenger:
    """Telegram tokeni sozlanmagan: xabarlar yuborilmaydi (javobda delivered=0)."""

    def send_message(self, chat_id: int, text: str) -> bool:
        log.warning("Telegram sozlanmagan, xabar yuborilmadi: %s", text[:60])
        return False

    def send_location(self, chat_id: int, lat: float, lon: float) -> bool:
        return False


def make_sms(s: Settings) -> SmsSender:
    if s.sms_mode == "eskiz" and s.eskiz_email and s.eskiz_password:
        return EskizSms(s.eskiz_email, s.eskiz_password)
    return LogSms()


def make_messenger(s: Settings) -> Messenger:
    return TelegramMessenger(s.telegram_token) if s.telegram_token else NullMessenger()
