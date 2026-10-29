# Artículos, almacenes y stock DEMO

Primera entrega del bloque 9. Consultas de solo lectura sobre datos ficticios,
con pertenencia explícita por empresa. No acredita compatibilidad con el ERP.
La siguiente entrega incorpora [proveedores y pedidos de compra](COMPRAS_DEMO.md).
Albaranes, incidencias y escrituras de negocio siguen pendientes.

## Consultas

Todas las rutas parten de `/api/v1/empresas/{empresa}` y requieren un JWT vigente.

| Ruta GET | Permisos en la empresa |
|---|---|
| `/articulos` | ARTICULOS |
| `/articulos/{referencia}` | ARTICULOS |
| `/almacenes` | ALMACENES |
| `/almacenes/{id}` | ALMACENES |
| `/almacenes/{id}/stock` | ARTICULOS, ALMACENES y STOCK |
| `/almacenes/{id}/movimientos` | ARTICULOS, ALMACENES y STOCK |
| `/movimientos-stock` | ARTICULOS, ALMACENES y STOCK |
| `/movimientos-stock/{id}` | ARTICULOS, ALMACENES y STOCK |

Sin autenticación: 401. Sin permiso, empresa inactiva, recurso ajeno o inexistente:
404. La autorización precede a la validación de filtros. No se publican escrituras.
Los roles globales autorizados permiten consultar empresas, pero no cambian la
pertenencia de sus recursos. Tener acceso a dos empresas no mezcla
sus datos. Se comprueban conjuntamente artículo y almacén de origen; los registros
sin pertenencia o con pertenencias incompatibles quedan fuera del conjunto autorizado.

Los listados devuelven `items`, `page`, `size`, `totalElements`, `totalPages` y `sort`.
`page` admite 0–100000; `size`, 1–100, con 20 por defecto.
Los filtros desconocidos o repetidos y los órdenes no admitidos devuelven 400.

| Listado | Búsqueda/filtros | Orden |
|---|---|---|
| Artículos | `q`: referencia o descripción, máximo 100 caracteres | `referencia`, `descripcion`; por defecto `referencia,asc` |
| Almacenes | `q`: código o descripción, máximo 100 caracteres | `id`, `codigo`, `descripcion`; por defecto `id,asc` |
| Stock del almacén | `q`: referencia o descripción del artículo | `referencia`; por defecto `referencia,asc` |
| Movimientos, generales o del almacén | `desde`, `hasta`, opcionales, días inclusivos | `id`, `fecha`; por defecto `id,asc` |

Todos los órdenes admiten `asc` y `desc`, con desempate por referencia o id en la
misma dirección. La búsqueda trata `%`, `_` y `!` como texto literal. Las fechas
usan `AAAA-MM-DD`, entre 1000 y 9999, y `desde` no puede superar `hasta`.
La fecha del movimiento conserva el DATETIME local de la DEMO, sin inventar una
zona horaria. El filtro `hasta` incluye las 23:59:59.

Los artículos exponen referencia, descripción, marca, modelo, unidad y estado
activo; los almacenes, id, código, descripción y estado activo. No se incluyen
precios, costes, datos del proveedor, contactos, observaciones ni textos libres
del movimiento. Los inactivos y los campos nulos se conservan en las consultas.

## Cálculo y límites

La migración `007_stock.sql` añade `API_Articulo_Empresa`, `API_Almacen_Empresa` y
`API_Traspaso_Stock`, sin modificar las migraciones anteriores ni el SQL original.
Los scripts de entorno local y CI la aplican por orden y controlan su checksum.
Los identificadores de esta entrega son exclusivamente sintéticos.

El saldo usa todos los movimientos autorizados del artículo y almacén:

- ENTRADA suma una cantidad no negativa.
- SALIDA resta una cantidad no negativa.
- AJUSTE añade una variación con signo; no representa un recuento absoluto.
- TRASPASO resta en `almacenId` y suma una vez en `almacenDestinoId`. El destino
  estructurado debe ser distinto del origen y pertenecer a la misma empresa.

Los textos `origen` y `destino` del esquema original nunca se interpretan.
La vista global `vw_stock_actual` no se utiliza. El historial por almacén incluye
los traspasos donde ese almacén es origen o destino; el listado general devuelve
cada movimiento una sola vez. `efectoOrigen` siempre es el efecto sobre el origen;
el destino recibe el signo contrario. Los filtros de fecha limitan el historial,
no el saldo completo de `/stock`.

Las cantidades se devuelven como cadenas decimales exactas con dos cifras. Cada
almacén devuelve todos los artículos de su empresa, incluidos los que no tienen
movimientos con `0.00`. No es un catálogo de artículos asignados al almacén.
Un saldo negativo se conserva; no se recorta a cero. No se descuentan reservas ni
se infiere disponibilidad, inventario físico, valoración o material imputado a obra.

Si una entrada, salida o traspaso tiene cantidad negativa, su efecto no se puede
interpretar: el saldo afectado devuelve `cantidad: null`, `calculable: false` y
`movimientosPendientes > 0`. Lo mismo ocurre con un traspaso sin destino válido.
Como ese destino es desconocido, se marcan inciertos **todos los almacenes de la
empresa para ese artículo**. Nunca se revela un identificador de destino ajeno.
Si el destino está identificado pero la cantidad es inválida, se marcan el origen
y ese destino. Los demás artículos siguen calculándose.

`calculable` expresa coherencia del conjunto autorizado, no integridad del origen
ni una conciliación con el ERP. Los movimientos excluidos por pertenencia no se
cuentan ni se revelan como pendientes. Antes de integrar datos reales se deben
confirmar pertenencias, unidades, precisión, signo de ajustes, transferencias y
posibles movimientos anulados.

## Datos y verificación

Empresa 1: almacenes 101 y 102; artículos DEMO-ART-001, 002 y 004. Empresa 2:
almacén 201 y artículo DEMO-ART-003. El artículo 001 tiene entrada 10, salida 1,
traspaso 2 del almacén 101 al 102 y ajuste -0.50. Sus saldos son **6.50 y 2.00**,
sumando 8.50. El artículo 002 conserva 4.00 en el almacén 101 y el 004 no tiene
movimientos. La empresa 2 tiene 7.00 del artículo 003.

Ejecutar `./scripts/entorno-local.ps1 -Accion probar` para migrar las bases DEMO
y verificar toda la suite. `StockTests` cubre saldos conciliados con el historial,
traspasos, ajustes, cantidades inválidas, destinos desconocidos o ajenos,
pertenencia incoherente, permisos, privacidad, paginación, fechas y errores.
Las pruebas usan transacciones con rollback en `dorna_test`.

Verificado el 11 de octubre de 2026: **291 pruebas correctas**, incluidas 43 de
stock, cero fallos, errores u omitidas. El recorrido HTTP real completo pasó con
el perfil `dev`; la aplicación de comprobación se detuvo al terminar.

El [contrato OpenAPI](../src/main/resources/static/api/v1/openapi.json) incluye
los ocho endpoints y sus respuestas. El recorrido real
`./scripts/comprobar-acceso.ps1`, con el perfil dev activo, comprueba también
catálogos, saldos, traspaso, ajuste, accesos cruzados y revocación del token.
