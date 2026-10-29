# Auditoría transversal

Etapa 10. Contrato `0.18.0`. V11 consolida los siete historiales de negocio en
`API_Auditoria`, conservando las tablas originales y sus reglas. La tabla de
consulta es una proyección JDBC inmutable; no necesita una entidad JPA de
escritura. El modelo conserva sus 63 entidades.

## Registro transaccional

Cada INSERT en los historiales de maestros, usuarios, empleo, observaciones,
facturación, tesorería y documentos genera un evento central mediante un trigger
PostgreSQL. Ambos registros y la operación de negocio comparten transacción.
Si falla la consolidación, falla la operación. La clave única `(modulo,origen_id)`
impide consolidar dos veces el mismo evento. Los reintentos idempotentes de los
módulos conservan su comportamiento, bloqueos y validaciones.

Cada evento conserva ID propio, módulo, ID del historial original, autor, empresa,
entidad/ID, acción, fecha UTC, indicador histórico y `cambios` JSONB. La empresa es
la propietaria; al compartir maestros es la de origen y el destino se conserva
como referencia. Los cambios de cuentas globales pueden no tener empresa.
Los identificadores se conservan sin claves foráneas desde la proyección: una
futura retirada de la entidad o cuenta no debe borrar el evento.

`cambios` tiene `referencias`, `antes` y `despues`. Las instantáneas son de campos
permitidos, no copias completas de entidades ni del cuerpo HTTP. `antes` es la
última instantánea comparable registrada por la API; vale null cuando no existe.
No prueba cambios realizados directamente con SQL fuera del flujo de negocio.
La fecha original puede coincidir entre eventos; el ID desempata el orden.

La migración incorpora eventos anteriores con `historico=true`, sus fechas y
autores originales, y ambas instantáneas null. Nunca atribuye el estado actual
de una entidad a una operación antigua. Los eventos nuevos usan `historico=false`.
No se inventan eventos para datos anteriores que carecen de historial.

## Datos permitidos y privacidad

| Módulo | Información estructurada conservada |
|---|---|
| Maestros | Tipo e ID del maestro, empresa de origen/destino, concesión de visibilidad. |
| Usuarios | ID de cuenta, estado activo, ID de rol y concesión/revocación; renovación de credencial como indicador. |
| Laboral | Operario/referencia; obra y fechas de asignación, cancelación y vínculo. Para ausencias, solo ID y cancelación. |
| Observaciones | Obra y transición de estado. |
| Facturación | Serie, ejercicio/tipo, rectificativa; estado, número, total decimal y original de factura. |
| Tesorería | Número e importe de cuotas; estado, tipo, importe y fecha del movimiento. |
| Documentos | ID y asociación a obra/presupuesto/ausencia; indicador de incorporación. |

Las acciones de roles, credenciales y visibilidad no utilizan `antes`, porque
describen relaciones distintas de la ficha principal. Las operaciones de cuenta
registran la acción de actualización pero no guardan nombres anteriores o nuevos.

Se excluyen contraseñas y hashes, JWT/sesiones, nombres de usuario, identidad
fiscal, datos bancarios, referencias y motivos libres, texto/resolución de
observaciones, tipo de ausencia y nombre, ruta, clave, hash o contenido de archivo.
Los importes permitidos se serializan como cadenas decimales exactas.
La auditoría sigue siendo privada: los IDs también pueden vincularse a personas.

## Consulta y permisos

- `GET /api/v1/auditoria`
- `GET /api/v1/auditoria/{id}`

Ambas rutas requieren sesión vigente, usuario y rol global activos, y permiso
`AUDITORIA` activo en nivel `ADMIN`. V11 lo concede únicamente al rol ADMIN.
ADMIN asignado solo por empresa, SOCIO, lectores y TRABAJADOR no pueden consultar
esta proyección. Los historiales y consultas propios de cada módulo mantienen
sus permisos anteriores. La falta de permiso devuelve 404; sin sesión, 401.
Los permisos se comprueban en cada petición, sin almacenarlos en el JWT.

El listado admite `page` (0–100000), `size` (1–100, por defecto 20), `empresa`,
`autor`, `modulo`, `desde` y `hasta`. Los IDs son positivos. Las fechas son
instantes ISO 8601 con zona entre los años 1 y 9999: desde inclusiva y hasta
exclusiva, con desde anterior a hasta cuando se envían ambas. Parámetros
desconocidos, repetidos o inválidos devuelven 400. Filtrar por empresa excluye
eventos globales sin empresa. No existe un filtro para ampliar los privilegios.

Orden fijo `fecha DESC, id DESC`. Conteo y página comparten una transacción de
lectura `REPEATABLE_READ`; páginas solicitadas en instantes diferentes pueden
desplazarse si se incorporan eventos. No se ofrece un cursor de exportación.
Las rutas de modificación del historial no están publicadas.

## Operación y límites

La tabla central rechaza UPDATE, DELETE y TRUNCATE mediante trigger. Esta
protección no sustituye la separación de cuentas de base de datos, ni constituye
una firma criptográfica o almacenamiento externo inviolable frente al propietario
del esquema. El usuario de ejecución sigue sin permisos DDL. Los historiales
originales se conservan; el nuevo registro no cambia su política de mantenimiento.

La copia PostgreSQL incluye tabla, JSONB, secuencia, funciones y triggers. Deben
restaurarse conjuntamente, no reconstruirse eventos nuevos desde el estado actual.
La consulta no contiene los bytes documentales. No hay borrado automático ni
retención temporal: se definirán con la operación del destino definitivo.

`scripts/ensayar-recuperacion.ps1` conserva `dorna_dev` como origen por defecto.
Para probar eventos ficticios sin introducirlos en desarrollo, admite
`-BaseOrigen dorna_ci_check_<8 hex>` sobre una base DEMO de ensayo ya preparada.
Verifica la instancia local y la marca DEMO, crea un destino nuevo y compara
conteos/hashes de todas las tablas, secuencias y vistas, sin modificar el origen.
Comprueba también las definiciones de las cuatro funciones y los nueve triggers de
auditoría, y que la copia restaurada rechaza UPDATE, DELETE y TRUNCATE. Estos
intentos se revierten incluso si falta la protección. El manifiesto registra
el número de eventos recuperados y el resultado de estas comprobaciones.

El arranque y provisión de cuentas iniciales son operaciones de instalación;
no se atribuye a una persona una autoría inexistente ni se auditan como peticiones
de gestión. `HttpAuditFilter` mantiene por separado requestId/estado/duración.
El registro de negocio cubre las escrituras auditadas de etapas 3–9 y el núcleo
de gestión de etapa 11, no cambios manuales de SQL ni CRUD todavía no implementado.
V12 incorpora `API_Gestion_Auditoria`, su trigger y el módulo GESTION. Conserva
versiones, actividad, estados y totales permitidos; excluye identidad y contacto.
Las pruebas verifican rollback de gestión y numeración ante fallo de auditoría.
Véase [Gestión desde escritorio](GESTION_ESCRITORIO.md).
