# Incidencias operativas DEMO

Cuarta entrega del bloque 9: consulta de incidencias relacionadas con obras y,
opcionalmente, operarios. Los datos son ficticios; no acreditan compatibilidad
con el ERP ni representan ausencias, vacaciones o incidencias de salud.

## Endpoints y permisos

Todas las rutas parten de `/api/v1/empresas/{empresa}` y requieren JWT vigente
y los permisos **INCIDENCIAS, OBRAS, CLIENTES y OPERARIOS** en esa empresa.
OPERARIOS se exige también si la incidencia no tiene operario asociado, para
mantener el mismo contrato de autorización en listados y detalles.

| GET | Resultado |
|---|---|
| `/incidencias` | Resúmenes paginados de la empresa. |
| `/incidencias/{id}` | Ficha con descripción operativa original. |
| `/obras/{id}/incidencias` | Resúmenes de una obra accesible. |
| `/operarios/{id}/incidencias` | Resúmenes de un operario accesible. |

Sin autenticación: 401. Sin permisos, empresa inactiva, recurso ajeno,
inexistente o incoherente: 404. La autorización y la comprobación del padre
preceden a la validación de filtros. Un padre accesible sin incidencias devuelve
una página vacía. No se publican altas, cambios de estado ni borrados.

Los resúmenes contienen `id`, `obraId`, `operarioId`, `fecha`, `estado` y
`prioridad`. `operarioId` puede ser nulo; no permite deducir disponibilidad ni
la causa de esa ausencia de relación. La ficha añade `descripcion` sin alterar
el texto original. Los clientes deben mostrarla como texto, no como HTML.
No se exponen autores, fechas de auditoría, nombres, contactos ni datos privados
del operario. El permiso INCIDENCIAS incluye acceso a la descripción operativa:
la adaptación al ERP exige confirmar el contenido real y su clasificación antes
de conectar textos que pudieran contener información laboral sensible.

## Paginación y filtros

Las páginas contienen `items`, `page`, `size`, `totalElements`, `totalPages` y
`sort`. Página inicial 0, máximo 100000; tamaño inicial 20, entre 1 y 100.
Orden por `id` o `fecha`, asc/desc; predeterminado `id,asc`. Las fechas iguales
se desempatan por id en la misma dirección.

| Listado | Filtros además de page, size y sort |
|---|---|
| Empresa | obra, operario, estado, prioridad, desde, hasta |
| Obra | operario, estado, prioridad, desde, hasta |
| Operario | obra, estado, prioridad, desde, hasta |

Los identificadores de filtro son enteros positivos. Un filtro ajeno o
inexistente produce una página vacía dentro del conjunto autorizado. No se
admite repetir el padre de una ruta anidada como filtro.

Estado y prioridad comparan el texto del catálogo según la colación de MySQL
DEMO, sin comodines; máximo 300 caracteres. Se recortan los espacios extremos;
un valor vacío no filtra. Se conservan las etiquetas originales, incluso si el
catálogo está inactivo. No se fija un enum de estados ni se infieren transiciones,
fechas de resolución, responsables de cierre o un orden de urgencia.

Fechas `AAAA-MM-DD`, años 1000–9999, límites inclusivos y desde ≤ hasta.
No se admite búsqueda por descripción. Filtros desconocidos, repetidos o
malformados devuelven 400 con el contrato común de error.

## Integridad y migración

`010_incidencias.sql` añade `API_Incidencia_Empresa`, cuatro ejemplos y el
permiso INCIDENCIAS al rol de consulta DEMO. Los preparadores local y CI
descubren la migración automáticamente y registran su checksum. Se conservan
el SQL original y las migraciones anteriores.

La incidencia tiene pertenencia explícita. Su obra y el cliente de la obra deben
pertenecer a la misma empresa. Si existe operario, debe pertenecer también a esa
empresa y tener una entidad de tipo PERSONA. Una incoherencia excluye la
incidencia completa de detalles, listados y conteos. La autorización del usuario
en varias empresas no permite mezclar sus recursos.

Las bajas de operarios, clientes o catálogos no eliminan el histórico. Una
relación nula con operario se conserva, sin inventar una asignación. Las consultas
no alteran partes, stock, pedidos ni otros documentos.

## Datos y comprobación

Empresa 1, obra 1001:

- 99501: 2026-09-10, operario 301, ABIERTA, ALTA.
- 99502: 2026-09-11, sin operario, EN_PROCESO, MEDIA.
- 99504: 2026-09-11, operario 301, RESUELTA, ALTA.

Empresa 2: 99503, obra 1003, operario 302, CERRADA, BAJA. La obra 1002 no tiene
incidencias. Las descripciones son ejemplos de ajustes, entregas y embalaje.

`./scripts/entorno-local.ps1 -Accion probar` aplica migraciones y ejecuta la suite
contra `dorna_test`. `IncidenciasTests` usa transacciones con rollback y verifica
campos mínimos, descripción original, nulos, histórico, pertenencia, permisos,
filtros, paginación y denegación de escrituras. `OpenApiTests` comprueba que las
cuatro rutas coinciden con el contrato publicado.

Con el perfil dev activo, `./scripts/comprobar-acceso.ps1` comprueba el recorrido
HTTP, los errores, las relaciones y la revocación de la sesión. Véanse el
[contrato OpenAPI](../src/main/resources/static/api/v1/openapi.json) y el
la documentación histórica archivada localmente para el resultado de la entrega.

Verificado el 11 de octubre de 2026: **49 pruebas de incidencias correctas**,
sin fallos, errores ni omitidas, tras corregir la preparación de una entidad de
prueba para respetar la restricción persona/empresa de MySQL. Las otras **406
pruebas** pasaron en la ejecución general: las suites vigentes suman **455**.
Recorrido HTTP completo correcto contra `dorna_dev`; la aplicación de
comprobación se detuvo al terminar. Registros locales en
`.local/pruebas-incidencias.log`, `.local/pruebas-incidencias-corregidas.log`
y `.local/http-incidencias.log`.
JAR actualizado con `./mvnw.cmd -B -DskipTests package` después de las pruebas;
registro en `.local/empaquetado-incidencias.log`.
