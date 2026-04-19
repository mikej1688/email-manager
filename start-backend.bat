@echo off
echo ================================
echo Starting Email Manager Backend
echo ================================
echo.
echo Backend API will be available at: http://localhost:8080
echo H2 Console: http://localhost:8080/h2-console
echo.
call mvn spring-boot:run
