# NucleusFlow Real Production Compute Boost & App Launch via Android Node
Write-Host "=== Conectando con Nodo de Procesamiento Android (Puerto 8899) ===" -ForegroundColor Cyan

try {
    $socket = New-Object Net.Sockets.TcpClient("127.0.0.1", 8899)
    $stream = $socket.GetStream()
    $writer = New-Object IO.StreamWriter($stream)
    $reader = New-Object IO.StreamReader($stream)

    Write-Host "1. Solicitando BOOST de todos los núcleos del móvil a máxima capacidad..." -ForegroundColor Yellow
    $writer.WriteLine("BOOST_ALL_CORES")
    $writer.Flush()
    $resp1 = $reader.ReadLine()
    Write-Host "Respuesta: $resp1" -ForegroundColor Green

    Write-Host "2. Lanzando aplicación / proceso en el dispositivo con privilegios Shizuku..." -ForegroundColor Yellow
    $writer.WriteLine("SPAWN_APP:com.nucleusflow/.MainActivity")
    $writer.Flush()
    $resp2 = $reader.ReadLine()
    Write-Host "Respuesta: $resp2" -ForegroundColor Green

    $socket.Close()
    Write-Host "=== ¡Emisión real completada con éxito! ===" -ForegroundColor Cyan
} catch {
    Write-Host "Error de conexión con el nodo Android. Asegurate de ejecutar: adb forward tcp:8899 tcp:8899" -ForegroundColor Red
}
