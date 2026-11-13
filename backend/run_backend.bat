@echo off
chcp 65001 > nul
title SafeDrive AI - Live Backend Server (Port 8000)
echo ============================================================
echo   SAFEDRIVE AI (HAMROH AI) - LIVE BACKEND SERVER
echo   Local URL:    http://localhost:8000
echo   LAN URL:      http://10.18.212.172:8000
echo   API Docs:     http://localhost:8000/docs
echo   Radars API:   http://localhost:8000/api/v1/radars
echo ============================================================
cd /d "%~dp0"
python server.py
pause
