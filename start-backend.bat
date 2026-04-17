@echo off
echo ================================
echo Starting Email Manager Backend
echo ================================
echo.
echo Backend API will be available at: http://localhost:8080
echo H2 Console: http://localhost:8080/h2-console
echo.
set GMAIL_CLIENT_ID=12057600958-24if33ogm2thfsm7dlnr2jt5pcfiqcha.apps.googleusercontent.com
set GMAIL_CLIENT_SECRET=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
call mvn spring-boot:run
