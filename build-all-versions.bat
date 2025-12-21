@echo off
setlocal enabledelayedexpansion

echo ====================================
echo Building plugin for all platforms
echo ====================================

cd /d "%~dp0"

set PLATFORMS=241 242 243 251 252 253
set VERSION=1.1.0

for %%p in (%PLATFORMS%) do (
    echo.
    echo [%%p] Building...
    call gradlew.bat :jetbrains-plugin:buildPlugin -PplatformMajor=%%p --no-daemon -q
    if errorlevel 1 (
        echo [%%p] FAILED
        exit /b 1
    )

    set "src=jetbrains-plugin\build\distributions\claude-code-plus-jetbrains-plugin-%VERSION%.zip"
    set "dst=jetbrains-plugin\build\distributions\claude-code-plus-jetbrains-plugin-%VERSION%-%%p.zip"
    if exist "!src!" (
        move /Y "!src!" "!dst!" >nul
        echo [%%p] OK - claude-code-plus-jetbrains-plugin-%VERSION%-%%p.zip
    )
)

echo.
echo ====================================
echo All versions built successfully!
echo Output: jetbrains-plugin\build\distributions\
echo ====================================
