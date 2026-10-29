# Operarios y partes históricos: bloque 5

La DEMO permite consultar la ficha laboral mínima y las horas registradas en
partes de mano de obra. Son operaciones de lectura con JWT y permisos por empresa.
No representan asignaciones futuras, disponibilidad, vacaciones ni ausencias.

## Pertenencia y permisos

La migración `004_operarios_partes.sql` crea `API_Operario_Empresa`: el operario
301 pertenece a la empresa 1; el 302, a la empresa 2. Es una asignación sintética,
con una empresa por operario. No acredita las relaciones laborales del ERP ni
resuelve traslados entre empresas o pertenencias con vigencia temporal.

Los partes heredan su ámbito de la obra. Solo se exponen cuando obra, cliente
de la obra y operario tienen pertenencias explícitas a la misma empresa. Esta
condición se aplica al detalle, al listado y al conteo, incluso cuando el usuario
tiene acceso a las dos empresas. Las fichas laborales requieren una entidad de
tipo PERSONA. Los recursos sin asignación y las relaciones incoherentes se ocultan.

El rol `CONSULTA_RECURSOS_DEMO` recibe OPERARIOS y PARTES_MANO_OBRA; las consultas
de partes requieren además OBRAS. La falta de cualquier permiso necesario,
un recurso ajeno y un recurso inexistente devuelven el mismo 404. Una sesión
inválida o revocada devuelve 401. Las escrituras permanecen denegadas.

Prefijo: `/api/v1/empresas/{empresa}`.

| Ruta GET | Permisos | Respuesta |
|---|---|---|
| `/operarios` | OPERARIOS | Página de fichas mínimas. |
| `/operarios/{id}` | OPERARIOS | Ficha mínima. |
| `/partes-mano-obra` | OPERARIOS, PARTES_MANO_OBRA y OBRAS | Página de partes históricos. |
| `/partes-mano-obra/{id}` | Los mismos tres permisos | Parte histórico. |
| `/operarios/{id}/partes` | Los mismos tres permisos | Partes del operario autorizado. |
| `/obras/{id}/partes` | Los mismos tres permisos | Partes de la obra autorizada. |

Las rutas anidadas comprueban también el recurso padre: un operario u obra
inexistente/ajeno devuelve 404; un recurso autorizado sin partes devuelve
una página vacía. Un ID ajeno usado como filtro en el listado general no produce
coincidencias y no permite acceder al recurso.

## Datos expuestos

La ficha contiene exclusivamente `id`, `codigo`, `nombre`, `categoria`,
`fechaAlta` y `fechaBaja`. El nombre se forma con nombre y apellidos, y puede
ser `null` si faltan ambos. La categoría y la fecha de baja pueden ser `null`.
No se exponen NSS, NIF, nacimiento, contacto privado, observaciones, jornada
semanal ni costes salariales.

El parte contiene `id`, `obraId`, `operarioId`, `fecha`, `horaInicio`, `horaFin`
y `horasTrabajadas`. Las horas trabajadas conservan el decimal almacenado como
cadena, por ejemplo `"8.00"`, sin recalcularlo a partir de entrada/salida.
Las horas de inicio/fin pueden ser `null`; no se infieren turnos, descansos ni
zonas horarias. Se omiten costes y observaciones del parte.

Los operarios de baja o con entidad inactiva y las categorías inactivas se
conservan en la consulta histórica. No se deduce un estado de disponibilidad
a partir de esas marcas. La API lee los registros existentes sin interpretar
una fecha de parte como una asignación futura.

## Filtros y paginación

Se utiliza el [contrato común de paginación](CONSULTAS_DEMO.md): `page` de 0 a
100000, `size` de 1 a 100 y `sort=campo,asc` o `campo,desc`. Los valores
predeterminados son 0, 20 e `id,asc`; el desempate siempre usa ID en la misma
dirección. Las páginas fuera del resultado conservan los totales filtrados.

| Colección | Filtros | Ordenación |
|---|---|---|
| Operarios | `q` por código o nombre/apellidos | `id`, `codigo`, `nombre` |
| Partes generales | `obra`, `operario`, `desde`, `hasta` | `id`, `fecha` |
| Partes de un operario | `obra`, `desde`, `hasta` | `id`, `fecha` |
| Partes de una obra | `operario`, `desde`, `hasta` | `id`, `fecha` |

`q` admite hasta 100 caracteres y busca `%`, `_` y `!` literalmente.
Los IDs de filtro son enteros positivos. Las fechas exigen `YYYY-MM-DD` válido,
entre 1000-01-01 y 9999-12-31, con ambos extremos inclusivos. Puede indicarse
solo un extremo. `desde` posterior a `hasta`, fechas imposibles, parámetros
repetidos, desconocidos o fuera de rango devuelven 400.

Las rutas anidadas no admiten sobrescribir mediante filtros el ID del padre.
Por ejemplo, `/operarios/301/partes?operario=302` devuelve 400.

```text
GET /api/v1/empresas/1/operarios?q=Uno&sort=nombre,asc
GET /api/v1/empresas/1/partes-mano-obra?obra=1001&operario=301&desde=2026-09-10&hasta=2026-09-11
GET /api/v1/empresas/1/operarios/301/partes?sort=fecha,desc
GET /api/v1/empresas/1/obras/1001/partes?size=20
```

## Contrato y verificación

OpenAPI 3.1.1, versión de API `0.5.0`, describe veintiséis rutas y treinta
esquemas. El contrato está disponible con autenticación en
`GET /api/v1/openapi.json`.

```powershell
.\scripts\entorno-local.ps1 -Accion probar
# Con la aplicación iniciada con -Accion ejecutar en otra terminal:
.\scripts\comprobar-acceso.ps1
```

`LaboralTests` verifica privacidad de campos, horas originales, datos nulos,
operarios de baja, filtros inclusivos, orden con empates, paginación, acceso a
dos empresas, relaciones incoherentes, permisos retirados, parámetros inválidos,
sesiones revocadas y escrituras denegadas. Los cambios de prueba se revierten
en `dorna_test`. El script HTTP comprueba fichas y partes, sus campos exactos,
rechazos entre empresas, fechas inválidas y acceso tras logout.

Verificación del 10 de octubre de 2026: **136 pruebas correctas**, cero fallos,
errores u omitidas; 40 corresponden a este bloque. Recorrido HTTP completo
correcto en `127.0.0.1:8080`, incluyendo todos los bloques anteriores. La
aplicación de comprobación se detuvo al terminar; MySQL conserva los datos.

La integración de operarios y partes reales requiere confirmar la fuente del
ERP, el significado de sus horas y la pertenencia empresa/ejercicio. El siguiente
bloque, ya completado para la DEMO, es [economía](ECONOMIA_DEMO.md); ver
la documentación histórica archivada localmente.
