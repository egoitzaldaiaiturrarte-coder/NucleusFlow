@echo off
echo === NucleusFlow Build & Install Script ===
echo 1. Desinstalando version anterior...
C:\Users\hp\AppData\Local\Android\Sdk\platform-tools\adb uninstall com.nucleusflow

echo 2. Compilando nueva version con Gradle...
call gradlew.bat assembleDebug

echo 3. Instalando APK actualizado en el movil...
C:\Users\hp\AppData\Local\Android\Sdk\platform-tools\adb install -r app\build\outputs\apk\debug\app-debug.apk

echo 4. Iniciando Shizuku UserService...
C:\Users\hp\AppData\Local\Android\Sdk\platform-tools\adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh

echo === Proceso completado con exito! ===
pause
