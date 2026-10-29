# Informes: bloque 7 de la DEMO

La API agrega en PostgreSQL las facturas y movimientos autorizados de cada empresa.
Ofrece seis series mensuales y sus totales, con importes exactos como cadenas de
dos decimales. El alcance es la DEMO; la conciliación con adiOBRASwin sigue
pendiente de confirmar el origen y las reglas del ERP.

## Rutas y permisos

Todas las operaciones son GET, bajo `/api/v1/empresas/{empresa}/informes`.
Requieren JWT y sesión vigente. La migración `006_informes.sql` incorpora el
permiso INFORMES al rol `CONSULTA_RECURSOS_DEMO`.

| Ruta | Permisos en esa empresa, además de INFORMES | Fecha que selecciona los registros |
|---|---|---|
| `/facturacion-cliente` | FACTURAS_CLIENTE | Emisión de factura. |
| `/facturacion-proveedor` | FACTURAS_PROVEEDOR | Emisión de factura. |
| `/cobros` | FACTURAS_CLIENTE y COBROS | Fecha efectiva del cobro. |
| `/pagos` | FACTURAS_PROVEEDOR y PAGOS | Fecha efectiva del pago. |
| `/saldos-cliente` | FACTURAS_CLIENTE y COBROS | Emisión de factura. |
| `/saldos-proveedor` | FACTURAS_PROVEEDOR y PAGOS | Emisión de factura. |

INFORMES no sustituye a los permisos de las fuentes. La autorización se comprueba
antes de validar parámetros y agregar. Empresa ajena, inexistente o permiso
insuficiente devuelven el mismo 404; sesión inválida o revocada devuelve 401.
Los datos y conteos excluyen facturas sin pertenencia explícita o con tercero u
obra incoherentes, conforme a [Economía DEMO](ECONOMIA_DEMO.md). Un usuario con
acceso a dos empresas recibe exclusivamente las cifras de la empresa de la ruta.

## Fechas y respuesta

`desde` y `hasta` son obligatorios e inclusivos, en `YYYY-MM-DD`, entre
1000-01-01 y 9999-12-31. Se exige `desde <= hasta`. La consulta puede abarcar
como máximo 24 meses naturales contando ambos extremos: de enero de 2025 a
diciembre de 2026 es válido; hasta enero de 2027 no lo es. Se rechazan fechas
imposibles, parámetros repetidos y cualquier otro parámetro con 400.

No hay paginación: la base agrega todos los registros autorizados del período.
La respuesta contiene `empresaId`, `tipo` (CLIENTE/PROVEEDOR), `desde`, `hasta`,
`criterio`, `meses` y `totales`. Cada mes incluye `mes` (`YYYY-MM`) y `valores`.
La serie se ordena cronológicamente y completa con cero los meses sin registros
autorizados. En los meses extremos se incluyen solo los días del intervalo.
El cero describe el conjunto consultado de la DEMO; no certifica ausencia de
actividad en el ERP. No se inventa una moneda que el esquema no almacena.

| Informe | Valores y totales | Criterio |
|---|---|---|
| Facturación | `facturas`, `subtotal`, `descuento`, `impuestos`, `total` | `FACTURAS_POR_FECHA_EMISION` |
| Cobros/pagos | `movimientos`, `importe` | `MOVIMIENTOS_POR_FECHA_EFECTIVA` |
| Saldos | `facturas`, `totalFacturas`, `movimientosRegistrados`, `saldoAritmetico` | `DEMO_FACTURAS_EMITIDAS_MENOS_MOVIMIENTOS_COMPLETOS` |

Facturación suma cabeceras originales, sin recalcular descuentos ni impuestos ni
filtrar por estado. Las líneas y presupuestos no participan en estas sumas.
Cobros/pagos cuenta y suma movimientos efectivos, aunque su factura se haya
emitido fuera del intervalo.

Saldos selecciona facturas emitidas en el intervalo y resta **todos sus
movimientos registrados**, incluidos los fechados fuera de ese intervalo o en
el futuro. Se agrega primero cada factura para evitar multiplicar importes por
líneas o movimientos. El resultado conserva signos y saldos negativos.
**No es un saldo histórico a fecha de corte**, una previsión de tesorería ni una
conciliación contable. Un mes sin facturas emitidas tiene saldo cero en esta
serie, aunque se hayan cobrado facturas de meses anteriores.

## Ejemplo conciliado

```text
GET /api/v1/empresas/1/informes/facturacion-cliente?desde=2026-09-01&hasta=2026-10-31
GET /api/v1/empresas/1/informes/cobros?desde=2026-09-01&hasta=2026-10-31
GET /api/v1/empresas/1/informes/saldos-cliente?desde=2026-09-01&hasta=2026-10-31
```

Cifras de la semilla para empresa 1, septiembre–octubre de 2026:

| Magnitud | Cliente | Proveedor |
|---|---|---|
| Facturas emitidas en septiembre | 3 | 3 |
| Total de facturas | 4235.00 | 1694.00 |
| Movimientos efectivos de septiembre | 1005.00 | 542.00 |
| Movimientos efectivos de octubre | 600.00 | 200.00 |
| Total de movimientos | 1605.00 | 742.00 |
| Saldo aritmético de las facturas seleccionadas | 2630.00 | 952.00 |

La facturación y el saldo de octubre son cero porque la semilla no contiene
facturas emitidas ese mes. Sus cobros/pagos sí aparecen en octubre. La empresa 2
se comprueba por separado y no se suma a estas cifras.

## Verificación y límites

El contrato OpenAPI 3.1.1, versión `0.7.0`, contiene 50 rutas y 49 esquemas.
Se obtiene con JWT en `GET /api/v1/openapi.json`.

```powershell
.\scripts\entorno-local.ps1 -Accion probar
# Con la aplicación iniciada mediante -Accion ejecutar:
.\scripts\comprobar-acceso.ps1
```

`InformesTests` compara totales con las consultas de facturas, movimientos y
saldos individuales. Comprueba meses vacíos, días inclusivos, signos, importes
originales, movimientos fuera del intervalo, pertenencias incoherentes,
permisos independientes, acceso a dos empresas, sesiones revocadas y parámetros
estrictos. Incluye 250 facturas y 500 cobros transaccionales para comprobar
agregaciones mayores que una página, sin truncamiento ni pérdida de precisión.
Las pruebas usan exclusivamente `dorna_test` y revierten sus cambios.
El recorrido HTTP comprueba las seis rutas, totales, meses vacíos, rechazos por
empresa, fechas inválidas y acceso denegado tras logout.

Verificación del 11 de octubre de 2026: **231 pruebas correctas**, cero fallos,
errores u omitidas; 48 corresponden a informes y 4 al contrato OpenAPI.
Recorrido HTTP completo correcto en `127.0.0.1:8080`, incluidos los bloques
anteriores. La aplicación de comprobación se detuvo al terminar.

Las pruebas de volumen son funcionales; no equivalen a una prueba de carga de
producción. Quedan pendientes la conciliación real, certificaciones, abonos,
divisas, anticipos, previsiones, imputación de compras a obra e informes de
beneficio. Las vistas originales del SQL no se usan para estos cálculos.
El siguiente bloque es la preparación de despliegue; ver
la documentación histórica archivada localmente.
