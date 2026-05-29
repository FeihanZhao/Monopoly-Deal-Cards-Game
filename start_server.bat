@echo off
chcp 65001 >nul
cd /d "D:\File\软工\课设1\Monopoly-Deal-Cards-Game"
setlocal enabledelayedexpansion

set /p DEPS=<cp.txt
set CP=target\classes;%DEPS%

echo Starting server on port 8888...
java -cp "%CP%" com.monopolydeal.MonopolyDealApplication --server 8888
pause
