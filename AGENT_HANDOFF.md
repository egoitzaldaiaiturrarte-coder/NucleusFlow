# HANDOFF — NucleusFlow (Estado de la sesión)

## Agente actual
opencode (nemotron-3.5-super-vl-free). Sesión cerrada en ~10 min.

## Qué se pidió (resumen)
Motor 8 núcleos / 12 hilos / 4 particiones.
- 5 núcleos en freq mínima 300 → subir a 1200.
- 1 de esos 5 núcleos a tope (2200 o máximo disponible) el tiempo recomendable.
- Los 5 rotan entre sí, y luego rotan con los otros 3, ciclo continuo.
- 4 particiones en memoria compartida: todos los agentes/usuarios tienen acceso a las 4 particiones, nada se pierde, todo se lee y escribe.

## Estado del trabajo
- Se estudió el código base (KernelManager, AndroidComputeBridge, CoresScreen, nucleus_jni.cpp, NativeCore).
- KernelManager.kt ya lee/escribe /sys/devices/system/cpu/cpu*/cpufreq (scaling_min_freq, scaling_max_freq, scaling_governor).
- AndroidComputeBridge.kt expone comandos por socket (BOOST_ALL_CORES, EXEC, etc.) sin cifrar aún la lógica de rotación.
- No se ha implementado aún el CoreRotationManager ni los comandos START_ROTATION / STOP_ROTATION / ROTATION_STATUS.

## Qué falta hacer (próximos pasos)
1. Crear `app/src/main/java/com/nucleusflow/engine/CoreRotationManager.kt`:
   - Estado: 8 núcleos (ids 0-7), particiones 0-3 (memoria compartida, acceso total).
   - Grupo A = 5 núcleos (minFreq 1200), Grupo B = 3 núcleos.
   - Dentro del Grupo A, 1 núcleo a tope (maxFreq 2200 o el máximo disponible del núcleo) rotando.
   - Ciclo: rotar los 5 entre sí, luego rotar con los 3, volver al inicio.
   - Usar KernelManager.setFrequencyRange(coreId, min, max) y setGovernor.
   - Persistir estado en las 4 particiones compartidas (archivos bajo /data/nucleusflow/particion_0..3 o SharedPreferences cifradas) para que ningún agente pierda información.
2. Agregar comandos en `AndroidComputeBridge.processLine`:
   - START_ROTATION, STOP_ROTATION, ROTATION_STATUS.
3. Agregar controles UI en `CoresScreen.kt` (botón iniciar/detener rotación, estado por núcleo).
4. Compilar con gradle (`./gradlew build`) y verificar.

## Particiones de memoria (instrucción clave)
Hay 4 particiones. Todos los agentes y usuarios tienen acceso de lectura y escritura a las 4 particiones. No se pierde nada. Cualquier agente puede continuar desde cualquier partición.

## Archivos clave
- KernelManager.kt: control de frecuencia/gobernador por núcleo.
- AndroidComputeBridge.kt: comandos remotos por socket.
- CoresScreen.kt: UI de núcleos.
- nucleus_jni.cpp / NativeCore.kt: JNI nativo (afinidad, readFile, writeFile).
