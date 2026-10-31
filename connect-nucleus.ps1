# NucleusFlow One-Click Auto-Connector & Bridge for Windows
Write-Host "=== NucleusFlow Multi-Transport Bridge ===" -ForegroundColor Cyan

$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (!(Test-Path $adbPath)) {
    $adbPath = "C:\platform-tools\adb.exe"
}
if (!(Test-Path $adbPath)) {
    Write-Host "Descargando platform-tools automáticamente..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" -OutFile "$env:TEMP\platform-tools.zip"
    Expand-Archive -Path "$env:TEMP\platform-tools.zip" -DestinationPath "C:\" -Force
    $adbPath = "C:\platform-tools\adb.exe"
}

Write-Host "1. Verificando conexiones (USB, Wi-Fi, Ethernet)..." -ForegroundColor Green
& $adbPath kill-server
Start-Sleep -Seconds 1
& $adbPath devices

$devices = & $adbPath devices | Select-String -Pattern "\bdevice\b"
if ($devices.Count -eq 0) {
    Write-Host "⚠️ No se detectó ningún dispositivo por USB. Asegúrate de conectar el cable USB o activar Depuración Inalámbrica." -ForegroundColor Red
    exit
}

Write-Host "2. Configurando puerto TCP/IP (5555) para soporte Wi-Fi / Ethernet..." -ForegroundColor Green
& $adbPath tcpip 5555
Start-Sleep -Seconds 2

# Get device IP address automatically
$ip = & $adbPath shell "ip -f inet addr show wlan0 2>/dev/null || ip -f inet addr show eth0 2>/dev/null" | Select-String -Pattern "inet\s+(\d+\.\d+\.\d+\.\d+)"
if ($ip -match "inet\s+(\d+\.\d+\.\d+\.\d+)") {
    $deviceIp = $Matches[1]
    Write-Host "IP detectada en dispositivo: $deviceIp" -ForegroundColor Cyan
    & $adbPath connect "$deviceIp:5555"
}

Write-Host "3. Iniciando Shizuku UserService en el dispositivo..." -ForegroundColor Green
& $adbPath shell "sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"

Write-Host "4. Compilando e instalando NucleusFlow APK..." -ForegroundColor Green
if (Test-Path ".\gradlew.bat") {
    .\gradlew.bat :app:assembleDebug
} else {
    C:\platform-tools\adb.exe devices # fallback
}

$apkPath = "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apkPath) {
    & $adbPath install -r $apkPath
    Write-Host "=== ¡NucleusFlow instalado y conectado con éxito! ===" -ForegroundColor Green
} else {
    Write-Host "⚠️ APK no encontrada en ruta estándar. Compilación requerida en Android Studio." -ForegroundColor Yellow
}
