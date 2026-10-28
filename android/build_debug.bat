@echo off
rem Hamroh AI: debug APK yig'ish. Natija va log shu papkada: build_log.txt
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [%date% %time%] yig'ish boshlandi > build_log.txt
call gradlew.bat :core:test :app:assembleDebug --console=plain >> build_log.txt 2>&1
echo EXIT=%ERRORLEVEL% >> build_log.txt
echo [%date% %time%] tugadi >> build_log.txt
