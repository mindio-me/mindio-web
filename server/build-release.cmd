@echo off
rem =====================================================
rem Release build with ProGuard obfuscation
rem Output: target\mindio-server.jar
rem =====================================================

echo === Building obfuscated release JAR ===
call mvnw.cmd clean package -Prelease -DskipTests

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Build failed. Check ProGuard output above.
    exit /b 1
)

echo.
echo === Build complete ===
echo Output: target\mindio-server.jar
