@echo off
chcp 65001 > nul
echo ========================================================
echo   Iniciando Space Cards Manager - TCG Store [SQLite]
echo ========================================================

start javaw --enable-native-access=ALL-UNNAMED -cp "bin;lib/*" com.spacecards.Main
exit
