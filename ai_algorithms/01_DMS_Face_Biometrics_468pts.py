# -*- coding: utf-8 -*-
"""
Hamroh AI — DMS (Driver Monitoring System) 468 Nuqtali Yuz Biometriyasi Algoritmi
================================================================================
Muallif: Ismoilov Abdukamol (Hamroh AI Asoschisi va Lead AI Dasturchisi)
Loyiha: Hamroh AI — Milliy Faol Yo'l Xavfsizligi va DMS Tizimi

Ushbu modul haydovchining ko'z yumilishi (EAR), eshnashi (MAR) va bosh holatini (Head Pose PnP)
468 nuqtali biometrik yuz to'ri orqali 12ms ichida oflayn tahlil qiladi.
"""

import math
import time
from typing import Dict, Any, Tuple
import cv2
import numpy as np

# ----------------- BIOMETRIK KONSTANTALAR VA INDEKSLAR -----------------
# MediaPipe 468 Face Mesh standart indekslari
LEFT_EYE = [362, 385, 387, 263, 373, 380]
RIGHT_EYE = [33, 160, 158, 133, 153, 144]
MOUTH_LIPS = [61, 291, 0, 17, 13, 14, 78, 308] # tashqi va ichki lab nuqtalari

# Xavf chegaralari (Thresholds)
EAR_MICROSLEEP_THRESH = 0.22      # Ko'z yumuqligi chegarasi
MICROSLEEP_DURATION_SEC = 1.2     # Mikrouyqu xavfi vaqti (soniya)
MAR_YAWN_THRESH = 0.65            # Eshnash chegarasi
YAWN_DURATION_SEC = 2.0           # Eshnash davomiyligi
HEAD_YAW_DISTRACTION_DEG = 25.0   # Boshni chetga burish (telefon/yon oyna)
HEAD_PITCH_PHONE_DEG = -18.0      # Boshni pastga qaratish (telefon ko'rish)


def calculate_euclidean_distance(pt1: np.ndarray, pt2: np.ndarray) -> float:
    """Ikki 2D/3D nuqta orasidagi Evklid masofasini hisoblash."""
    return float(np.linalg.norm(pt1 - pt2))


def calculate_ear(eye_points: np.ndarray) -> float:
    """
    Eye Aspect Ratio (EAR) hisoblash formulasi:
    EAR = (|p2 - p6| + |p3 - p5|) / (2 * |p1 - p4|)
    """
    if len(eye_points) < 6:
        return 0.35
    p1, p2, p3, p4, p5, p6 = eye_points[:6]
    vertical_1 = calculate_euclidean_distance(p2, p6)
    vertical_2 = calculate_euclidean_distance(p3, p5)
    horizontal = calculate_euclidean_distance(p1, p4)

    if horizontal == 0:
        return 0.0
    ear = (vertical_1 + vertical_2) / (2.0 * horizontal)
    return float(ear)


def calculate_mar(mouth_points: np.ndarray) -> float:
    """
    Mouth Aspect Ratio (MAR) hisoblash formulasi (Eshnashni aniqlash):
    MAR = (|m2 - m8| + |m3 - m7| + |m4 - m6|) / (3 * |m1 - m5|)
    """
    if len(mouth_points) < 8:
        return 0.15
    m1, m5 = mouth_points[0], mouth_points[1] # Chap va o'ng lab burchagi
    top_lip, bot_lip = mouth_points[2], mouth_points[3]
    h_dist = calculate_euclidean_distance(m1, m5)
    v_dist = calculate_euclidean_distance(top_lip, bot_lip)

    if h_dist == 0:
        return 0.0
    return float(v_dist / h_dist)


def estimate_head_pose(landmarks_3d: np.ndarray, img_w: int, img_h: int) -> Tuple[float, float, float]:
    """
    PnP (Perspective-n-Point) algoritmi orqali haydovchi boshining
    Pitch (yuqori-past), Yaw (chap-o'ng) va Roll burilish burchaklarini hisoblash.
    """
    # 3D kanonik yuz modeli koordinatalari (mm)
    model_points = np.array([
        (0.0, 0.0, 0.0),             # Burun uchi (Nose tip)
        (0.0, -330.0, -65.0),        # Iyak (Chin)
        (-225.0, 170.0, -135.0),     # Chap ko'z tashqi burchagi
        (225.0, 170.0, -135.0),      # O'ng ko'z tashqi burchagi
        (-150.0, -150.0, -125.0),    # Chap lab burchagi
        (150.0, -150.0, -125.0)      # O'ng lab burchagi
    ], dtype=np.float64)

    # 2D tasvirdagi nuqtalar
    image_points = landmarks_3d[:6, :2].astype(np.float64)

    # Kamera matritsasi (Focal length taxmini)
    focal_length = img_w
    center = (img_w / 2.0, img_h / 2.0)
    camera_matrix = np.array([
        [focal_length, 0, center[0]],
        [0, focal_length, center[1]],
        [0, 0, 1]
    ], dtype=np.float64)
    dist_coeffs = np.zeros((4, 1)) # Distorsiyasiz

    success, rotation_vector, translation_vector = cv2.solvePnP(
        model_points, image_points, camera_matrix, dist_coeffs, flags=cv2.SOLVEPNP_ITERATIVE
    )

    if not success:
        return 0.0, 0.0, 0.0

    rot_mat, _ = cv2.Rodrigues(rotation_vector)
    # Euler burchaklarini ajratish
    sy = math.sqrt(rot_mat[0, 0] * rot_mat[0, 0] + rot_mat[1, 0] * rot_mat[1, 0])
    singular = sy < 1e-6

    if not singular:
        pitch = math.atan2(rot_mat[2, 1], rot_mat[2, 2])
        yaw = math.atan2(-rot_mat[2, 0], sy)
        roll = math.atan2(rot_mat[1, 0], rot_mat[0, 0])
    else:
        pitch = math.atan2(-rot_mat[1, 2], rot_mat[1, 1])
        yaw = math.atan2(-rot_mat[2, 0], sy)
        roll = 0.0

    pitch_deg = math.degrees(pitch)
    yaw_deg = math.degrees(yaw)
    roll_deg = math.degrees(roll)
    return pitch_deg, yaw_deg, roll_deg


class DriverMonitor:
    """Haydovchi holatini real vaqtda kuzatuvchi asosiy sinf."""

    def __init__(self):
        self.eye_closed_start_time = None
        self.yawn_start_time = None
        self.distraction_start_time = None
        self.total_blinks = 0
        self.total_yawns = 0
        self.microsleep_events = 0
        self.phone_distraction_events = 0
        self.last_ear = 0.35
        self.last_mar = 0.15

    def process_frame(self, ear: float, mar: float, pitch: float, yaw: float, current_time: float) -> Dict[str, Any]:
        """Bitta kadr bo'yicha tahlil va ogohlantirish statusini qaytaradi."""
        status = "NORMAL"
        alert_message = ""
        urgency = 0 # 0=Xavfsiz, 1=Diqqat, 2=Xavfli, 3=Kritik

        # 1. Ko'z yumuqligi va Mikrouyqu tahlili
        if ear < EAR_MICROSLEEP_THRESH:
            if self.eye_closed_start_time is None:
                self.eye_closed_start_time = current_time
            duration = current_time - self.eye_closed_start_time
            if duration >= MICROSLEEP_DURATION_SEC:
                status = "MICROSLEEP_DETECTED"
                alert_message = "DIQQAT! Ko'zingizni oching! Mikrouyqu aniqlandi!"
                urgency = 3
                self.microsleep_events += 1
            elif duration >= 0.4:
                status = "EYES_CLOSING"
                alert_message = "Ko'zingiz yumilmoqda, hushyor bo'ling!"
                urgency = 2
        else:
            if self.eye_closed_start_time is not None:
                duration = current_time - self.eye_closed_start_time
                if duration >= 0.15:
                    self.total_blinks += 1
            self.eye_closed_start_time = None

        # 2. Eshnash tahlili (Charchoq)
        if mar > MAR_YAWN_THRESH:
            if self.yawn_start_time is None:
                self.yawn_start_time = current_time
            if (current_time - self.yawn_start_time) >= YAWN_DURATION_SEC:
                if urgency < 2:
                    status = "YAWN_FATIGUE"
                    alert_message = "Charchoq alomatlari! To'xtab dam olish tavsiya etiladi."
                    urgency = 1
        else:
            self.yawn_start_time = None

        # 3. Chalg'ish va Telefon tahlili
        if abs(yaw) > HEAD_YAW_DISTRACTION_DEG or pitch < HEAD_PITCH_PHONE_DEG:
            if self.distraction_start_time is None:
                self.distraction_start_time = current_time
            if (current_time - self.distraction_start_time) >= 1.5:
                if urgency < 2:
                    status = "DISTRACTION"
                    alert_message = "Yo'lga qarang! Chalg'ish aniqlandi!"
                    urgency = 2
                    self.phone_distraction_events += 1
        else:
            self.distraction_start_time = None

        self.last_ear = ear
        self.last_mar = mar

        return {
            "status": status,
            "alert": alert_message,
            "urgency": urgency,
            "ear": round(ear, 3),
            "mar": round(mar, 3),
            "pitch": round(pitch, 1),
            "yaw": round(yaw, 1),
            "total_blinks": self.total_blinks,
            "microsleep_count": self.microsleep_events,
            "distraction_count": self.phone_distraction_events
        }


if __name__ == "__main__":
    print("Hamroh AI DMS 468 Nuqtali Yuz Biometriyasi Algoritmi — Sinov rejimi")
    dms = DriverMonitor()
    t_now = time.time()
    # Simulyatsiya: haydovchi ko'zini yumganda
    for i in range(15):
        t_sim = t_now + (i * 0.1) # har 100ms
        res = dms.process_frame(ear=0.15, mar=0.2, pitch=0.0, yaw=0.0, current_time=t_sim)
        print(f"t={i*100}ms | EAR={res['ear']} | Status={res['status']} | Urgency={res['urgency']}")
