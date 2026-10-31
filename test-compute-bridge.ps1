# NucleusFlow Compute Bridge - Test de autenticacion y COMPUTE_CORES
param(
    [string]$Token = ""
)

$host = "127.0.0.1"
$port = 8899

function Invoke-ComputeRequest {
    param([string]$Line)
    $socket = New-Object Net.Sockets.TcpClient($host, $port)
    $stream = $socket.GetStream()
    $writer = New-Object IO.StreamWriter($stream)
    $reader = New-Object IO.StreamReader($stream)
    $writer.WriteLine($Line)
    $writer.Flush()
    $resp = $reader.ReadLine()
    $socket.Close()
    return $resp
}

Write-Host "=== TEST 1: Sin token -> debe ser RECHAZADO ===" -ForegroundColor Yellow
$resp1 = Invoke-ComputeRequest -Line "COMPUTE_CORES"
Write-Host "Respuesta: $resp1"
if ($resp1 -like "ERR:*") {
    Write-Host "PASS: Sin token rechazado correctamente." -ForegroundColor Green
} else {
    Write-Host "FAIL: Sin token deberia ser rechazado." -ForegroundColor Red
    exit 1
}

if (-not $Token) {
    Write-Host "=== TEST 2: Con token -> COMPUTE_CORES responde numero de nucleos ===" -ForegroundColor Yellow
    Write-Host "Error: se requiere el token para el TEST 2." -ForegroundColor Red
    Write-Host "Uso: .\test-compute-bridge.ps1 -Token <token>" -ForegroundColor Red
    exit 2
}

Write-Host "=== TEST 2: Con token -> COMPUTE_CORES responde numero de nucleos ===" -ForegroundColor Yellow
$resp2 = Invoke-ComputeRequest -Line "AUTH:$Token:COMPUTE_CORES"
Write-Host "Respuesta: $resp2"
if ($resp2 -like "SUCCESS:CoreCount:*") {
    Write-Host "PASS: Comando COMPUTE_CORES respondio con token." -ForegroundColor Green
} else {
    Write-Host "FAIL: Con token deberia responder SUCCESS:CoreCount." -ForegroundColor Red
    exit 1
}

Write-Host "=== TODAS LAS PRUEBAS COMPLETADAS ===" -ForegroundColor Cyan
