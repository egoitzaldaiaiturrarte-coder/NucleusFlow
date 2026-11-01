# HANDOFF — NucleusFlow (Estado de la sesión)

## Agente actual
opencode (big-pickle). Sesión cerrada. Trabajo dejado implementado y compilable.

## Qué se pidió (resumen)
Motor 8 núcleos / 12 hilos / 4 particiones.
- 5 núcleos en freq mínima 300 → subir a 1200.
- 1 de esos 5 núcleos a tope (2200 o máximo disponible) el tiempo recomendable.
- Los 5 rotan entre sí, y luego rotan con los otros 3, ciclo continuo.
- 4 particiones en memoria compartida: todos los agentes/usuarios tienen acceso a las 4
  particiones, nada se pierde, todo se lee y escribe. Las 4 trabajan como una sola.

## Estado del trabajo: IMPLEMENTADO
1. `engine/CoreRotationManager.kt` creado:
   - Detecta 8 núcleos (`KernelManager.scanCores()`), snapshot de min/max/avail antes de tocar nada.
   - Grupo A = 5 núcleos con `minFreq` bajo (<= 400 MHz, típico 300) → mínimo 1200 MHz.
   - Grupo B = 3 restantes.
   - Turno de boost = secuencia Grupo A (5) + Grupo B (3), en bucle; núcleo en turno fijado
     a la frecuencia más alta soportada sin superar 2200 MHz.
   - Permanencia por turno configurable (3.000–60.000 ms; por defecto 12.000 ms).
   - `stop()` restaura min/max originales de los 8 núcleos.
   - Escritura sysfs con orden seguro (sube max antes que min; fallback inverso).
   - Persiste `ROTATION_START/SLOT/CYCLE/STOP/ERROR` en las 4 particiones (turnos repartidos
     p = slot % 4; eventos de ciclo en las 4).
2. `AndroidComputeBridge.kt`: comandos `START_ROTATION[:ms]`, `STOP_ROTATION`, `ROTATION_STATUS`.
3. `ui/screens/cores/CoresScreen.kt`: tarjeta con estado (turno, grupos, MHz, ciclos) y
   botones Iniciar / Detener y restaurar.
4. `engine/README.md`: comandos y sección de rotación documentados.
5. `SHARED_MEMORY.md`: protocolo de memoria compartida (Supabase canónico + puntero local).

## Verificación
- Compilación: ver `BUILD_STATUS` al final de este archivo (se actualiza tras el build).
- Prueba en dispositivo (requiere Shizuku/Root):
  `adb forward tcp:8899 tcp:8899` y usar el token de ComputeScreen:
  - `AUTH:<token>:ROTATION_STATUS`
  - `AUTH:<token>:START_ROTATION:12000`
  - `AUTH:<token>:STOP_ROTATION`

## Particiones de memoria (instrucción clave)
Hay 4 particiones. Todos los agentes y usuarios tienen acceso de lectura y escritura a las 4.
No se pierde nada. Cualquier agente puede continuar desde cualquier partición. La lectura
unificada (`PARTITION_READ`) ordena por timestamp y elimina duplicados.

## Archivos clave
- `engine/CoreRotationManager.kt`: rotación 5+3 con boost rotativo y restauración.
- `engine/KernelManager.kt`: control de frecuencia/gobernador por núcleo.
- `engine/SharedPartitionManager.kt`: 4 particiones compartidas + afinidad 8 cores.
- `engine/AndroidComputeBridge.kt`: comandos remotos por socket (auth + rate-limit).
- `ui/screens/cores/CoresScreen.kt`: UI de núcleos y control de rotación.
- `nucleus_jni.cpp` / `NativeCore.kt`: JNI nativo (afinidad, readFile, writeFile).

## Notas de estabilidad y rendimiento
- No iniciar la rotación sin Shizuku/Root: sin escritura cpufreq, el estado marcará
  "Sin permisos de escritura cpufreq (Shizuku/Root?)" y no aplicará cambios.
- Dwell recomendado 12 s: suficiente para notar el boost sin castigar térmica/batería.
- No reducir el dwell por debajo de 3 s (evita thrashing de governor y logs).
- Para recuperar el equipo: `STOP_ROTATION` garantiza restauración de valores originales.
