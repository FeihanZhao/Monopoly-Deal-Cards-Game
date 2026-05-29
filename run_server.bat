@echo off
cd /d "D:\File\软工\课设1\Monopoly-Deal-Cards-Game"
setlocal enabledelayedexpansion
set CP=target\classes
for /f "delims=" %%i in (cp.txt) do set CP=!CP!;%%i
echo Starting server...
java -cp "!CP!" com.monopolydeal.MonopolyDealApplication --server 8888
