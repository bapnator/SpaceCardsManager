@echo off
chcp 65001 > nul
echo ========================================================
echo   Compilando Space Cards Manager [Java + MariaDB]
echo ========================================================

set JAVAC=javac
where javac >nul 2>&1
if %ERRORLEVEL% neq 0 (
    set JAVAC=%USERPROFILE%\.vscode\extensions\redhat.java-1.57.0-win32-x64\jre\25.0.4.1-win32-x86_64\bin\javac.exe
)

if not exist bin mkdir bin

dir /s /b src\*.java > sources.txt
"%JAVAC%" -cp "lib/*" -d bin -encoding UTF-8 @sources.txt
del sources.txt

if %ERRORLEVEL% equ 0 (
    echo [EXITO] Compilacion completada correctamente.
) else (
    echo [ERROR] Ocurrio un error al compilar.
)
pause
