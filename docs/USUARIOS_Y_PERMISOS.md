# Usuarios y permisos — etapa 4

La gestión de identidad es central: una cuenta puede tener asignaciones en
varias empresas. Las asignaciones locales no facultan para modificar la cuenta
compartida, sus credenciales o accesos de otras empresas.

## Política de acceso

| Operación | Autorización |
|---|---|
| Consultar cuentas, asignaciones, roles y catálogo de permisos | Rol global inscrito en `API_Roles_Globales`, activo, con lectura `USUARIOS` |
| Crear o modificar cuenta, restablecer contraseña, conceder o revocar roles | Rol global activo con `USUARIOS` en nivel `ADMIN` |
| Consultar mis permisos en una empresa | Lectura `EMPRESA` en esa empresa activa |

ADMIN conserva administración global y SOCIO lectura global. Las cuatro
cuentas `ADMIN-<EMPRESA>` mantienen `LECTURA_EMPRESA`. Una asignación local de
ADMIN no da administración global. El servidor consulta permisos vigentes;
no deduce autoridad del nombre de usuario ni acepta el autor desde el cliente.
La falta de autorización o de recurso se devuelve como 404.

Desde etapa 10, la consulta transversal del historial requiere además permiso
`AUDITORIA` en nivel `ADMIN` mediante un rol global. Solo ADMIN lo recibe
inicialmente; SOCIO y administradores locales no acceden a esta consulta privada.
Véase [Auditoría transversal](AUDITORIA.md).

Los roles y sus matrices se consultan, pero no se editan por HTTP: cambiar una
matriz compartida afectaría a todas sus asignaciones. Esta etapa gestiona los
permisos por empresa asignando o retirando roles existentes. Los globales solo
pueden asignarse si figuran expresamente en `API_Roles_Globales`. Un rol global
también puede asignarse localmente sin extender su alcance a otras empresas.
Se puede retirar una asignación cuya empresa o rol se hayan desactivado; no se
pueden conceder accesos a cuentas, empresas o roles inactivos.

## Contrato HTTP

OpenAPI `0.12.0` añade trece operaciones; las anteriores conservan su formato.

| Ruta | Métodos | Comportamiento |
|---|---|---|
| `/api/v1/usuarios` | GET, POST | Página de cuentas; alta activa sin asignaciones, con 201 y Location |
| `/api/v1/usuarios/{id}` | GET, PUT | Detalle mínimo; sustitución de nombre y estado activo |
| `/api/v1/usuarios/{id}/password` | PUT | Restablecimiento por administrador, 204 |
| `/api/v1/usuarios/{id}/accesos` | GET | Roles explícitos; empresa nula para los globales |
| `/api/v1/usuarios/{id}/empresas/{empresa}/roles/{rol}` | PUT, DELETE | Concesión o revocación local, 204 |
| `/api/v1/usuarios/{id}/roles-globales/{rol}` | PUT, DELETE | Concesión o revocación global, 204 |
| `/api/v1/roles` | GET | Roles, estado, ámbito global permitido y matriz de permisos activos |
| `/api/v1/permisos` | GET | Catálogo de permisos y su estado |
| `/api/v1/empresas/{empresa}/permisos` | GET | Mayor nivel efectivo de cada permiso del usuario autenticado |

El listado admite `page` (0–100000) y `size` (1–100, por defecto 20), con orden
estable por ID. Rechaza parámetros desconocidos y repetidos. Las respuestas de
cuenta contienen únicamente `id`, `usuario` y `activo`: no incluyen correo,
entidad fiscal, datos laborales, contraseñas ni hashes. Los DTO iniciales
`Usuario*DTO` se conservan como borradores; no se conectan a estos endpoints.

Los nombres admiten hasta 50 caracteres, sin espacios exteriores ni controles.
La unicidad sigue la collation española de PostgreSQL. Las contraseñas nuevas
requieren al menos 12 caracteres Unicode completos (puntos de código) y como
máximo 72 bytes UTF-8, de acuerdo con
el codificador BCrypt usado. Los DTO las marcan solo de entrada y las omiten de
`toString`. La política de alta no cambia las contraseñas existentes.
Duplicados, concesión a cuenta inactiva y retirada del último administrador
devuelven 409 con el contrato común de error, sin detalles SQL.

No hay borrado físico de usuarios, autorregistro, envío de contraseñas ni
recuperación por correo. La desactivación conserva las referencias históricas.
El restablecimiento es una operación administrativa; no existe todavía un flujo
de recuperación autónoma o de cambio por el usuario final.

## Transacciones, sesiones y auditoría

`GestionUsuariosService` delimita las transacciones. El repositorio utiliza JPA
para Usuario y SQL explícito para las asignaciones ya existentes, los catálogos
y la auditoría; comparten la misma transacción. Un bloqueo asesor transaccional
PostgreSQL serializa la gestión administrativa. La autorización se comprueba
otra vez tras adquirirlo, y se bloquea la cuenta afectada.

No puede retirarse ni desactivarse el último administrador global activo con
`USUARIOS=ADMIN`, incluso con dos solicitudes simultáneas. El conflicto revierte
el cambio completo. Esta garantía corresponde a la API; las escrituras SQL
directas y la provisión operativa deben respetar la misma política.

Cambiar nombre, estado, contraseña o una asignación revoca todas las sesiones
de la cuenta afectada. Reactivarla no recupera sesiones anteriores. Repetir
una asignación sin cambios no genera auditoría ni revocación adicional.
Login bloquea la cuenta antes de comprobar la clave; refresh bloquea usuario
y después sesión. La gestión usa el mismo orden para evitar que una renovación
concurrente deje vigente una sesión anterior al restablecimiento de contraseña.

La estructura incluye `API_Usuarios_Auditoria`: autor, usuario afectado,
acción, empresa y rol cuando proceda, e instante UTC. No almacena contraseñas,
hashes ni tokens. Se registra dentro de la transacción del cambio. No es una
auditoría global de todas las entidades ni una protección frente al administrador
de la base; esa consolidación corresponde a la etapa 10.

La audiencia JWT conserva el identificador histórico `dorna-mobile` y su
validación estricta. Identifica el destino API; no otorga privilegios por tipo
de cliente. El escritorio usará la misma autenticación y autorización del
servidor. Mantener el identificador evita invalidar tokens vigentes por un
cambio cosmético; no se aceptan audiencias alternativas.

## Trabajador y continuidad

V5 incorpora `TRABAJADOR` como rol local limitado a lectura `EMPRESA`. No concede
OBRAS, CLIENTES, OPERARIOS ni permisos económicos. El detalle actual de obra
incluye presupuesto y sus contactos requieren CLIENTES; no son respuestas
adecuadas para abrir acceso general al trabajador.

V6, en la etapa 5, añade TRABAJO_PROPIO junto al vínculo usuario–empleado y las
asignaciones explícitas. Las rutas `mi-trabajo` devuelven la ficha propia, obras
asignadas vigentes, contactos mínimos y ausencias propias. Requieren empleado
activo y nunca conceden acceso general a obras, clientes, tareas, incidencias
o economía. Vínculos y ausencias ajenas exigen administración global; SOCIO y
cuentas locales conservan lectura de asignaciones de obra, sin acceso general
a ausencias. Véase [Empleados, obras y ausencias](EMPLEADOS_OBRAS_AUSENCIAS.md).
V7 añade OBSERVACIONES con ESCRITURA para TRABAJADOR, limitada por la asignación
vigente a crear observaciones operativas. Resolver exige también escritura OBRAS;
no la recibe el trabajador. Las cuentas lectoras reciben solo lectura del nuevo
permiso. Véase [Observaciones de obra](OBSERVACIONES_OBRA.md).
Añadir otros roles a una cuenta suma permisos; el rol limitado no reduce
privilegios concedidos por otros roles.

Las seis cuentas de desarrollo conservan su política. El script de provisión
local sigue siendo una operación explícita que converge esas cuentas al perfil
documentado; no debe ejecutarse para editar cuentas gestionadas por la API.
Evidencias y alcance de verificación en la documentación histórica archivada localmente.
