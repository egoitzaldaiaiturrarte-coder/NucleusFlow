# NucleusFlow Real Production Compute Boost & App Launch via Android Node
param(
    [string]$Token = ""
)

Write-Host "=== Conectando con Nodo de Procesamiento Android (Puerto 8899) ===" -ForegroundColor Cyan

if (-not $Token) {
    Write-Host "Error: se requiere el token de autenticacion del nodo." -ForegroundColor Red
    Write-Host "Obtenlo desde la app NucleusFlow (pantalla Compute -> Copiar token) o via keystore." -ForegroundColor Red
    exit 1
}

try {
    $socket = New-Object Net.Sockets.TcpClient("127.0.0.1", 8899)
    $stream = $socket.GetStream()
    $writer = New-Object IO.StreamWriter($stream)
    $reader = New-Object IO.StreamReader($stream)

    Write-Host "1. Solicitando BOOST de todos los nucleos del movil a maxima capacidad..." -ForegroundColor Yellow
    $writer.WriteLine("AUTH:$Token:BOOST_ALL_CORES")
    $writer.Flush()
    $resp1 = $reader.ReadLine()
    Write-Host "Respuesta: $resp1" -ForegroundColor Green

    Write-Host "2. Lanzando aplicacion / proceso en el dispositivo con privilegios Shizuku..." -ForegroundColor Yellow
    $writer.WriteLine("AUTH:$Token:SPAWN_APP:com.nucleusflow/.MainActivity")
    $writer.Flush()
    $resp2 = $reader.ReadLine()
    Write-Host "Respuesta: $resp2" -ForegroundColor Green

    $socket.Close()
    Write-Host "=== ¡Emision real completada con éxito! ===" -ForegroundColor Cyan
} catch {
    Write-Host "Error de conexion con el nodo Android. Asegurate de ejecutar: adb forward tcp:8899 tcp:8899" -ForegroundColor Red
}
