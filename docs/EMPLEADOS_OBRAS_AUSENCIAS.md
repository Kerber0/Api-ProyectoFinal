# Empleados, obras y ausencias — etapa 5

Se reutiliza `Operario` como ficha del empleado. Las nuevas relaciones son datos
propios del sistema central; no se importan ni se deducen del calendario, los
partes históricos, el nombre de usuario o la entidad fiscal. V6 no introduce
vínculos ni asignaciones ficticias automáticamente.

## Cuenta y empleado

`API_Usuario_Operario` vincula una cuenta con un operario y viceversa. Se admite
una cuenta sin empleado y un empleado sin cuenta. El operario conserva su empresa
propietaria; una cuenta puede tener roles en otras empresas, pero su consulta
personal solo muestra al empleado vinculado en su empresa.

Consultar, crear o retirar el vínculo exige `USUARIOS=ADMIN` **global**, además
de `EMPRESA` y `OPERARIOS` de la empresa activa. Escribir exige escritura en
`OPERARIOS`. Una cuenta ADMIN local no administra la identidad central.
El vínculo no concede roles. Solo se vinculan cuentas activas; repetir la misma
relación es idempotente y reasignar exige desvincular primero. El vínculo puede
prepararse antes del alta laboral, pero no habilita consultas antes de esa fecha.

Cambiar el vínculo revoca todas las sesiones de la cuenta y registra autor,
empresa, empleado, acción e identificador de cuenta. Se bloquea el usuario antes
de sus sesiones, con el mismo orden que login/refresh. Desvincular conserva las
asignaciones, las ausencias y los partes del empleado. Una nueva cuenta vinculada
al mismo empleado accede a sus datos según los permisos y fechas vigentes.

## Asignaciones y consulta propia

Una asignación contiene empresa, empleado, obra, fecha inicial, fecha final
opcional y marca de cancelación. Ambos extremos son inclusivos; fin nulo significa
período abierto. Las claves foráneas compuestas impiden mezclar empresas de obra
y empleado. La API exige además coherencia del cliente de la obra con la empresa.
El período debe quedar dentro del alta/baja del empleado con entidad activa.

No se admiten períodos solapados no cancelados para el mismo empleado y obra.
Sí se permiten distintas obras simultáneas: la asignación concede acceso y no
certifica disponibilidad ni reserva horas. Una ausencia no cancela asignaciones.
Para corregir una asignación se cancela y se crea otra; los registros originales
se conservan. La cancelación es idempotente, se audita y retira acceso en la
próxima petición. No se asigna a nadie por haber registrado partes en una obra.

Leer asignaciones requiere `EMPRESA`, `OPERARIOS` y `OBRAS`. Crearlas o cancelarlas
requiere escritura en los dos últimos. ADMIN puede gestionarlas; SOCIO y las
cuentas de empresa conservan lectura. TRABAJADOR no consulta asignaciones ajenas.

V6 añade `TRABAJO_PROPIO`: lectura para TRABAJADOR y los roles lectores existentes,
ADMIN para el rol ADMIN. Se suma a `EMPRESA`; **no concede OBRAS, CLIENTES ni
permisos económicos**. Las rutas `mi-trabajo` siempre resuelven al empleado desde
la identidad autenticada, incluso para ADMIN. Sin vínculo, permiso, empresa
activa, entidad activa o alta laboral vigente devuelven 404.

Las obras y contactos exigen una asignación vigente en la **fecha UTC del servidor**.
No se permite elegir la fecha para acceder a obras pasadas o futuras. Esta fecha
se calcula una vez por operación y se reutiliza en sus consultas. La fecha de baja
es inclusiva. Tener una asignación no abre los endpoints generales de obra.

La ficha propia contiene identificador, código y nombre. La obra contiene código,
título, estado, fechas de inicio/fin prevista y ubicación, que puede ser nula.
Omite presupuesto, costes, descripción y observaciones libres. Los contactos
incluyen solo identificador de asociación, nombre, apellidos, teléfono y móvil:
deben estar activos, asociados expresamente a la obra y pertenecer a su cliente.
No se concede acceso a la ficha general del cliente ni a otros contactos.

## Ausencias y privacidad

ADMIN global registra y consulta ausencias de empleados en empresas autorizadas;
se aplica la misma política de identidad central que a los vínculos. SOCIO y las
cuentas locales no reciben acceso general a ausencias por tener lectura OPERARIOS.
Cada trabajador puede consultar **solo sus propias ausencias** con TRABAJO_PROPIO
y vínculo vigente. No puede crearlas, cancelarlas ni consultar las de compañeros.

El registro contiene empleado, empresa, fechas inclusivas obligatorias, tipo
`VACACIONES`, `PERMISO`, `BAJA` u `OTRA`, y cancelación. No contiene diagnóstico,
notas libres, NSS, justificante, aprobación o cálculo de días laborables. El tipo
BAJA es una clasificación administrativa, no un dato clínico. No hay flujo de
solicitud o aprobación ni escritura en calendario o partes de mano de obra.

Se rechazan intervalos invertidos, fuera del alta/baja laboral o solapados con
otra ausencia no cancelada. Para corregir se cancela y se crea un registro nuevo.
Los listados conservan canceladas para mostrar el historial explícitamente.
La auditoría identifica el registro y la acción, sin copiar el tipo ni datos
personales a los registros HTTP. El registro de ausencia original se conserva.

## Contrato HTTP

OpenAPI **0.13.0** añade 14 operaciones; se conservan las consultas anteriores.
`B` representa `/api/v1/empresas/{empresa}`.

| Ruta | Métodos | Alcance |
|---|---|---|
| `B/operarios/{id}/usuario` | GET, PUT, DELETE | Consulta/vínculo/desvínculo central |
| `B/operarios/{id}/asignaciones` | GET, POST | Historial por período y alta |
| `B/operarios/{id}/asignaciones/{asignacion}` | DELETE | Cancelación lógica |
| `B/operarios/{id}/ausencias` | GET, POST | Historial privado por período y alta |
| `B/operarios/{id}/ausencias/{ausencia}` | DELETE | Cancelación lógica |
| `B/mi-trabajo/empleado` | GET | Ficha propia mínima |
| `B/mi-trabajo/obras` | GET | Obras asignadas vigentes |
| `B/mi-trabajo/obras/{obra}` | GET | Detalle operativo mínimo |
| `B/mi-trabajo/obras/{obra}/contactos` | GET | Contactos de la obra autorizada |
| `B/mi-trabajo/ausencias` | GET | Historial propio por período |

Los listados admiten `page` (0–100000) y `size` (1–100, defecto 20), ordenados por
ID ascendente. Asignaciones y ausencias exigen además `desde` y `hasta`, años
1–9999 y hasta 366 días de diferencia; seleccionan intersecciones inclusivas.
Se rechazan parámetros desconocidos o repetidos. Conteos y páginas usan el mismo
ámbito. Las altas devuelven 201 y el registro; vínculo y cancelaciones, 204.
404 oculta inexistencia y falta de permisos; 400 valida entradas y 409 expresa
conflictos de vínculo, período laboral o solapamiento con el error común.

## Persistencia, concurrencia y límites

`EmpleoService` delimita transacciones; `EmpleoRepository` escribe las tres nuevas
entidades mediante JPA y usa SQL específico para lecturas, bloqueo y auditoría.
No se serializan entidades. `API_Laboral_Auditoria` comparte la transacción del
cambio. No hay borrado físico de asignaciones o ausencias por HTTP.

Las escrituras laborales comparten el bloqueo asesor transaccional de gestión
de usuarios y revalidan autoridad tras adquirirlo. Serializan comprobación e
inserción para impedir solapamientos simultáneos. Esta garantía se aplica a la
API; los importadores futuros y operaciones SQL deben respetar el mismo protocolo.
Las claves foráneas, unicidad 1:1 y validación de intervalos sí están en la base.
No se modifican las migraciones aplicadas ni se infiere información del ERP.

No se amplía aquí el CRUD general de empleados u obras, la planificación de
capacidad, las tareas/incidencias del trabajador, las observaciones de etapa 6,
el control horario ni los flujos de aprobación. La auditoría global de etapa 10
deberá integrar este historial sin perderlo.

## Justificantes incorporados en etapa 9

La relación privada ausencia–documento conserva empresa, autor, fecha, nombre,
MIME, tamaño, SHA-256 y referencia opaca del almacenamiento común. Los bytes
permanecen fuera de la base. No se aceptan rutas locales ni URL públicas.

ADMIN global incorpora justificantes con escritura DOCUMENTOS y OPERARIOS.
El titular consulta y descarga los suyos con vínculo y alta laboral vigentes.
Leer obras o tener OPERARIOS no concede acceso a justificantes ajenos. Las
ausencias canceladas conservan sus archivos, pero no admiten nuevas subidas.
Contrato, validación y recuperación en
[Documentos y almacenamiento](DOCUMENTOS_Y_ALMACENAMIENTO.md).

Evidencias de ejecución en la documentación histórica archivada localmente.
