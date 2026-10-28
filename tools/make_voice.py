#!/usr/bin/env python3
"""Ovozli ogohlantirishlarning oflayn audio fayllarini yaratadi (espeak-ng, uz/ru/en).

    pip install espeakng-loader ; ffmpeg kerak
    python make_voice.py

Natija: android/app/src/main/res/raw/<til>_<kalit>.ogg
Studiya yozuvlari tayyor bo'lganda, xuddi shu nomli fayllar bilan almashtiriladi — kod o'zgarmaydi.
"""
import ctypes
import os
import subprocess
import tempfile
import wave
from pathlib import Path

import espeakng_loader

OUT = Path(__file__).resolve().parents[1] / "android" / "app" / "src" / "main" / "res" / "raw"

PHRASES = {
    "pedestrian_ahead": ("Diqqat! Oldinda piyoda!", "Внимание! Впереди пешеход!", "Warning! Pedestrian ahead!"),
    "child_ahead": ("Diqqat! Oldinda bola bo'lishi mumkin!", "Внимание! Впереди может быть ребёнок!", "Warning! Possible child ahead!"),
    "forward_collision": ("Diqqat! Oldingi mashinaga juda yaqinsiz!", "Внимание! Опасное сближение!", "Warning! Too close to the car ahead!"),
    "fatigue_rest": ("Siz charchadingiz. Iltimos, dam oling!", "Вы устали. Пожалуйста, отдохните!", "You seem tired. Please take a rest!"),
    "speeding": ("Tezlikni kamaytiring!", "Снизьте скорость!", "Slow down!"),
    "school_zone": ("Diqqat, maktab hududi.", "Внимание, школьная зона.", "Caution, school zone."),
    "school_zone_enhanced": ("Diqqat! Maktab hududi, bolalar ko'p. Sekinlang!", "Внимание! Школьная зона, много детей. Сбавьте скорость!", "Caution! School zone, children around. Slow down!"),
    "kindergarten_zone": ("Diqqat, bog'cha hududi.", "Внимание, детский сад.", "Caution, kindergarten zone."),
    "speed_camera": ("Oldinda fotoradar.", "Впереди камера контроля скорости.", "Speed camera ahead."),
    "crosswalk": ("Piyodalar o'tish joyi.", "Пешеходный переход.", "Pedestrian crossing."),
    "speed_bump": ("Oldinda sun'iy notekislik.", "Впереди лежачий полицейский.", "Speed bump ahead."),
    "pothole": ("Oldinda chuqur.", "Впереди яма.", "Pothole ahead."),
    "lead_moved": ("Yo'l ochiq.", "Можно ехать.", "Traffic is moving."),
    "break_suggestion": ("Ikki soatdan beri haydayapsiz. Dam olish vaqti.", "Вы за рулём больше двух часов. Пора отдохнуть.", "You have been driving for two hours. Time for a break."),
    "phone_distraction": ("Haydash paytida telefonga qaramang.", "Не отвлекайтесь на телефон за рулём.", "Keep your eyes on the road, not the phone."),
    "thermal_camera_off": ("Telefon qizidi. Kamera vaqtincha o'chirildi.", "Телефон перегрелся. Камера временно отключена.", "Phone is overheating. Camera paused."),
    "sos_countdown": ("Kuchli zarba sezildi. Yaxshimisiz? Javob bo'lmasa, yaqinlaringizga xabar yuboriladi.",
                      "Обнаружен сильный удар. Вы в порядке? Если не ответите, мы сообщим близким.",
                      "Strong impact detected. Are you okay? If you do not respond, your family will be notified."),
    "sos_sent": ("Yaqinlaringizga xabar yuborildi.", "Близким отправлено сообщение.", "Your family has been notified."),
    "sos_cancelled": ("Xabar bekor qilindi.", "Сообщение отменено.", "Alert cancelled."),
    "drive_started": ("Hamroh yoqildi. Oq yo'l!", "Хамрох включён. Счастливого пути!", "Hamroh is on. Have a safe trip!"),
}
LANGS = ("uz", "ru", "en")

lib = ctypes.CDLL(espeakng_loader.get_library_path())
SYNTH_CB = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.POINTER(ctypes.c_short), ctypes.c_int, ctypes.c_void_p)
_buf: list[bytes] = []


@SYNTH_CB
def _cb(wav, n, _events):
    if wav and n > 0:
        _buf.append(ctypes.string_at(wav, n * 2))
    return 0


def main():
    data_parent = str(Path(espeakng_loader.get_data_path()).parent)
    rate = lib.espeak_Initialize(2, 500, data_parent.encode(), 0)  # AUDIO_OUTPUT_SYNCHRONOUS
    if rate <= 0:
        raise SystemExit("espeak-ng ishga tushmadi")
    lib.espeak_SetSynthCallback(_cb)
    lib.espeak_SetParameter(1, 145, 0)  # espeakRATE: so'z/daqiqa
    OUT.mkdir(parents=True, exist_ok=True)
    for li, lang in enumerate(LANGS):
        if lib.espeak_SetVoiceByName(lang.encode()) != 0:
            raise SystemExit(f"{lang} ovozi topilmadi")
        for key, texts in PHRASES.items():
            _buf.clear()
            t = texts[li].encode("utf-8")
            lib.espeak_Synth(t, len(t) + 1, 0, 0, 0, 1, None, None)  # espeakCHARS_UTF8
            lib.espeak_Synchronize()
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
                with wave.open(tmp.name, "wb") as w:
                    w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate)
                    w.writeframes(b"".join(_buf))
            out = OUT / f"{lang}_{key}.ogg"
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-af", "volume=1.6",
                            "-c:a", "libvorbis", "-q:a", "3", str(out)], check=True)
            os.unlink(tmp.name)
    print(f"{len(LANGS) * len(PHRASES)} ta fayl:", OUT)


if __name__ == "__main__":
    main()
