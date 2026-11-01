# COMUNICACIÓN MULTI-AGENTE Y ECOSYSTEM SYNC

**Fecha:** 31 de Octubre, 2026  
**Destino:** Todos los Agentes del Ecosistema Dual / OpenCloud  
**Asunto:** Auto-inicio de Shizuku y Nodo de Cómputo Android

## Actualización Crítica
- `NucleusApp` ahora inicia automáticamente `ShizukuManager` y `AndroidComputeBridge.startComputeNode()` al arrancar la aplicación, eliminando la necesidad de activar manualmente el servidor en el puerto 8899.
- El script `trigger-real-boost.ps1` conectará de inmediato con el nodo de procesamiento móvil tras hacer `adb forward tcp:8899 tcp:8899`.

## ESTADO 31/10/2026 08:2x — Ola de verificaciones reales y fixes de compilacion

- Repo controlado: `egoitzaldaiaiturrarte-coder/NucleusFlow` (main). Historial: b0e2a6e (gitignore *.p12) → 58e1be5 (hardening bridge V1 mitigation) → c430f1c (fix compile errors) → 4d2eca5 (restore launcher) → 9b5a264 (repo recovery).
- WIP hardening (ya commiteado en 58e1be5): AndroidComputeBridge (token Keystore, rate-limit 5/60s, bind 127.0.0.1|0.0.0.0, COMPUTE_CORES/CoreCount, BOOST_ALL_CORES, EXEC:, SPAWN_APP:), ComputeScreen (token UI + Switch bind + Iniciar/Detener), engine/README.md, test-compute-bridge.ps1, trigger-real-boost.ps1 actualizado.
- CI: verde en c430f1c (41181679884) → fallo en 58e1be5 (keystore pkg + Switch API) → fixes propios en: AndroidComputeBridge imports (crypto.EncryptedSharedPreferences + keystore.MasterKey), ComputeScreen Switch (checked/onCheckedChange), build.gradle (security-crypto 1.0.0 + keystore 1.0.0 → corregido a keystore 1.1.0). Run actual en curso: 41184713365 (fix keystore 1.1.0).
- APK: instalada en /storage/emulated/0/Download/NucleusFlow-ci.apk (descargada de run verde). versionCode 1 / versionName 1.0.0. Proceso com.nucleusflow vivo (PID 2289). Puerto 8899 NO escucha (nodo detenido). App en foco NucleusFlow.
- Vulnerabilidades pendientes de accion manual: V2 rotar tokens OAuth/API keys en sdcard backups; V3 rotar PAT ghp_ (scopes admin) y limpiar remote bionic-audio-lab; V5 http.sslVerify=false; V6 openclaw origins.
- Token compute bridge: generado en Keystore (AES256_GCM) por nodo; UI ComputeScreen copia a clipboard; cliente test: test-compute-bridge.ps1.

## PROXIMO PASO
- Si CI 41184713365 queda verde: descargar APK → install → start node (Iniciar Nodo) → test COMPUTE_CORES con token → BOOST_ALL_CORES → reportar veredicto final del modulo.
- Luego: cerrar ochenta tareas de seguridad y retomar cadena principal (chat de agentes / P3-P7).

## CIERRE FASE — 31/10/2026 (cierre solicitado por operador)

- Fase cerrada: recuperación repo (.git corrupto → desde remoto sano, commits propios), .gitignore, wrapper real, workflow CI (SDK+NDK+CMake), restore launcher (mipmap/colors), CI verde en e6bb1bf (run 41185551594) → APK nueva (18,33 MB) descargada (18:45).
- Fase en pausa/pendiente para continuación: instalación del APK nuevo (vía /sdcard/Download para pm install) y arranque nodo (botón Iniciar Nodo) + prueba COMPUTE_CORES/BOOST. Puerto 8899 NO escucha actualmente (nodo detenido).
- Hardening bridge V1 (commit 58e1be5 + fixes propios hasta e6bb1bf): token Keystore, rate-limit, bind localhost/LAN, COMPUTE_CORES, BOOST_ALL_CORES, EXEC:, SPAWN_APP — CI green, fuente consistente.
- Vulnerabilidades abiertas requiriendo acción manual del operador: V2 rotar tokens OAuth/API keys en backups sdcard; V3 rotar PAT ghp_ + limpiar remote bionic-audio-lab; V5 sslVerify=false; V6 openclaw origins "*".
- Dónde está lo no encontrado: diseño multi-enlace (BT+red+USB sin cuello de botella) NO hallado en este dispositivo — solo plausible en Windows (NucleusFlow-Shared).
- Hand-off inmediato para el siguiente agente: leer /root/NucleusFlow/inbox/sync_notification.md, arrancar CI→install→node→test del bridge, y retomar cadena principal (chat de agentes / P3-P7).

## RESUMEN EJECUTIVO DE ESTA OLA
1. Repo controlado recuperado y en verde de build (CI green).
2. APK firmada-debug generada y descargada.
3. Hardening bridge aplicado y source compila.
4. Puente 8899 documentado/implementado; arranque real pendiente.
5. Memoria compartida actualizada.

**Cierre solicitado:** OK — fase cerrada, memoria dejada al otro usuario/agentes para continuación.

## DECISIONES DE DISEÑO — 31/10/2026 (cierre fase modulo+chat)
- P3 (chat): TEXTO. A decisión del operador.
- P7 (login): RESUELTO por diseño: chat single-user (solo él) → no requiere login entre usuarios; acceso restringido por clave + única entrada + verificación huella facial+fingerprint.
- Modo de la app: bloqueo con clave, única pantalla/punto de entrada a agentes, ocultar apps relacionadas.

## CIERRE DEFINITIVO DE ESTA FASE (solicitud operador: "Cerramos y dejamos memoria al otro usuario en 10 minutos")
Pendiente inherente: instalación APK final (vía /sdcard/Download) + arranque nodo + prueba COMPUTE_CORES/BOOST.

## CIERRE FINAL — 31/10/2026 (solicitud operador: "Perfecto deja constancia y dejalo blindado")

- Constancia final: CI GREEN en main @ bab6b53 (run 41186381270, success) — HEAD limpio, sincronizado con origin/main.
- Blindaje aplicado: .gitignore (*.p12, *.jks, *.keystore, secure/), secret scan CI (ghp_/sk-/eyJ), sslVerify global=true, V1 bridge (auth/rate-limit/bind/commands) compilando y CI green, cleanup backups sdcard.
- Pendiente manual (operador): rotar PAT ghp_ (github settings tokens) + limpiar remote bionic-audio-lab (PAT en claro); prueba real adb forward + test-compute-bridge; definir origin real openclaw allowedOrigins.

## CIERRE COMPACTO — 01/11/2026
- CI GREEN final: main @ 4dc6fb6 (run 41314811851, success). HEAD limpio, sync origin/main.
- Recuperado de rojo por cambios paralelos: ChatManager.kt (imports flow) -> 8df6a1c; ChatScreen.kt (weight scope/ExperimentalMaterial3Api/RecognizerIntent) -> 4dc6fb6.
- TAREA 10 (FASE C) COMPLETADA: openclaw.json gateway.controlUi.allowedOrigins = ["http://localhost:18789","http://127.0.0.1:18789"] (gateway_port 18789); token intacto; permisos 600; backup openclaw.json.bak-compact-*.
- Pendiente manual: rotar PAT ghp_ + limpiar remote bionic-audio-lab; prueba real en dispositivo (adb forward + test-compute-bridge); purge .p12 (C9) solo si operador confirma.
- "16 hilos extra": NO existe esa configuracion en el repo (solo governor/frecuencia por core y afinidad taskset). Requiere aclaracion del operador.

## PARTICIONES COMPARTIDAS (4) — 01/11/2026
- Implementado `engine/SharedPartitionManager.kt`: 4 particiones siempre activas desde `NucleusApp.onCreate`.
- Escritura: las 4 escribibles por los 8 cores (afinidad del proceso a mask 0xFF; intento de online cpu0..7).
- Lectura: unificada de las 4 (`readUnified()`: merge por timestamp, sin duplicados).
- Bridge: `PARTITION_STATUS`, `PARTITION_WRITE:<p>:<texto>`, `PARTITION_WRITE_ALL:<texto>`, `PARTITION_READ` (documentado en engine/README.md).
- Ruta: `filesDir/nucleusflow_partitions/particion_0..3.dat`; registro `<ms>|core=<id>|<texto>`.
- CI GREEN en b46e42e (run 41316321128).

## ROTACION DE NUCLEOS 5+3 IMPLEMENTADA — 01/11/2026
- `engine/CoreRotationManager.kt`: Grupo A = 5 nucleos de minFreq bajo (<=400 MHz, tipico 300)
  con minimo elevado a 1200 MHz; Grupo B = 3 restantes. Turno de boost recorre A(5)+B(3)
  en bucle, fijando el nucleo en turno a la frecuencia mas alta soportada <= 2200 MHz.
- Dwell por defecto 12000 ms (rango 3000-60000). `STOP_ROTATION` restaura min/max originales.
- Bridge: `START_ROTATION[:ms]`, `STOP_ROTATION`, `ROTATION_STATUS`.
- UI: tarjeta de control en `CoresScreen` (estado, grupos, turno, MHz, ciclos, botones).
- Memoria: turnos repartidos en las 4 particiones; eventos de ciclo en las 4.
- Nuevo `MEMORY_POINTER[:url]`: el telefono guarda solo el enlace a Supabase (ver SHARED_MEMORY.md).
- Documentacion: `engine/README.md`, `AGENT_HANDOFF.md`, `SHARED_MEMORY.md`.
- Compilacion: pendiente de confirmar en este entorno (ver AGENT_HANDOFF / BUILD_STATUS).

## CORRECCION DE TIRONES — 01/11/2026
- Causas: SystemExecutor emitia un log por cada lectura (cientos por escaneo); scanCores se re-ejecutaba por cada nucleo en setters (O(n^2)); escaneos bloqueantes en hilo principal; emisiones de StateFlow con listas identicas.
- Fixes: `SystemExecutor.quiet {}` (silencia logs masivos); KernelManager/ProcessManager emiten solo si cambia la lista; `setGovernor/setFrequencyRange(rescan=false)` + un unico scan; BOOST sin doble scan; rotacion silenciada; escaneos y acciones de UI movidos a `Dispatchers.IO`.
- Fix de compilacion de agente paralelo: `CoreRotationManager` secuencia `List<CoreSnapshot>`.
- CI GREEN en f314fa6 (run 41319171724).
