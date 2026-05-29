@echo off
chcp 65001 >nul
cd /d "D:\File\软工\课设1\Monopoly-Deal-Cards-Game"
setlocal enabledelayedexpansion

:: Read dependency classpath
set /p DEPS=<cp.txt
set CP=target\classes;%DEPS%

echo =========================================
echo  Monopoly Deal Cards Game - Launcher
echo =========================================
echo.

:: Step 1: Start server
echo [1/3] Starting game server on port 8888...
start "MonopolyDeal-Server" cmd /c "java -cp "%CP%" com.monopolydeal.MonopolyDealApplication --server 8888"
if %ERRORLEVEL% NEQ 0 (
    echo [!] Server may have failed to start.
)
timeout /t 3 /nobreak >nul

:: Step 2: Start Client 1
echo [2/3] Starting Client 1...
start "MonopolyDeal-Client1" cmd /c "java -cp "%CP%" com.monopolydeal.MonopolyDealApplication --client localhost 8888"
timeout /t 1 /nobreak >nul

:: Step 3: Start Client 2
echo [3/3] Starting Client 2...
start "MonopolyDeal-Client2" cmd /c "java -cp "%CP%" com.monopolydeal.MonopolyDealApplication --client localhost 8888"

echo.
echo =========================================
echo  All components launched!
echo  - Server: console window
echo  - Client 1: game window
echo  - Client 2: game window
echo.
echo  Press any key to exit this launcher.
echo  (Game windows will remain open)
echo =========================================
pause >nul
