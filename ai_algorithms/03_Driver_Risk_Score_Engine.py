# -*- coding: utf-8 -*-
"""
Hamroh AI — Haydovchi Xavfsizlik Skori va B2B Flot Telemetriya Algoritmi
========================================================================
Muallif: Ismoilov Abdukamol (Hamroh AI Asoschisi va Lead AI Dasturchisi)
Loyiha: Hamroh AI — Milliy Faol Yo'l Xavfsizligi va DMS Tizimi

Ushbu modul haydovchining har daqiqalik harakatlari, qoidabuzarliklari,
charchoq darajasi va e'tiborini 0 dan 100 ballgacha baholovchi
dinamik matematik modelni ifodalaydi.
"""

import time
from typing import Dict, Any, List


class DriverSafetyScoreEngine:
    """
    Haydovchining xavfsizlik balini hisoblovchi matematik dvigatel.
    Boshlang'ich ball: 100 (Mukammal haydash).
    """

    # Qoidabuzarlik va xavf jarima ballari (Penalties)
    PENALTY_MICROSLEEP = 25.0       # Mikrouyqu aniqlanganda (-25 ball)
    PENALTY_PHONE_DISTRACTION = 15.0 # Telefonga qarash / bosh burish (-15 ball)
    PENALTY_CRITICAL_TTC = 20.0     # Kritik yaqinlashish / tormoz (-20 ball)
    PENALTY_YAWN_FATIGUE = 5.0      # Charchoq alomati / eshnash (-5 ball)
    PENALTY_HARD_BRAKE = 10.0       # Keskin tormozlanish (-10 ball)
    PENALTY_SPEEDING = 8.0          # Tezlik chegarasini buzish (-8 ball)

    # Xavfsiz haydash uchun rag'batlantirish (Recovery Bonus)
    BONUS_SAFE_MINUTE = 0.5         # Har bir xavfsiz daqiqa uchun +0.5 ball qaytariladi

    def __init__(self, initial_score: float = 100.0):
        self.score = float(initial_score)
        self.total_distance_km = 0.0
        self.events_log: List[Dict[str, Any]] = []
        self.last_update_time = time.time()
        self.consecutive_safe_seconds = 0.0

    def record_violation(self, violation_type: str, detail: str = ""):
        """Qoidabuzarlikni qayd etish va ballni pasaytirish."""
        deduction = 0.0
        if violation_type == "MICROSLEEP":
            deduction = self.PENALTY_MICROSLEEP
        elif violation_type == "DISTRACTION":
            deduction = self.PENALTY_PHONE_DISTRACTION
        elif violation_type == "CRITICAL_TTC":
            deduction = self.PENALTY_CRITICAL_TTC
        elif violation_type == "YAWN":
            deduction = self.PENALTY_YAWN_FATIGUE
        elif violation_type == "HARD_BRAKE":
            deduction = self.PENALTY_HARD_BRAKE
        elif violation_type == "SPEEDING":
            deduction = self.PENALTY_SPEEDING

        self.score = max(0.0, self.score - deduction)
        self.consecutive_safe_seconds = 0.0 # Xavfsiz yurish hisoblagichi nollanadi

        record = {
            "time": time.time(),
            "type": violation_type,
            "deduction": deduction,
            "new_score": round(self.score, 1),
            "detail": detail
        }
        self.events_log.append(record)

    def tick_second(self, delta_sec: float = 1.0, is_safe_now: bool = True):
        """Har soniyada haydovchining tiklanishini hisoblash."""
        if is_safe_now:
            self.consecutive_safe_seconds += delta_sec
            # Har 60 soniya xavfsiz yurish uchun tiklanish
            if self.consecutive_safe_seconds >= 60.0:
                self.score = min(100.0, self.score + self.BONUS_SAFE_MINUTE)
                self.consecutive_safe_seconds = 0.0

    def get_status_badge(self) -> Dict[str, Any]:
        """B2B Flot boshqaruv tizimi uchun haydovchi reyting statusi."""
        if self.score >= 90.0:
            rating = "A+"
            level = "MUKAMMAL"
            color = "#00E5FF" # Neon Cyan
        elif self.score >= 80.0:
            rating = "A"
            level = "YAXSHI"
            color = "#10B981" # Green
        elif self.score >= 65.0:
            rating = "B"
            level = "QONIQARLI"
            color = "#F59E0B" # Amber
        elif self.score >= 50.0:
            rating = "C"
            level = "XAVFLI"
            color = "#EF4444" # Orange-Red
        else:
            rating = "D"
            level = "O'TA KRITIK"
            color = "#DC2626" # Deep Red

        return {
            "score": round(self.score, 1),
            "rating": rating,
            "level": level,
            "badge_color": color,
            "total_violations": len(self.events_log)
        }


if __name__ == "__main__":
    print("Hamroh AI Haydovchi Skori Algoritmi — Sinov rejimi")
    engine = DriverSafetyScoreEngine()
    print("Boshlang'ich holat:", engine.get_status_badge())

    # 1-hodisa: Telefonga qarab chalg'ish
    engine.record_violation("DISTRACTION", "Haydovchi 2.1 soniya pastga qaradi")
    print("Telefon chalg'ishidan so'ng:", engine.get_status_badge())

    # 2-hodisa: Mikrouyqu
    engine.record_violation("MICROSLEEP", "Ko'z 1.4 soniya yumuq holatda qoldi")
    print("Mikrouyqudan so'ng:", engine.get_status_badge())
