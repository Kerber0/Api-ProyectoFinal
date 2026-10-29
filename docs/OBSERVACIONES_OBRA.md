# Observaciones de obra — etapa 6

Las observaciones son mensajes operativos nuevos asociados a una obra y a su
empresa propietaria. No se convierten incidencias ni campos históricos de texto.
El autor y los instantes UTC de creación y resolución los asigna el servidor;
el cliente únicamente envía el texto correspondiente. No hay archivos en esta
etapa: su asociación se integrará con el almacenamiento común de etapa 9.

## Acceso

V7 añade el permiso `OBSERVACIONES`: ADMIN recibe ADMIN; SOCIO, SOCIO_ADMIN y
LECTURA_EMPRESA conservan lectura. TRABAJADOR recibe ESCRITURA para crear
observaciones dentro de su ámbito operativo, sin recibir OBRAS, CLIENTES ni
permisos económicos. No se amplía la escritura de las cuentas ADMIN-<EMPRESA>.

Las rutas generales de obra exigen EMPRESA y OBRAS además de OBSERVACIONES.
Consultar requiere lectura; crear o resolver exige escritura en OBRAS y
OBSERVACIONES. Con los roles actuales solo ADMIN puede crear y resolver por
estas rutas. Una futura asignación explícita de ambos permisos de escritura
también habilitará esa gestión en su empresa; no se comprueba el nombre de cuenta.

Las rutas `mi-trabajo` exigen EMPRESA, TRABAJO_PROPIO y OBSERVACIONES, vínculo
cuenta–empleado activo y asignación a la obra vigente en la fecha UTC del servidor.
Reutilizan las comprobaciones de etapa 5, incluido cliente coherente con la empresa.
Leer requiere lectura OBSERVACIONES y crear requiere escritura OBSERVACIONES.
La fecha, el empleado y la identidad no se pueden elegir mediante parámetros.
Se consultan las observaciones de todos los autores de la obra asignada, no solo
las propias. Un trabajador no puede resolver mediante estas rutas ni mediante
las generales. La cancelación de la asignación o baja laboral retira acceso,
incluso a observaciones escritas por él; el historial permanece para gestión.

No existe vinculación automática por ser autor. ADMIN sin empleado puede usar
gestión general, pero tampoco eludir los requisitos de `mi-trabajo`. La ausencia
de recurso o autorización devuelve 404, y la falta de sesión devuelve 401.

## Datos y transiciones

La creación produce una observación ABIERTA, con ID, obra, autor, fecha y texto.
Resolver cambia únicamente su estado a RESUELTA y añade autor de resolución,
fecha y explicación. Texto original, autor y creación permanecen intactos.
Repetir la misma resolución normalizada devuelve el resultado original sin
añadir auditoría ni sustituir autor/fecha; una explicación diferente devuelve 409.
No se habilitan reapertura, edición ni borrado.

Los textos son planos, obligatorios y no blancos, con máximo de 2000 puntos de
código Unicode antes de recortar espacios exteriores. Se permiten tabulación y
saltos de línea; se rechazan los demás caracteres de control. No se interpretan
como HTML. Las aplicaciones cliente deberán mostrarlos como texto. Los cuerpos
no aceptan autor, fechas, estado o IDs de otra empresa/obra.

Los listados incluyen solo ID, obra, autor, creación, estado, autor de resolución
y fecha de resolución. El detalle añade texto y resolución. Los autores se
representan por ID de cuenta; no se exponen fichas personales, importes, costes
ni datos fiscales. La respuesta conserva nulos mientras no existe resolución.
Este texto es nuevo y operativo; no abre los campos libres históricos de obra.

## HTTP y persistencia

OpenAPI **0.14.0**, siete operaciones nuevas y 123 en total. `B` representa
`/api/v1/empresas/{empresa}`.

| Ruta | Métodos | Uso |
|---|---|---|
| `B/obras/{obra}/observaciones` | GET, POST | Listado y creación de gestión |
| `B/obras/{obra}/observaciones/{id}` | GET | Detalle de gestión |
| `B/obras/{obra}/observaciones/{id}/resolucion` | PUT | Resolución idempotente |
| `B/mi-trabajo/obras/{obra}/observaciones` | GET, POST | Listado y creación en obra asignada |
| `B/mi-trabajo/obras/{obra}/observaciones/{id}` | GET | Detalle en obra asignada |

POST recibe `{"texto":"Revisar acceso"}` y responde 201 con el detalle.
PUT recibe `{"resolucion":"Acceso revisado"}` y responde 200 con el detalle.
GET devuelve 200. Los listados aceptan `page` (0–100000), `size` (1–100,
defecto 20) y `estado` opcional (`ABIERTA` o `RESUELTA`), ordenados por ID
ascendente. Conteo y página comparten filtros. Parámetros desconocidos,
repetidos, fechas elegidas por el cliente o valores inválidos devuelven 400.
Se conservan el formato de error común y los límites HTTP 413/429.

V7 crea `API_Observaciones_Obra` y `API_Observaciones_Auditoria`, sin fixtures.
Una clave foránea compuesta impide asociar una obra de otra empresa. La base
exige coherencia entre estado y resolución, fechas ordenadas, autores existentes
y longitudes de texto. No hay borrados en cascada ni cambios a migraciones previas.

`ObservacionService` define la transacción y el DTO; `ObservacionRepository`
escribe el agregado con JPA y consulta resúmenes/auditoría con SQL parametrizado.
Cada creación o resolución guarda acción, autor, observación y fecha en la misma
transacción; los textos se conservan solo en el agregado, sin copiarlos a logs
HTTP ni a la auditoría. Una restricción impide duplicar una misma acción.

Las escrituras comparten el bloqueo transaccional de gestión de usuarios y
asignaciones, y vuelven a comprobar el ámbito después de obtenerlo. Esto serializa
resoluciones y evita crear tras una retirada de acceso confirmada mientras se
esperaba. Los futuros importadores y mutaciones deberán respetar ese protocolo;
el bloqueo no protege escrituras SQL externas que lo omitan. La etapa 10 deberá
integrar este historial al consolidar auditoría, conservándolo.

Evidencias y límites de verificación en
la documentación histórica archivada localmente.
