# Presupuestos y documentos de obra: bloque 4

Implementado sobre datos ficticios de la DEMO. Las consultas conservan los
importes almacenados y permiten descargar los dos archivos de ejemplo mediante
autenticación y autorización por empresa.

## Rutas y permisos

Prefijo: `/api/v1/empresas/{empresa}`. Todas las rutas son GET y requieren
`Authorization: Bearer <accessToken>` de una sesión vigente.

| Ruta | Permiso en la empresa | Respuesta |
|---|---|---|
| `/presupuestos` | PRESUPUESTOS | Página de resúmenes. |
| `/presupuestos/{id}` | PRESUPUESTOS | Cabecera con importes originales. |
| `/presupuestos/{id}/lineas` | PRESUPUESTOS | Página de líneas. |
| `/presupuestos/{id}/lineas/{linea}` | PRESUPUESTOS | Línea del presupuesto indicado. |
| `/obras/{obra}/documentos` | OBRAS y DOCUMENTOS | Página de metadatos. |
| `/obras/{obra}/documentos/{id}` | OBRAS y DOCUMENTOS | Metadatos sin rutas ni claves de almacenamiento. |
| `/obras/{obra}/documentos/{id}/contenido` | OBRAS y DOCUMENTOS | Descarga del archivo autorizado. |

Los permisos PRESUPUESTOS y DOCUMENTOS se añaden al rol
`CONSULTA_RECURSOS_DEMO` mediante la migración `003_presupuestos_documentos.sql`.
Un permiso no concede el otro. Los documentos requieren también poder consultar
la obra. Los presupuestos muestran únicamente IDs de cliente/obra; sus fichas
siguen requiriendo CLIENTES/OBRAS respectivamente.

La falta de permiso, una relación incoherente y un recurso ajeno o inexistente
devuelven el mismo 404. La ausencia de credenciales o una sesión revocada
devuelven 401. Las operaciones de escritura siguen denegadas.

## Pertenencia y datos

`API_Presupuesto_Empresa` asigna los presupuestos 4001 y 4002 a la empresa 1,
y el 4003 a la empresa 2. Cada presupuesto requiere un cliente de la misma
empresa; si tiene obra, esta debe pertenecer a esa empresa y al mismo cliente.
La condición se aplica también al conteo y a las líneas. Una línea solo se
devuelve bajo su presupuesto, aunque el usuario tenga acceso a ambos.

Los documentos heredan la empresa de la obra y exigen coherencia con su cliente.
El documento 7001 pertenece a la obra 1001 de la empresa 1; el 7002, a la obra
1003 de la empresa 2. `API_Archivo_Documento` registra la empresa y una clave
opaca del archivo; ambos deben coincidir con el documento solicitado.

Los importes, cantidades, IVA y descuentos se devuelven como cadenas decimales
exactas, por ejemplo `"3630.00"`. No se recalculan totales históricos ni se
atribuye una moneda. El esquema DEMO conserva dos decimales; esto no acredita
la precisión del ERP. Las fechas son `YYYY-MM-DD`; `fechaSubida` conserva la
fecha y hora DATETIME, sin añadir una zona horaria que el origen no proporciona.
`obraId`, `validez` y `tipo` de documento pueden ser `null`.

La cabecera declara `formatoLineas: "PLANO"`. Ordenar por ID da un orden estable
para paginar, pero no representa el orden editorial del documento. Capítulos,
partidas, mediciones, gastos, beneficio y estados reales quedan pendientes de
la fuente del ERP. La DEMO utiliza el catálogo existente `Estados_Factura`
sin afirmar que sus estados sean válidos para presupuestos reales.

## Paginación y filtros

Se mantiene el formato `items`, `page`, `size`, `totalElements`, `totalPages`,
`sort` del [contrato común](CONSULTAS_DEMO.md). `page` va de 0 a 100000 y
`size` de 1 a 100, con valores iniciales 0 y 20.

| Colección | Filtros | Campos de ordenación |
|---|---|---|
| Presupuestos | `q` sobre número, `estado`, `cliente`, `obra` | `id`, `numero`, `fecha` |
| Líneas | Sin filtros adicionales | `id` |
| Documentos | `q` sobre nombre | `id`, `nombre`, `fecha` |

El orden predeterminado es `id,asc`; se admite `asc` o `desc` y se desempata por
ID en la misma dirección. `q` admite hasta 100 caracteres y busca `%`, `_` y
`!` literalmente. `estado` admite hasta 300 caracteres y usa la comparación
del catálogo según la colación MySQL. `cliente` y `obra` son enteros positivos.
Parámetros desconocidos, repetidos o inválidos devuelven 400 en los listados.
Un filtro sin coincidencias devuelve una página vacía, conservando el conteo
correspondiente. No se infiere un ejercicio a partir de fechas o números.

```text
GET /api/v1/empresas/1/presupuestos?obra=1001&cliente=101&sort=fecha,desc
GET /api/v1/empresas/1/presupuestos/4001/lineas?page=0&size=20
GET /api/v1/empresas/1/obras/1001/documentos?q=montaje
GET /api/v1/empresas/1/obras/1001/documentos/7001/contenido
```

## Descargas privadas

Los archivos sintéticos están en `src/main/resources/demo/documentos/`, fuera
del directorio de recursos públicos. `ArchivoDemoStore` construye una ruta
interna usando empresa, documento y una clave con formato UUID; nunca abre
`Documentos.ruta`, una URL ni una ruta proporcionada por el cliente. Una clave
de otro documento tampoco permite acceder a su contenido.

La descarga limita el contenido a 10 MiB y devuelve `application/octet-stream`,
`Content-Disposition: attachment; filename="documento-{id}"`, `Content-Length`,
`Cache-Control: no-store` y `X-Content-Type-Options: nosniff`. El nombre y tipo
almacenados se consideran metadatos y no se interpolan en estas cabeceras.
Si falta el registro, el archivo o una clave válida, devuelve 404. Los metadatos
pueden existir aunque no haya contenido descargable.

Este almacén contiene únicamente ejemplos empaquetados con la aplicación;
no implementa subida, almacenamiento empresarial ni acceso a archivos del ERP.

## Contrato y comprobación

OpenAPI 3.1.1, versión de API `0.4.0`, incluye veinte rutas y veintiséis esquemas.
Se consulta en `GET /api/v1/openapi.json` con autenticación. Describe tanto las
respuestas JSON como el contenido binario, las cabeceras y los errores comunes.

```powershell
.\scripts\entorno-local.ps1 -Accion probar
# Con la API iniciada mediante -Accion ejecutar en otra terminal:
.\scripts\comprobar-acceso.ps1
```

`ExpedientesTests` cubre 33 casos: filtros y paginación, valores nulos, importes
originales, líneas ajenas, relaciones incoherentes, permisos independientes,
acceso a dos empresas, claves y nombres maliciosos, contenido exacto, cabeceras,
sesión revocada y escrituras denegadas. `OpenApiTests` comprueba la cobertura de
los controladores, las referencias internas, los parámetros de ruta y el
contrato binario. El script HTTP incluye presupuestos, líneas y documentos,
compara los bytes descargados y comprueba rechazos entre empresas y tras logout.

Verificación del 10 de octubre de 2026: **96 pruebas correctas**, cero fallos,
errores u omitidas, y recorrido HTTP completo correcto contra la aplicación
en `127.0.0.1:8080`. La aplicación de comprobación se detuvo al terminar.
El script de entorno también se verificó guardando stdout/stderr en un archivo:
los avisos de Java no producen un falso fallo en PowerShell 5.1; se conserva
el código de salida de Maven.

La integración con adiOBRASwin/MDB sigue pendiente del bloque 0. El siguiente
bloque, ya completado para la DEMO, es la consulta mínima de
[operarios y partes históricos](OPERARIOS_DEMO.md); ver
la documentación histórica archivada localmente.
