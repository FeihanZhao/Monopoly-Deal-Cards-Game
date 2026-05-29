@echo off
chcp 65001 >nul
cd /d "D:\File\软工\课设1\Monopoly-Deal-Cards-Game"
setlocal enabledelayedexpansion

:: Read dependency classpath from cp.txt
set /p DEPS=<cp.txt
set CP=target\classes;%DEPS%

:: Start server
echo Starting server on port 8888...
start "Monopoly-Server" cmd /c "java -cp "%CP%" com.monopolydeal.MonopolyDealApplication --server 8888"

timeout /t 3 /nobreak >nul

:: Start client 1
echo Starting Client 1...
start "Monopoly-Client1" cmd /c "java -cp "%CP%" com.monopolydeal.MonopolyDealApplication --client localhost 8888"

:: Start client 2
timeout /t 1 /nobreak >nul
echo Starting Client 2...
start "Monopoly-Client2" cmd /c "java -cp "%CP%" com.monopolydeal.MonopolyDealApplication --client localhost 8888"

echo.
echo All started! Look for windows:
echo - Server: console window (Monopoly-Server)
echo - Client 1: game window
echo - Client 2: game window
echo.
echo Close the server window to stop everything.
pause
