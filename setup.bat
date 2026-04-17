@echo off
echo ================================
echo Email Manager - Quick Start
echo ================================
echo.

echo Step 1: Building Backend...
cd /d "%~dp0"
call mvn clean install -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo Error building backend!
    pause
    exit /b 1
)
echo Backend build successful!
echo.

echo Step 2: Installing Frontend Dependencies...
cd frontend
call npm install
if %ERRORLEVEL% NEQ 0 (
    echo Error installing frontend dependencies!
    pause
    exit /b 1
)
echo Frontend dependencies installed!
echo.

echo ================================
echo Setup Complete!
echo ================================
echo.
echo To start the application:
echo.
echo 1. Start Backend (in one terminal):
echo    mvn spring-boot:run
echo.
echo 2. Start Frontend (in another terminal):
echo    cd frontend
echo    npm start
echo.
echo Backend will run on: http://localhost:8080
echo Frontend will run on: http://localhost:3000
echo.
pause
