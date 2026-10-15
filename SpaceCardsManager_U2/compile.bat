@echo off
echo ========================================================
echo   Compilando Space Cards Manager [Java + SQLite]
echo ========================================================

if not exist bin mkdir bin

dir /s /b src\*.java > sources.txt
javac -cp "lib/*" -d bin -encoding UTF-8 @sources.txt
del sources.txt

if %ERRORLEVEL% equ 0 (
    echo [EXITO] Compilacion completada correctamente.
) else (
    echo [ERROR] Ocurrio un error al compilar.
)
pause
