# Economía: bloque 6 de la DEMO

Este documento describe el contrato de lectura e históricos. La gestión nueva
de etapa 7 y sus reglas de cálculo están en [Facturación](FACTURACION.md).
Las afirmaciones históricas sobre ausencia de escritura no se aplican a esas
rutas de gestión. Las consultas económicas mantienen su formato y conservan
los importes de los documentos antiguos.

La API ofrece lectura de facturas de cliente y proveedor, líneas, cobros, pagos,
vencimientos y saldos aritméticos. Los datos son ficticios y los cálculos se
limitan a reglas explícitas compatibles con el esquema local. No acreditan una
conciliación contable ni las reglas económicas de adiOBRASwin.

## Pertenencia y permisos

La migración `005_economia.sql` añade `API_Proveedor_Empresa`,
`API_Factura_Cliente_Empresa` y `API_Factura_Proveedor_Empresa`. Cada registro
asignado tiene una empresa en la DEMO. Las pertenencias no se infieren de números,
fechas, series ni nombres de terceros.

| Empresa | Proveedores | Facturas cliente | Facturas proveedor |
|---|---|---|---|
| 1 | 201 | 5001, 5002, 5003 | 6001, 6002, 6003 |
| 2 | 202 | 5004 | 6004 |

Una factura de cliente debe tener un cliente de su misma empresa. Si tiene obra,
esta debe pertenecer a esa empresa y al mismo cliente. La obra puede ser `null`.
Una factura de proveedor requiere un proveedor de su misma empresa; el esquema
no relaciona esa factura con una obra, por lo que no se inventa tal imputación.
Los proveedores compartidos y la correspondencia empresa/ejercicio del ERP
quedan pendientes de confirmar.

Las líneas y movimientos heredan la pertenencia de la factura. Las consultas
verifican su coherencia antes de exponer cualquier detalle, importe o conteo.
Un usuario autorizado en ambas empresas tampoco puede mezclar IDs en las rutas.

El rol `CONSULTA_RECURSOS_DEMO` recibe FACTURAS_CLIENTE, FACTURAS_PROVEEDOR,
COBROS y PAGOS. El permiso histórico FACTURAS no habilita estas operaciones.

| Operación | Permisos en la empresa |
|---|---|
| Facturas cliente y sus líneas | FACTURAS_CLIENTE |
| Cobros, saldo y vencimientos cliente | FACTURAS_CLIENTE y COBROS |
| Facturas proveedor y sus líneas | FACTURAS_PROVEEDOR |
| Pagos, saldo y vencimientos proveedor | FACTURAS_PROVEEDOR y PAGOS |

Las facturas no incluyen importes acumulados ni saldos calculados. Esos datos
requieren también el permiso del movimiento correspondiente. Sí incluyen el
estado original del documento. La referencia de tercero muestra solo ID, código
y nombre; no habilita la consulta de su ficha completa.

JWT y sesión vigente son obligatorios. Falta de permiso, recurso ajeno,
inexistente o con pertenencia incoherente devuelven el mismo 404; una sesión
inválida devuelve 401. No se habilita escritura económica.

## Rutas

Prefijo: `/api/v1/empresas/{empresa}`. Todas las rutas son GET.

| Cliente | Proveedor | Respuesta |
|---|---|---|
| `/facturas-cliente` | `/facturas-proveedor` | Página de facturas originales. |
| `/facturas-cliente/{id}` | `/facturas-proveedor/{id}` | Factura. |
| `/facturas-cliente/{id}/lineas` | `/facturas-proveedor/{id}/lineas` | Página de líneas. |
| `/facturas-cliente/{id}/lineas/{linea}` | `/facturas-proveedor/{id}/lineas/{linea}` | Línea de esa factura. |
| `/facturas-cliente/{id}/cobros` | `/facturas-proveedor/{id}/pagos` | Página de movimientos de esa factura. |
| `/facturas-cliente/{id}/saldo` | `/facturas-proveedor/{id}/saldo` | Saldo aritmético DEMO. |
| `/cobros` | `/pagos` | Página de movimientos efectivos. |
| `/cobros/{id}` | `/pagos/{id}` | Movimiento. |
| `/vencimientos-cliente` | `/vencimientos-proveedor` | Página de vencimientos de factura. |

## Importes y reglas

Los decimales se devuelven como cadenas exactas de dos decimales, sin pasar por
coma flotante. Cabeceras y líneas conservan sus importes originales, aunque no
coincidan entre sí. Las líneas admiten campos nulos según el esquema. No se
recalculan descuentos o impuestos ni se atribuye una moneda no almacenada.

La factura incluye `tipo` (CLIENTE/PROVEEDOR), `estadoOriginal`, referencia
`tercero`, `obraId`, fechas y los importes `subtotal`, `descuento`, `impuestos`
y `total`. En proveedor, `obraId` siempre es `null`.

Un cobro/pago expone exclusivamente `id`, `facturaId`, `fecha`, `importe` y
`metodoPago`. Se omiten IBAN, número de operación y observaciones.

Regla `DEMO_TOTAL_MENOS_MOVIMIENTOS`:

```text
movimientosRegistrados = SUM(importe de todos los cobros/pagos de la factura)
                        o 0.00 si no hay movimientos
saldoAritmetico = total original de la factura - movimientosRegistrados
```

Los movimientos se agregan por factura antes de unirlos al documento; no se
unen a las líneas para calcular sumas. Así, varias líneas y varios movimientos
no multiplican los importes. El estado original no determina el saldo y no se
modifica al consultar. Los signos se conservan: un exceso de movimientos puede
dar saldo negativo y un movimiento negativo reduce el acumulado. Esto no afirma
que tales registros tengan un significado contable concreto en el ERP.

Los filtros de fecha de un listado seleccionan registros y no recortan el
acumulado. El saldo utiliza todos los movimientos registrados, incluso si su
fecha queda fuera del rango consultado. **No es un saldo histórico a fecha de
corte**, ni excluye automáticamente movimientos fechados en el futuro.

Ejemplos de la semilla de empresa 1:

| Factura | Total | Movimientos registrados | Saldo aritmético |
|---|---|---|---|
| Cliente 5001 | 1210.00 | 0.00 | 1210.00 |
| Cliente 5002 | 2420.00 | 1000.00 | 1420.00 |
| Cliente 5003 | 605.00 | 605.00 | 0.00 |
| Proveedor 6001 | 484.00 | 0.00 | 484.00 |
| Proveedor 6002 | 968.00 | 500.00 | 468.00 |
| Proveedor 6003 | 242.00 | 242.00 | 0.00 |

## Vencimientos y filtros

El origen DEMO tiene una única `fecha_vencimiento` opcional por factura. La
consulta de vencimientos devuelve un registro por factura con fecha informada,
incluidas las facturas con saldo cero o negativo. Las facturas sin fecha no
aparecen allí, pero mantienen disponibles su ficha y su saldo. No se crean
cuotas, importes de vencimiento independientes ni previsiones de pago.

Todos los listados usan el [contrato común](CONSULTAS_DEMO.md): `page` 0–100000,
`size` 1–100, predeterminados 0 y 20; `sort=campo,asc|desc`, predeterminado
`id,asc`, con desempate por ID en la misma dirección.

| Listado | Filtros | Ordenación | Fecha filtrada por `desde`/`hasta` |
|---|---|---|---|
| Facturas | `q`, `estado`, `cliente` o `proveedor`; `obra` solo cliente | `id`, `numero`, `fecha` | Emisión de factura. |
| Líneas | Sin filtros adicionales | `id` | No admite fechas. |
| Cobros/pagos generales | `factura`, `cliente` o `proveedor`; `obra` solo cobros | `id`, `fecha` | Movimiento efectivo. |
| Cobros/pagos de una factura | Sin filtros de IDs | `id`, `fecha` | Movimiento efectivo. |
| Vencimientos | `cliente` o `proveedor`; `obra` solo cliente | `id`, `fecha` | Vencimiento de factura. |

`q` busca literalmente en el número, con máximo 100 caracteres. `estado`
compara el estado original según la colación MySQL, con máximo 300 caracteres.
Los IDs de filtro son positivos. Las fechas son inclusivas, en `YYYY-MM-DD`
válido entre 1000-01-01 y 9999-12-31; se admite un único extremo, pero no
`desde > hasta`. Los parámetros desconocidos, repetidos o inválidos devuelven
400. Las rutas anidadas no admiten reemplazar el ID del padre mediante filtros.

```text
GET /api/v1/empresas/1/facturas-cliente?cliente=101&obra=1001&sort=fecha,desc
GET /api/v1/empresas/1/facturas-cliente/5002/saldo
GET /api/v1/empresas/1/cobros?desde=2026-10-01&hasta=2026-10-31
GET /api/v1/empresas/1/vencimientos-proveedor?desde=2026-10-01&hasta=2026-10-31
```

## Comprobación y límites

OpenAPI 3.1.1, versión de API `0.6.0`, documenta cuarenta y cuatro rutas y
cuarenta esquemas. Se obtiene mediante `GET /api/v1/openapi.json` con JWT.

```powershell
.\scripts\entorno-local.ps1 -Accion probar
# Con la aplicación iniciada mediante -Accion ejecutar:
.\scripts\comprobar-acceso.ps1
```

`EconomiaTests` verifica saldos sin movimientos, parciales y completos, signos,
estados originales, varias líneas sin multiplicación de sumas, fechas efectivas
y vencimientos, campos nulos, privacidad, permisos independientes, dos empresas,
pertenencias incoherentes, filtros y paginación, sesiones revocadas y escrituras
denegadas. Los cambios de prueba se revierten en `dorna_test`. El recorrido HTTP
comprueba ambas clases de factura, líneas, movimientos, vencimientos y saldos.

Verificación del 10 de octubre de 2026: **182 pruebas correctas**, cero fallos,
errores u omitidas; 46 corresponden a economía. Recorrido HTTP completo correcto
en `127.0.0.1:8080`, incluidos los bloques anteriores, rechazos entre empresas y
acceso denegado tras logout. La aplicación de comprobación se detuvo al terminar.

Quedan pendientes de fuente real: certificaciones, abonos, anulaciones,
devoluciones, anticipos sin factura, vencimientos múltiples, divisas,
imputación de compras a obra y conciliación con el ERP. El cálculo aritmético
DEMO no resuelve esas reglas. El bloque 7 amplía estas consultas con
[informes mensuales autorizados](INFORMES_DEMO.md); véase el
la documentación histórica archivada localmente.
