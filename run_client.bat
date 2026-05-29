@echo off
chcp 65001 >nul
cd /d "D:\File\软工\课设1\Monopoly-Deal-Cards-Game"
set /p DEPS=<cp.txt
set CP=target\classes;%DEPS%
start "Monopoly-Client" cmd /c "java -cp "%CP%" com.monopolydeal.MonopolyDealApplication --client localhost 8889"
