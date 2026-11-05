@echo off
setlocal
echo ========================================================
echo   AutoAimSellSign 1.12.2 - Build Script
echo ========================================================

if exist "C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot"
) else (
    echo [WARNING] Khong tim thay JDK 8 tai duong dan mac dinh!
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [*] Dang build mod bang Gradle Forge...
call gradlew.bat build --offline

if %ERRORLEVEL% equ 0 (
    echo.
    echo [THANH CONG] Build hoan tat!
    echo File .jar dau ra: build\libs\AutoAimSellSign-1.12.2-1.0.jar
) else (
    echo.
    echo [THAT BAI] Co loi xay ra trong qua trinh build.
)

pause
endlocal
