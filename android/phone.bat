@echo off
rem Hamroh AI: qayta yig'ish va USB orqali ulangan telefonda sinash. Natija: test_out\phone_log.txt va rasmlar
cd /d "%~dp0"
if not exist test_out mkdir test_out
set LOG=test_out\phone_log.txt
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set ADB=C:\Users\phd-2\AppData\Local\Android\Sdk\platform-tools\adb.exe
set PKG=uz.hamroh.app.debug
echo [%date% %time%] start > %LOG%
echo --- BUILD >> %LOG%
call gradlew.bat :core:test :app:assembleDebug --console=plain >> %LOG% 2>&1
echo BUILD_EXIT=%ERRORLEVEL% >> %LOG%
echo --- USB qurilmalar (pnputil) >> %LOG%
pnputil /enum-devices /connected /class AndroidUsbDeviceClass >> %LOG% 2>&1
pnputil /enum-devices /connected /class WPD >> %LOG% 2>&1
echo --- adb devices >> %LOG%
set N=0
:wait
"%ADB%" devices -l >> %LOG% 2>&1
"%ADB%" get-state > test_out\state.txt 2>&1
findstr /b "device" test_out\state.txt > nul && goto ready
set /a N+=1
if %N% GTR 60 goto nodevice
timeout /t 5 /nobreak > nul
goto wait
:nodevice
echo NO_DEVICE after 5 min >> %LOG%
goto end
:ready
echo DEVICE_READY >> %LOG%
"%ADB%" shell getprop ro.product.model >> %LOG% 2>&1
"%ADB%" shell getprop ro.build.version.release >> %LOG% 2>&1
"%ADB%" install -r -g app\build\outputs\apk\debug\app-debug.apk >> %LOG% 2>&1
"%ADB%" logcat -c
"%ADB%" shell monkey -p %PKG% -c android.intent.category.LAUNCHER 1 >> %LOG% 2>&1
timeout /t 8 /nobreak > nul
"%ADB%" exec-out screencap -p > test_out\p1.png
"%ADB%" shell wm size >> %LOG% 2>&1
"%ADB%" logcat -d -b crash > test_out\crash.txt 2>&1
:end
echo [%date% %time%] DONE >> %LOG%
