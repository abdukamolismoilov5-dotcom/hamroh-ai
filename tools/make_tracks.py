#!/usr/bin/env python3
"""testdata/tracks/*.csv sintetik qayta o'ynatish treklarini yaratadi (TZ 8-bo'lim).

Har trekdagi "# EXPECT X" / "# EXPECT !X" qatorlari kutilgan (yoki kutilmagan) ogohlantirish turlari,
"# EXPECT_EVENT X" — kutilgan hodisa (SosTriggered, PhoneDropped, POTHOLE, HARD_BRAKE ...).
Haqiqiy yo'l treklari ilovaning "trek yozish" rejimida shu formatda yoziladi va shu papkaga qo'shiladi.
"""
import math
from pathlib import Path

R = 6_371_008.8
OUT = Path(__file__).resolve().parents[1] / "testdata" / "tracks"


def dest(lat, lon, brg, d):
    dl = d / R
    th = math.radians(brg)
    la1, lo1 = math.radians(lat), math.radians(lon)
    la2 = math.asin(math.sin(la1) * math.cos(dl) + math.cos(la1) * math.sin(dl) * math.cos(th))
    lo2 = lo1 + math.atan2(math.sin(th) * math.sin(dl) * math.cos(la1), math.cos(dl) - math.sin(la1) * math.sin(la2))
    return math.degrees(la2), (math.degrees(lo2) + 540) % 360 - 180


class Track:
    def __init__(self, name, lat=41.311, lon=69.279, brg=90.0, desc=""):
        self.name, self.lat, self.lon, self.brg, self.t = name, lat, lon, brg, 0
        self.lines = [f"# {name}: {desc}"]

    def expect(self, *types):
        self.lines += [f"# EXPECT {t}" for t in types]; return self

    def expect_event(self, *ev):
        self.lines += [f"# EXPECT_EVENT {e}" for e in ev]; return self

    def clock(self, day, minute):
        self.lines.append(f"CLOCK,{day},{minute}"); return self

    def poi(self, pid, typ, ahead_m, limit=None, brg=None):
        la, lo = dest(self.lat, self.lon, self.brg if brg is None else brg, ahead_m)
        self.lines.append(f"POI,{pid},{typ},{la:.7f},{lo:.7f}" + (f",{limit}" if limit else "")); return self

    def drive(self, seconds, kmh, imu_hz=0, vertical=None):
        v = kmh / 3.6
        for _ in range(seconds):
            self.lines.append(f"L,{self.t},{self.lat:.7f},{self.lon:.7f},{v:.3f},{self.brg}")
            if imu_hz:
                step = 1000 // imu_hz
                for k in range(0, 1000, step):
                    tv = vertical(self.t + k) if vertical else 0.0
                    self.lines.append(f"I,{self.t + k},1.0,{tv}")
            self.lat, self.lon = dest(self.lat, self.lon, self.brg, v)
            self.t += 1000
        return self

    def decel(self, from_kmh, to_kmh, seconds):
        for i in range(seconds + 1):
            kmh = from_kmh + (to_kmh - from_kmh) * i / seconds
            v = kmh / 3.6
            self.lines.append(f"L,{self.t},{self.lat:.7f},{self.lon:.7f},{v:.3f},{self.brg}")
            self.lat, self.lon = dest(self.lat, self.lon, self.brg, v)
            self.t += 1000
        return self

    def imu(self, g, vert=0.0, dt=20):
        self.lines.append(f"I,{self.t},{g},{vert}"); self.t += dt; return self

    def still(self, seconds, hz=10, g=1.0):
        for _ in range(seconds):
            self.lines.append(f"L,{self.t},{self.lat:.7f},{self.lon:.7f},0.0,-")
            for k in range(0, 1000, 1000 // hz):
                self.lines.append(f"I,{self.t + k},{g},0.0")
            self.t += 1000
        return self

    def tick(self):
        self.lines.append(f"T,{self.t}"); return self

    def save(self):
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / f"{self.name}.csv").write_text("\n".join(self.lines) + "\n")


def main():
    Track("01_school_morning", desc="dushanba 08:00, 52 km/soat, maktab yonidan o'tish") \
        .expect("SCHOOL_ZONE", "SPEEDING").clock(1, 480).poi("s1", "SCHOOL", 1400).drive(140, 52).save()
    Track("02_school_sunday", desc="yakshanba 08:00 — kuchaytirilgan rejim yo'q, 45 km/soat") \
        .expect("SCHOOL_ZONE", "!SPEEDING").clock(7, 480).poi("s1", "SCHOOL", 1400).drive(140, 45).save()
    Track("03_highway_camera", desc="trassa 100 km/soat, 70 cheklovli radar") \
        .expect("SPEED_CAMERA", "SPEEDING").poi("c1", "SPEED_CAMERA", 3000, 70).drive(120, 100).save()
    Track("04_camera_behind", desc="radar orqada qoldi — ogohlantirish yo'q") \
        .expect("!SPEED_CAMERA").poi("c1", "SPEED_CAMERA", 200, 70, brg=270).drive(100, 80).save()
    Track("05_obey_limit", desc="radar, cheklovga rioya qilinadi") \
        .expect("SPEED_CAMERA", "!SPEEDING").poi("c1", "SPEED_CAMERA", 2000, 70).drive(110, 68).save()
    Track("06_pothole_road", desc="40 km/soat, bitta chuqur") \
        .expect_event("POTHOLE").drive(70, 40, imu_hz=50,
                                         vertical=lambda t: {65000: -5.0, 65040: 6.0}.get(t, 0.0)).save()
    Track("07_hard_brake", desc="70 dan 20 ga 3 soniyada") \
        .expect_event("HARD_BRAKE").drive(70, 70).decel(70, 20, 3).drive(10, 20).save()
    Track("08_crash_sos", desc="50 km/soat, 6g zarba, harakatsizlik, javobsiz") \
        .expect_event("ProtectClip", "CountdownStarted", "SosTriggered").drive(70, 50).imu(6.0).still(45).tick().save()
    t = Track("09_phone_drop", desc="to'xtab turganda telefon tushib ketdi").expect_event("PhoneDropped", "!SosTriggered").still(5)
    for _ in range(15):
        t.imu(0.05, dt=20)
    t.imu(7.0).still(50).tick().save()
    Track("10_speed_bump_crosswalk", desc="sun'iy notekislik va piyodalar o'tish joyi") \
        .expect("SPEED_BUMP", "CROSSWALK").poi("b1", "SPEED_BUMP", 1200).poi("x1", "CROSSWALK", 1500).drive(130, 40).save()
    print("treklar:", OUT)


if __name__ == "__main__":
    main()
