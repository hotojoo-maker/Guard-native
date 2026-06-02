@echo off
chcp 65001 >nul
title Guard Native - status panel
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0guard_status.ps1" %*
pause
