@echo off
chcp 65001 >nul
cd /d "D:\File\软工\课设1\Monopoly-Deal-Cards-Game"

set /p DEPS=<cp.txt
set CP=target\classes;%DEPS%

echo Starting Client 1...
start "Monopoly-Client1" cmd /c "java -cp "%CP%" com.monopolydeal.MonopolyDealApplication --client localhost 8888"
timeout /t 2 /nobreak >nul

echo Starting Client 2...
start "Monopoly-Client2" cmd /c "java -cp "%CP%" com.monopolydeal.MonopolyDealApplication --client localhost 8888"

echo Done! Check your desktop for game windows.
