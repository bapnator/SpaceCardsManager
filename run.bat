@echo off
chcp 65001 > nul
echo ========================================================
echo   Iniciando Space Cards Manager - TCG Store [MariaDB]
echo ========================================================

set JAVAW=javaw
where javaw >nul 2>&1
if %ERRORLEVEL% neq 0 (
    set JAVAW=%USERPROFILE%\.vscode\extensions\redhat.java-1.57.0-win32-x64\jre\25.0.4.1-win32-x86_64\bin\javaw.exe
)

start "" "%JAVAW%" --enable-native-access=ALL-UNNAMED -cp "bin;lib/*" com.spacecards.Main
exit
