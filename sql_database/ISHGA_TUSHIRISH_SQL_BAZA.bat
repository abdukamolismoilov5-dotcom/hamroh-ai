@echo off
chcp 65001 >nul
title Hamroh AI - Jonli SQL Server va Baza Boshqaruv Paneli
echo ====================================================================
echo   HAMROH AI — JONLI SQL BACKEND SERVERI VA BAZA BOSHQARUV PANELI
echo   Mobil ilovadan ro'yxatdan o'tganlar darhol SQL bazaga qo'shiladi!
echo ====================================================================
start "" "http://localhost:8000"
python "%~dp004_live_sql_backend_server.py"
pause
