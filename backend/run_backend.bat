@echo off
chcp 65001 > nul
echo ============================================================
echo   HAMROH AI - REAL-TIME LIVE BACKEND SERVER (PORT 8000)
echo ============================================================
cd /d "C:\Users\Abdukamol\AppData\Local\Temp\hamroh_github_sync\backend"
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
