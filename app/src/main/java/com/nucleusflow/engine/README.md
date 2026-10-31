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
| `COMPUTE_CORES` | Devuelve el numero de nucleos disponibles | `SUCCESS:CoreCount:<n>` |
| `BOOST_ALL_CORES` | Fuerza governor `performance` y sube `scaling_min_freq`/`scaling_max_freq` de todos los `cpu*` + compacta memoria | `SUCCESS: ...` |
| `SPAWN_APP:<component>` | Lanza app/proceso con `am start -n <component>` via Shizuku | `SUCCESS: ...` |
| `EXEC:<cmd>` | Ejecuta comando del sistema via Shizuku/SystemExecutor | `SUCCESS: ...` |

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
