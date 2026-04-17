@echo off
REM Quick OAuth Setup Script for Email Manager

echo =====================================
echo Email Manager - OAuth Setup Helper
echo =====================================
echo.

REM Check if .env file exists
if exist .env (
    echo [OK] .env file found
    echo.
) else (
    echo [!] No .env file found. Creating from template...
    copy .env.example .env
    echo.
    echo [ACTION REQUIRED] Please edit .env file with your Google OAuth credentials
    echo.
    echo Steps:
    echo 1. Open .env file in an editor
    echo 2. Get credentials from: https://console.cloud.google.com/
    echo 3. Fill in GMAIL_CLIENT_ID and GMAIL_CLIENT_SECRET
    echo 4. Save the file and run this script again
    echo.
    pause
    exit /b 1
)

REM Check if credentials are set in .env
findstr /C:"your-client-id-here" .env >nul 2>&1
if %errorlevel% equ 0 (
    echo [!] WARNING: .env file still contains placeholder values
    echo Please update .env with your actual Google OAuth credentials
    echo.
    pause
)

echo Loading environment variables from .env...
echo.

REM Load .env file (basic implementation)
for /f "tokens=1,* delims==" %%a in (.env) do (
    set "line=%%a"
    REM Skip comments and empty lines
    if not "!line:~0,1!"=="#" (
        if not "%%a"=="" (
            set "%%a=%%b"
            echo   Set: %%a
        )
    )
)

echo.
echo =====================================
echo Starting Email Manager Backend
echo =====================================
echo.
echo OAuth Configuration:
echo   Redirect URI: http://localhost:8080/api/oauth/callback
echo   Frontend URL: http://localhost:3000
echo.
echo After backend starts:
echo   1. Run start-frontend.bat in another terminal
echo   2. Open http://localhost:3000
echo   3. Go to Account Management
echo   4. Click "Connect Gmail (OAuth)"
echo.
echo Starting backend server...
echo.

call .\mvnw.cmd spring-boot:run
