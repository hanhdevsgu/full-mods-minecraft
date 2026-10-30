@echo off
chcp 65001 >nul
title Trung Tam Quan Ly Key - Sell PVP
cd /d "%~dp0"
cls
echo =====================================================================
echo    ⚔️  TRUNG TAM QUAN LY BAN QUYEN SELL PVP (LOCAL SERVER)
echo =====================================================================
echo    * Server noi bo: http://127.0.0.1:5555
echo    * Dong bo 2 chieu voi Google Sheets
echo    * Tu dong phan hoi xac thuc cho Mod Minecraft (0.005s)
echo =====================================================================
echo.
echo  Dang mo trinh duyet quan ly ban quyen...
start "" "http://127.0.0.1:5555"
echo  Dang khoi chay Server Local... (Nhan Ctrl + C de dung)
echo.
python admin_server.py
pause
