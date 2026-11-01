# MEMORIA COMPARTIDA — NucleusFlow

## Regla de oro
- **Supabase es la fuente de verdad** (memoria permanente de cada sesión).
- **El teléfono solo guarda el enlace (puntero) a Supabase** + las 4 particiones como
  área de trabajo temporal.
- **Las 4 particiones trabajan como una sola**: todos los agentes/usuarios leen y escriben
  en las 4; la lectura unificada ordena por timestamp y elimina duplicados.

## Mapa de capas
| Capa | Qué guarda | Dónde | Vida |
|---|---|---|---|
| Puntero | URL de Supabase + último timestamp | 4 particiones (`SUPABASE_POINTER`) y `CloudSyncManager` | Permanente (solo enlace) |
| Trabajo | Estado de rotación, notas de agentes, eventos | `filesDir/nucleusflow_partitions/particion_0..3.dat` | Hasta compactación |
| Canónico | Memoria completa de la sesión | Supabase (`CloudSyncManager.syncToSupabase`) | Permanente |

## Formato de registro local
```
<ms>|core=<0-7>|<texto>
```
- Turnos de rotación: se reparten entre las 4 particiones (`p = slot % 4`).
- Eventos de ciclo (`START/SLOT/CYCLE/STOP/ERROR`): se escriben en las 4.
- Puntero: `<ms>|core=0|SUPABASE_POINTER|<url>|<ms>`.

## Protocolo de cierre de sesión (compactación blindada)
1. Verificar que la rotación esté detenida (`STOP_ROTATION`) para restaurar frecuencias.
2. `PARTITION_READ` → obtener la unión de las 4 particiones.
3. Subir ese contenido a Supabase con `CloudSyncManager.syncToSupabase(payloadJson)`
   (tabla `nucleus_profiles`, `Prefer: resolution=merge-duplicates`).
4. Confirmar respuesta 2xx. **No borrar nada local hasta confirmar el 2xx.**
5. Registrar/actualizar el puntero: comando bridge `MEMORY_POINTER:<url>`.
6. Solo entonces se puede recortar el área de trabajo (opcional y manual).

> Nunca vaciar las particiones antes de que Supabase confirme la subida: evita pérdida
> de información entre agentes.

## Comandos bridge útiles
- `PARTITION_STATUS` — estado de las 4 particiones + unificado.
- `PARTITION_READ` — lectura unificada ordenada y sin duplicados.
- `PARTITION_WRITE:<p>:<texto>` / `PARTITION_WRITE_ALL:<texto>` — escritura.
- `MEMORY_POINTER` — consulta el enlace canónico.
- `MEMORY_POINTER:<url>` — guarda/actualiza el enlace en las 4 particiones.

## Orden y estabilidad (checklist)
- Orden de aplicación de frecuencias: subir `scaling_max_freq` antes de `scaling_min_freq`;
  al bajar, `min` antes de `max`. (Implementado en `CoreRotationManager.writeFreq`.)
- Snapshot de min/max originales antes de rotar; `STOP_ROTATION` los restaura.
- Dwell recomendado 12 s (aceptado 3–60 s). Evita thrashing térmico y de governor.
- Requiere Shizuku/Root para escribir cpufreq; si no, el estado reporta el error.
- Mantener `ROTATION_STATUS` como latido para que el siguiente agente sepa el turno actual.
- Referenciar cada cambio en `AGENT_HANDOFF.md` + `inbox/sync_notification.md`.
