# NucleusFlow Android Compute Bridge (protocolo)

Servidor TCP en el puerto `8899` del nodo Android de procesamiento.

## Bind

- Por defecto: `127.0.0.1` (compatible con `adb forward tcp:8899 tcp:8899`).
- Toggle explicito en `ComputeScreen` (etiquetado "Solo LAN, requiere token") para escuchar en `0.0.0.0`.

## Formato de linea de peticion

Cada linea de peticion debe llevar token:

```
AUTH:<token>:<COMMAND>
```

- `<token>`: hex de 32 bytes (64 caracteres) generado al arranque del nodo y almacenado en Android Keystore (EncryptedSharedPreferences / MasterKey AES256_GCM).
- `<COMMAND>`: comando a ejecutar.

## Comandos

| Comando | Descripcion | Respuesta |
|---|---|---|
| `COMPUTE_CORES` | Devuelve el numero de nucleos disponibles | `SUCCESS:CoreCount=<n>` |
| `BOOST_ALL_CORES` | Fuerza governor `performance` y sube `scaling_min_freq`/`scaling_max_freq` de todos los `cpu*` + compacta memoria | `SUCCESS: ...` |
| `SPAWN_APP:<component>` | Lanza app/proceso con `am start -n <component>` via Shizuku | `SUCCESS: ...` |
| `EXEC:<cmd>` | Ejecuta comando del sistema via Shizuku/SystemExecutor | `SUCCESS: ...` |
| `PARTITION_STATUS` | Estado de las 4 particiones (lineas, bytes, total unificado) | `PARTITIONS=4 CORES=8 AFFINITY=0xff...` |
| `PARTITION_WRITE:<p>:<texto>` | Escribe en la particion `p` (0-3) | `SUCCESS:Written to partition <p>` |
| `PARTITION_WRITE_ALL:<texto>` | Escribe el mismo texto en las 4 particiones | `SUCCESS:Written to 4 partitions` |
| `PARTITION_READ` | Lectura unificada de las 4 particiones (orden por timestamp, sin duplicados) | `SUCCESS:\n<registros>` |
| `START_ROTATION[:<ms>]` | Inicia la rotacion de los 8 nucleos (5 del Grupo A a minimo 1200 MHz + 1 a tope <= 2200 MHz, luego los 3 del Grupo B, en bucle). `ms` = permanencia por turno (3000-60000, por defecto 12000) | `SUCCESS:Rotation started ...` |
| `STOP_ROTATION` | Detiene la rotacion y restaura min/max originales de todos los nucleos | `SUCCESS:Rotation stopped ...` |
| `ROTATION_STATUS` | Estado de la rotacion (grupos, nucleo en turno, ciclos, frecuencias) | `ROTATION=RUNNING\|STOPPED ...` |
| `MEMORY_POINTER` | Devuelve el enlace canonico a Supabase | `SUCCESS:SUPABASE_POINTER=<url>` o `ERR:NO_POINTER` |
| `MEMORY_POINTER:<url>` | Registra el enlace a Supabase en las 4 particiones | `SUCCESS:Pointer stored` |

## Autenticacion y rate-limit

- Token invalido o linea sin `AUTH:` -> `ERR:AUTH_REQUIRED`.
- Comparacion en tiempo constante (MessageDigest.isEqual).
- Maximo 5 fallos -> bloqueo temporal de 60 s (`ERR:TOO_MANY_FAILURES`).

## Errores comunes

- `UNKNOWN_COMMAND`: comando no reconocido.
- `ERR:AUTH_REQUIRED`: falta o falla el token.
- `ERR:TOO_MANY_FAILURES`: rate-limit activo (bloqueo 60 s).

## Prueba

```
adb forward tcp:8899 tcp:8899
.\trigger-real-boost.ps1 -Token <token>
```

Script de prueba: `test-compute-bridge.ps1`.

## Particiones compartidas (4)

- 4 particiones (`particion_0..3.dat`) siempre activas desde el arranque (`SharedPartitionManager.init`).
- Escritura: las 4 son escribibles y accesibles desde los 8 cores (`ALL_CORES_MASK = 0xFF`).
- Lectura: `PARTITION_READ` devuelve la union unificada de las 4, ordenada por timestamp y sin duplicados.
- Al iniciar se intenta poner online los 8 cores y fijar la afinidad del proceso a los 8 cores.
- Ruta base: `filesDir/nucleusflow_partitions` (app-privada). Formato de registro: `<ms>|core=<id>|<texto>`.

## Rotacion de nucleos (8 = 5 + 3)

- Grupo A: los 5 nucleos con `scaling_min_freq` bajo (<= 400 MHz, tipicamente 300 MHz).
  Su minimo se eleva a 1200 MHz o al valor soportado mas cercano por encima.
- Grupo B: los 3 nucleos restantes. Mantienen sus valores originales salvo en su turno.
- Turno de boost: recorre Grupo A (5) y luego Grupo B (3), en bucle. El nucleo en
  turno se fija a la frecuencia mas alta soportada sin superar 2200 MHz.
- Permanencia recomendada por turno: 12000 ms (rango aceptado 3000-60000).
- Al detener se restauran `scaling_min_freq`/`scaling_max_freq` originales.
- Cada turno y cada ciclo se registra en las 4 particiones compartidas
  (`ROTATION_SLOT`, `ROTATION_CYCLE`, `ROTATION_START/STOP`).
