# Albaranes de cliente y proveedor DEMO

Tercera entrega del bloque 9: consulta de albaranes, líneas históricas y relaciones
de cabecera con facturas opcionales. Todos los ejemplos son ficticios; no acreditan
compatibilidad con el ERP real. No se publican escrituras.

## Endpoints y permisos

Las rutas parten de `/api/v1/empresas/{empresa}` y requieren un JWT vigente.

| GET | Permisos en la empresa |
|---|---|
| `/albaranes-cliente` | ALBARANES_CLIENTE, CLIENTES y FACTURAS_CLIENTE |
| `/albaranes-cliente/{id}` | ALBARANES_CLIENTE, CLIENTES y FACTURAS_CLIENTE |
| `/albaranes-cliente/{id}/lineas` | Los anteriores y ARTICULOS |
| `/albaranes-cliente/{albaran}/lineas/{id}` | Los anteriores y ARTICULOS |
| `/facturas-cliente/{id}/albaranes` | ALBARANES_CLIENTE, CLIENTES y FACTURAS_CLIENTE |
| `/albaranes-proveedor` | ALBARANES_PROVEEDOR, PROVEEDORES y FACTURAS_PROVEEDOR |
| `/albaranes-proveedor/{id}` | ALBARANES_PROVEEDOR, PROVEEDORES y FACTURAS_PROVEEDOR |
| `/albaranes-proveedor/{id}/lineas` | Los anteriores y ARTICULOS |
| `/albaranes-proveedor/{albaran}/lineas/{id}` | Los anteriores y ARTICULOS |
| `/facturas-proveedor/{id}/albaranes` | ALBARANES_PROVEEDOR, PROVEEDORES y FACTURAS_PROVEEDOR |

FACTURAS_CLIENTE o FACTURAS_PROVEEDOR se requieren también cuando la relación opcional es nula. El
permiso ARTICULOS se requiere para todas las líneas, incluidas las de referencia
nula. El permiso del otro tipo de albarán no concede acceso. Sin autenticación:
401. Sin permiso, empresa inactiva, recurso ajeno o incoherente: 404. La
autorización precede a la validación de filtros; no se publican escrituras.

Las cabeceras exponen id, tipo CLIENTE/PROVEEDOR, número, fecha, estado, tercero
mínimo (id, código y nombre), facturaId e importes históricos. La factura puede
ser nula y una factura puede agrupar varios albaranes. No se exponen observaciones
ni datos bancarios o de contacto.

Las líneas exponen id, albaranId, tipo, referenciaArticulo, descripción, cantidad,
precio, descuento, IVA e importe. No se consulta el precio actual del catálogo
para sustituir el precio histórico. Referencia, descripción e importe pueden ser
nulos y se devuelven así; un importe desconocido no se transforma en cero.
Una referencia nula no permite distinguir un servicio de un artículo eliminado.

## Paginación y filtros

Todos los listados devuelven `items`, `page`, `size`, `totalElements`, `totalPages`
y `sort`. Página 0 por defecto, máximo 100000; tamaño 20 por defecto, entre 1 y 100.
Una factura accesible sin albaranes devuelve una página vacía; un padre
ajeno o inexistente devuelve 404. Las líneas se buscan siempre dentro de su albarán.

| Listado | Filtros además de page, size y sort |
|---|---|
| Albaranes de cliente | q, estado, desde, hasta, cliente, factura |
| Albaranes de proveedor | q, estado, desde, hasta, proveedor, factura |
| Albaranes de una factura cliente | q, estado, desde, hasta, cliente |
| Albaranes de una factura proveedor | q, estado, desde, hasta, proveedor |
| Líneas | Ninguno |

Cabeceras: orden `id`, `numero` o `fecha`, asc/desc, por defecto `id,asc`. Desempate
por id en la misma dirección. Líneas: `id,asc` o `id,desc`.
`q` busca literalmente en el número (máximo 100 caracteres); `%`, `_` y `!` no
actúan como comodines. `estado` compara el texto original, máximo 300 caracteres.
Las fechas usan `AAAA-MM-DD`, años 1000–9999, límites inclusivos y desde ≤ hasta.
Los identificadores de filtro son enteros positivos. Un filtro ajeno o inexistente
produce una lista vacía dentro del conjunto autorizado.

No se admiten filtros desconocidos, repetidos o malformados: devuelven 400.
La ruta anidada no admite volver a especificar factura como filtro.

## Integridad y pertenencia

La migración `009_albaranes.sql` incorpora las tablas de pertenencia explícita
`API_Albaran_Cliente_Empresa` y `API_Albaran_Proveedor_Empresa`, los ejemplos y los
permisos del rol DEMO. No modifica el SQL original ni migraciones ya aplicadas.
Los preparadores local y CI aplican las migraciones con control de checksum.
`DB/propia/012_albaranes_facturas.sql` sustituye las relaciones antiguas por
`id_factura` opcional con clave foránea del tipo correspondiente y borrado
restringido. `014_albaranes_facturas_ejemplos.sql` asigna solo vínculos ficticios
explícitos. Estos scripts no convierten datos reales del ERP.

El albarán, su tercero y todos los artículos referenciados deben pertenecer a la
misma empresa. Una línea con referencia nula conserva la pertenencia del documento.
Si una referencia no nula es ajena o no tiene asignación, se oculta **el albarán
completo**, tanto en listas y conteos como en detalles y líneas.

Si hay factura, debe pertenecer a la misma empresa y al mismo tercero que el
albarán. La obra opcional de una factura cliente debe tener pertenencia coherente
con esa factura. Una factura incoherente oculta sus albaranes asociados. Las
bajas de terceros o artículos no borran el histórico.

No existe relación actual albarán–pedido ni correspondencia entre sus líneas.
Es necesario definirla antes de calcular recepciones por línea.

## Límites funcionales

Los importes y cantidades se devuelven como cadenas decimales exactas con dos
cifras, sin recalcular la cabecera desde las líneas. El estado se conserva desde
`Estados_Factura`, reutilizado por el esquema DEMO, y no prueba facturación, pago
ni recepción completa. El vínculo explícito identifica una factura, pero no
recalcula sus importes ni acredita que se haya pagado.

La consulta no genera movimientos de stock, no actualiza el pedido y no deduce
cantidades recibidas, pendientes, reservas ni disponibilidad. Estas reglas y la
pertenencia real siguen pendientes del ERP.

## Datos y verificación

Empresa 1: albaranes cliente 99001 (factura 5001, dos líneas, total 242.00), 99002
(sin factura, línea con importe nulo, total 90.75) y 99004 (sin líneas).
Albaranes proveedor 99101 y 99102 asociados a la factura 6001 (242.00 y 121.00),
99104 sin factura ni líneas y 99105 con línea sin referencia, descripción ni importe.
Empresa 2: cliente 99003 (factura 5004) y proveedor 99103 (factura 6004), ambos de 12.10.
No cambian los saldos de stock, facturas ni pedidos anteriores.

`./scripts/entorno-local.ps1 -Accion probar` aplica migraciones y ejecuta las pruebas
en `dorna_test`. `AlbaranesTests` comprueba ambas fuentes, histórico, nulos,
pertenencias de líneas y padres, permisos, filtros, paginación y accesos cruzados.
Las pruebas usan transacciones con rollback.

El [contrato OpenAPI](../src/main/resources/static/api/v1/openapi.json) incluye
los diez endpoints. `./scripts/comprobar-acceso.ps1`, con el perfil dev activo,
comprueba documentos, líneas, relaciones, nulos, errores y revocación de la sesión.

Verificación histórica anterior al cambio de relación, el 11 de octubre de 2026:
**406 pruebas correctas** en una ejecución
completa, incluidas las **68 de albaranes**, sin fallos, errores ni omitidas.
El recorrido HTTP completo pasó con el perfil `dev` contra `dorna_dev`; la
aplicación de comprobación se detuvo al terminar. Registros locales:
`.local/pruebas-albaranes-cierre.log` y `.local/http-albaranes-cierre.log`.
JAR actualizado con `./mvnw.cmd -B -DskipTests package` después de la suite
completa; registro en `.local/empaquetado-albaranes-cierre.log`.

## Cambio de contrato 0.10.0

Las rutas `/obras/{id}/albaranes` y `/pedidos-proveedor/{id}/albaranes` se
sustituyen por las rutas de factura indicadas arriba. `facturaId` sustituye a
`obraId` y `pedidoId`; el filtro es `factura`. Los clientes de la versión 0.9.4
deben adaptar estas consultas. El resultado vigente se registra en
la documentación histórica archivada localmente.
