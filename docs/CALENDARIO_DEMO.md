# Calendario y tareas operativas DEMO

Quinta entrega del bloque 9. Consulta de eventos operativos y tareas con datos
ficticios y pertenencia explícita por empresa. No acredita planificación del ERP,
disponibilidad laboral, ausencias ni sincronización con otros calendarios.

## Rutas y permisos

Las rutas parten de `/api/v1/empresas/{empresa}` y requieren JWT vigente.

| GET | Permisos en la empresa |
|---|---|
| `/eventos` | EVENTOS, OBRAS y CLIENTES |
| `/eventos/{id}` | EVENTOS, OBRAS y CLIENTES |
| `/obras/{id}/eventos` | EVENTOS, OBRAS y CLIENTES |
| `/tareas` | TAREAS, OBRAS, CLIENTES y OPERARIOS |
| `/tareas/{id}` | TAREAS, OBRAS, CLIENTES y OPERARIOS |
| `/obras/{id}/tareas` | TAREAS, OBRAS, CLIENTES y OPERARIOS |
| `/operarios/{id}/tareas` | TAREAS, OBRAS, CLIENTES y OPERARIOS |

Los permisos se exigen también cuando las relaciones opcionales son nulas.
Sin autenticación: 401; sin permiso, pertenencia o recurso coherente: 404.
La autorización y la comprobación del padre preceden a la validación de filtros.
Un padre accesible sin resultados devuelve una página vacía. No hay escrituras.

Eventos: `id`, `titulo`, `fechaInicio`, `fechaFin`, `tipo`, `obraId`. Las tareas
incluyen `id`, `titulo`, `fechaLimite`, `fechaFinalizacion`, `estado`, `prioridad`,
`obraId`, `operarioId`. Solo los detalles añaden `descripcion`, que puede ser
nula. Los textos se conservan y deben renderizarse como texto, no como HTML.
No se exponen autores, auditoría, colores ni datos personales del operario.
La clasificación de títulos y descripciones reales debe confirmarse antes de
conectar el ERP. Los ejemplos solo contienen operaciones de montaje y revisión.

## Pertenencia y límites

`011_calendario.sql` incorpora `API_Evento_Empresa` y `API_Tarea_Empresa`, ocho
ejemplos y los permisos EVENTOS/TAREAS del rol DEMO. Los preparadores local y CI
aplican automáticamente la migración y registran su checksum; no se alteran
el esquema original ni las migraciones anteriores.

Si hay obra, tanto ella como su cliente deben pertenecer a la misma empresa.
Si hay operario en la tarea, debe pertenecer a esa empresa y ser una PERSONA.
Una relación incoherente oculta todo el recurso en detalles, listas y conteos.
Los padres nulos se conservan; la pertenencia nunca se deduce de ellos.

Solo se publican eventos de tipo OBRA, REUNION y AVISO. PERSONAL y VACACIONES
quedan excluidos también en acceso directo, búsqueda y conteos. El modelo de
evento no tiene relación con operarios. No se usa el texto del tipo para deducir
una ausencia. Las tareas conservan los estados y prioridades de catálogo, incluso
inactivos; las bajas de operarios y clientes no eliminan el histórico.

No hay recurrencias, recordatorios, resolución de solapamientos, cambios de
estado, reservas ni asignaciones futuras de personal. La referencia de una tarea
a un operario no demuestra su disponibilidad ni genera partes de trabajo.

## Paginación y filtros

Página inicial 0, máximo 100000; tamaño inicial 20, entre 1 y 100. Respuesta
común: `items`, `page`, `size`, `totalElements`, `totalPages`, `sort`.

| Listado | Filtros además de page, size y sort |
|---|---|
| Eventos de empresa | q, obra, tipo, desde, hasta |
| Eventos de obra | q, tipo, desde, hasta |
| Tareas de empresa | q, obra, operario, estado, prioridad, desde, hasta |
| Tareas de obra | q, operario, estado, prioridad, desde, hasta |
| Tareas de operario | q, obra, estado, prioridad, desde, hasta |

Eventos: orden por `id`, `titulo` o `fechaInicio`. Tareas: `id`, `titulo` o
`fechaLimite`. Dirección asc/desc; predeterminado `id,asc`; desempate por id en
la misma dirección. Al ordenar tareas por fecha, los nulos aparecen primero en
ascendente y al final en descendente.

`q` busca literalmente solo en el título, máximo 100 caracteres; `%`, `_` y `!`
no son comodines. Tipo: máximo 30 caracteres; estado/prioridad: 300. Comparación
según la colación MySQL DEMO; se recortan espacios y un texto vacío no filtra.
Los IDs de filtro son positivos; un ID ajeno o inexistente no produce coincidencias.
No se admite repetir el padre como filtro en las rutas anidadas. Parámetros
desconocidos, repetidos o inválidos devuelven 400.

## Fechas y horas

`desde` y `hasta` son días inclusivos `AAAA-MM-DD`, años 1000–9999 y desde ≤ hasta.

- Eventos: se seleccionan los que intersectan el intervalo de días. Un evento
  iniciado antes de `desde` se incluye si termina dentro o después. Un final
  exactamente a medianoche cuenta como intersección con ese día. Si no hay final,
  solo se usa el inicio para filtrar; la respuesta conserva el final nulo y no
  afirma que la duración sea cero. Un final anterior al inicio oculta el evento
  completo, sin corregirlo ni modificar el origen.
- Tareas: las fechas filtran `fechaLimite`, nunca `fechaFinalizacion`. Con un
  filtro de fechas, una tarea sin límite no coincide. Sin filtros se conserva.
  La fecha de finalización no modifica el estado ni permite inferir transiciones.

Los DATETIME de los eventos se publican como `AAAA-MM-DDTHH:mm:ss` local, sin
offset ni `Z`. El esquema no acredita su zona horaria; no deben convertirse
automáticamente a UTC. OpenAPI usa un patrón de hora local y no `date-time`, que
exigiría una zona. La precisión del esquema DEMO es de segundos.

## Ejemplos y verificación

Empresa 1: eventos 99601 (obra 1001, del 10 al 12 de septiembre), 99602 (aviso
sin obra ni final, 11 de septiembre) y 99604 (reunión de obra el 11).
Tareas 99701 y 99704, obra 1001/operario 301, límite 10 de septiembre;
99704 terminó el 9. La tarea 99702 no tiene padres, fechas ni descripción.
Empresa 2: evento 99603 y tarea 99703, obra 1003/operario 302 donde corresponda.

`CalendarioTests` verifica aislamiento, permisos, campos mínimos, nulos, filtros,
horas locales, intersecciones, intervalos invertidos, eventos excluidos y bajas.
`OpenApiTests` cubre las siete rutas. Los scripts `comprobar-acceso.ps1` y
`comprobar-despliegue.mjs` recorren estas consultas por HTTP y HTTPS.
El 12 de octubre de 2026 pasaron las 60 pruebas de calendario dentro de una
ejecución completa de 515 pruebas, sin fallos, errores ni omitidas. El JAR se
actualizó y el ensayo HTTPS verificó las siete rutas, filtros, horas locales,
nulos, aislamiento y revocación de sesión. La aplicación del ensayo quedó
detenida. Los resultados figuran en el la documentación histórica archivada localmente.
