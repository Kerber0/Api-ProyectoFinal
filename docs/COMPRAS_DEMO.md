# Proveedores y pedidos de compra DEMO

Segunda entrega del bloque 9: lectura de proveedores, pedidos de proveedor y
líneas históricas. Los datos son ficticios. No acredita compatibilidad con el ERP.
La consulta de [albaranes](ALBARANES_DEMO.md) permite navegar los documentos
asociados a cada factura. El seguimiento de recepciones por línea y las escrituras
siguen pendientes de confirmar sus reglas y relaciones.

## Consultas y permisos

Todas las rutas parten de `/api/v1/empresas/{empresa}` y requieren un JWT vigente.

| Ruta GET | Permisos en esa empresa |
|---|---|
| `/proveedores` | PROVEEDORES |
| `/proveedores/{id}` | PROVEEDORES |
| `/proveedores/{id}/pedidos` | PROVEEDORES y PEDIDOS |
| `/pedidos-proveedor` | PROVEEDORES y PEDIDOS |
| `/pedidos-proveedor/{id}` | PROVEEDORES y PEDIDOS |
| `/pedidos-proveedor/{id}/lineas` | PROVEEDORES, PEDIDOS y ARTICULOS |
| `/pedidos-proveedor/{pedido}/lineas/{id}` | PROVEEDORES, PEDIDOS y ARTICULOS |

Sin autenticación: 401. Sin permiso, recurso ajeno, empresa inactiva o documento
incoherente: 404. La autorización precede a la validación de filtros. No se
publican escrituras. Se requieren los permisos de compras indicados; los permisos
financieros no los sustituyen. ADMIN y SOCIO los reciben globalmente según la
[política de usuarios](USUARIOS_LOCALES.md); las cuentas de empresa solo en la suya.

La ficha mínima del proveedor expone `id`, `codigo`, `nombre`, `tipo` y `activo`.
No expone IBAN, NIF, contactos ni observaciones. El nombre conserva la razón social
o, cuando no existe, nombre y apellidos; puede ser nulo. Incluye empresas,
autónomos y proveedores inactivos para conservar la consulta histórica.

La cabecera incluye proveedor mínimo, número, fecha, estado e importes almacenados.
Las líneas incluyen referencia de artículo, descripción histórica, cantidad,
precio de coste, descuento, IVA e importe. La descripción nula no se sustituye
por la del catálogo. Los artículos inactivos no eliminan el histórico.

## Paginación y filtros

Los listados usan `items`, `page`, `size`, `totalElements`, `totalPages` y `sort`.
`page` admite 0–100000, por defecto 0; `size`, 1–100, por defecto 20.

| Listado | Filtros | Orden |
|---|---|---|
| Proveedores | `q`: código o nombre, máximo 100 caracteres | `id`, `codigo`, `nombre` |
| Pedidos | `q`: número, `estado`, `desde`, `hasta`, `proveedor` | `id`, `numero`, `fecha` |
| Pedidos del proveedor de la ruta | Igual que pedidos, sin filtro `proveedor` | `id`, `numero`, `fecha` |
| Líneas de un pedido | Paginación y orden | `id` |

Cada orden admite `asc` o `desc`; por defecto `id,asc`. Los empates se resuelven
por id en la misma dirección. La búsqueda trata `%`, `_` y `!` como texto literal.
`estado` compara con el texto almacenado, sin introducir estados nuevos; máximo
300 caracteres. Las fechas usan `AAAA-MM-DD`, años 1000–9999, días inclusivos,
y `desde` no puede ser posterior a `hasta`.

Los filtros desconocidos, repetidos o malformados devuelven 400. Un filtro de
proveedor ajeno o inexistente en `/pedidos-proveedor` produce una lista vacía;
en `/proveedores/{id}/pedidos` el padre ajeno o inexistente devuelve 404.
Un pedido autorizado sin líneas devuelve una página vacía. Una línea de otro
pedido devuelve 404 aunque ambos pedidos sean accesibles.

## Pertenencia e integridad

La migración `008_compras.sql` añade `API_Pedido_Proveedor_Empresa` y ejemplos
sintéticos en las tablas originales. Reutiliza `API_Proveedor_Empresa` del bloque
económico y `API_Articulo_Empresa` de stock. No modifica migraciones ya aplicadas.

El pedido y el proveedor deben tener la misma empresa. Todos los artículos de
sus líneas deben tener pertenencia a esa empresa. Si cualquiera de esas
relaciones es incompatible o carece de pertenencia, se excluye **el pedido
completo** de cabeceras, listas, conteos y líneas. No se publica una versión
parcial con un total de cabecera que incluya artículos ajenos.

El proveedor habitual del catálogo de artículos no se usa como prueba de
pertenencia ni para cambiar el proveedor del pedido histórico. La serie tampoco
determina la empresa. Las asignaciones DEMO son explícitas y no constituyen un
mapeo validado de la fuente real.

## Importes y límites funcionales

Cantidades e importes se devuelven como cadenas decimales exactas con dos cifras.
Se conservan los valores históricos sin recalcular desde el catálogo ni sumar
líneas para reemplazar la cabecera. En los ejemplos, el descuento de cabecera es
un importe y el de línea es porcentual; esa convención debe contrastarse con el
ERP antes de integrar o autorizar escrituras.

El esquema reutiliza `Estados_Factura` para pedidos. Se muestra su descripción
original: `ACEPTADO` no acredita recepción, facturación ni pago. No se infieren
cantidades recibidas o pendientes, vínculos con facturas o movimientos de stock.
Tampoco se asignan compras a obras sin una relación acreditada.

La consulta de líneas revela los costes históricos a quienes tienen los tres
permisos indicados. La consulta básica de artículos sigue sin exponer costes.

## Datos y comprobación

Empresa 1, proveedor 201: pedidos 98001 (484.00, dos líneas), 98002 (217.80,
una línea con descripción nula y descuento) y 98004 (0.00, sin líneas).
Empresa 2, proveedor 202: pedido 98003 (36.30, una línea). La semilla no modifica
los saldos de stock ni los datos de facturación anteriores.

`./scripts/entorno-local.ps1 -Accion probar` aplica las migraciones pendientes y
ejecuta la suite contra `dorna_test`. `ComprasTests` verifica privacidad, importes
históricos, paginación, filtros, nulos, inactivos, permisos, accesos entre empresas
y documentos incoherentes, con transacciones que se revierten al finalizar.

El [contrato OpenAPI](../src/main/resources/static/api/v1/openapi.json) incluye
los siete endpoints. Con el perfil `dev` activo, `./scripts/comprobar-acceso.ps1`
recorre proveedores, pedidos, líneas, filtros, aislamiento y revocación de tokens.

Verificado el 11 de octubre de 2026: **47 pruebas de compras correctas** y 338 en
el conjunto de suites vigentes. La ejecución general pasó las otras 291; después
de corregir un dato de prueba que incumplía la restricción de entidades PERSONA,
se repitió la suite de compras completa con resultado correcto. El recorrido HTTP
real pasó y la aplicación de comprobación se detuvo. JAR actualizado.
