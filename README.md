# NucleusFlow

Aplicación Android para control adaptativo de núcleos/hilos, frecuencia, afinidad, ZRAM/SWAP, VM, compactación y priorización a primer plano con Shizuku/Root.

## Características
- Descubrimiento 100% adaptativo (solo rutas escribibles)
- CPU affinity / thread pinning vía sched_setaffinity (NDK)
- Control por cluster (min/max/gov)
- Gestión por app + foreground
- cgroups v1/v2 con fallback a affinity
- ZRAM (disksize/comp_algorithm/max_comp_streams), SWAP, vm.*
- Compactación one-shot (/proc/sys/vm/compact_memory)
- Empuje a ZRAM/SWAP + priorización a primer plano (oom_score_adj + cgroups)
- Snapshot/Restauración
- Integración con Android: mapa dinámico, comunicación bidireccional, colaboración activa
- Explicaciones Pro por ajuste con rutas/métodos
- Shizuku UserService + fallback su

## Compilar
Abrir en Android Studio (Kotlin + NDK + CMake). Requiere Android SDK/NDK.

## Uso
Con Shizuku conectado o con `su` disponible. La app solo mostrará lo que el kernel permita.
