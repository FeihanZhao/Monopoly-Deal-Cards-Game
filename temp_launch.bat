@echo off
chcp 65001 >nul
cd /d "D:\File\软工\课设1\Monopoly-Deal-Cards-Game"
set /p DEPS=<cp.txt
start "Monopoly-Client1" cmd /c "java -cp "target/classes;%DEPS%" com.monopolydeal.MonopolyDealApplication --client localhost 8888"
