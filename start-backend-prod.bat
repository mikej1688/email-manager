@echo off
echo ============================================
echo Starting Email Manager Backend (PRODUCTION)
echo MySQL profile active
echo ============================================
echo.

REM Required — set these before running or export them in your environment
if "%DB_PASSWORD%"=="" (
    echo ERROR: DB_PASSWORD environment variable is not set.
    echo Set it with:  set DB_PASSWORD=your_mysql_password
    pause
    exit /b 1
)
if "%ENCRYPTION_KEY%"=="" (
    echo ERROR: ENCRYPTION_KEY environment variable is not set.
    echo Set it with:  set ENCRYPTION_KEY=your_base64_32byte_key
    pause
    exit /b 1
)

echo DB host    : %DB_HOST%
echo DB name    : %DB_NAME%
echo DB user    : %DB_USERNAME%
echo.
echo Backend API : http://localhost:8080
echo.

set SPRING_PROFILES_ACTIVE=prod
call mvn spring-boot:run
