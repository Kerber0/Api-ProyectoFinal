# Tesorería — etapa 8

Gestión interna en EUR de vencimientos, cobros, pagos y devoluciones de las
facturas emitidas por el módulo de etapa 7. `documentoId` identifica la gestión;
`facturaId`, la cabecera económica. Los históricos siguen disponibles por las
rutas anteriores y no se convierten en planes o aplicaciones inferidos.

## Plan de vencimientos

PUT crea un plan de 1–50 cuotas ordenadas por fecha, no anteriores a la factura.
Se permiten varias cuotas del mismo día y vencimientos futuros. Sus importes,
con signo y hasta dos decimales, deben sumar exactamente el total del documento;
no se redondea el reparto. Todas las cuotas tienen el signo de la factura y son
distintas de cero. Las facturas de total cero no necesitan ni admiten plan.

El plan es inmutable. Repetir las mismas fechas e importes es idempotente;
cambiarlo devuelve 409. No se genera automáticamente desde la fecha única de
cabecera. Una emitida sin plan devuelve una lista vacía. No se crean planes
para borradores, anuladas o históricos sin gestión.

La consulta general devuelve pendientes por defecto, ordenados por fecha e ID.
Permite incluir liquidados con `pendientes=false`, filtrar por documento,
tercero y fechas, y paginar. El pendiente es el importe menos las aplicaciones
de movimientos no revertidos. Un crédito pendiente es negativo.

## Cobros, pagos y reparto

Un movimiento admite 1–50 aplicaciones a vencimientos de la misma empresa,
tipo y tercero, incluso de varias facturas. Todas tienen el signo del
movimiento, suman exactamente su importe y no exceden en valor absoluto el
pendiente de cada cuota. No se repite un vencimiento en el reparto. Se admite
pago anticipado respecto al vencimiento, pero no una fecha anterior a la
factura ni futura respecto al reloj UTC del servidor.

CLIENTE registra cobros; PROVEEDOR, pagos. Métodos: EFECTIVO, TRANSFERENCIA,
TARJETA, BIZUM y CHEQUE. No hay saldo sin aplicar, anticipos independientes de
factura, divisas ni reparto automático.

Cada alta lleva una `clave` UUID en minúsculas, única por empresa y tipo.
Un reintento con igual contenido normalizado devuelve el movimiento actual,
incluso conciliado o revertido. Otro contenido devuelve 409. El orden del
reparto y los ceros decimales finales no alteran esa identidad.

Cada aplicación materializa un Cobro o Pago en el modelo existente; las
consultas e informes anteriores reflejan los importes sin sumarlos dos veces.
El detalle enlaza sus identificadores económicos. El número de filas económicas
es el de aplicaciones, no el de operaciones bancarias. Antes de escribir se
contrasta el saldo económico con las aplicaciones del documento; una diferencia
devuelve 409 y requiere revisión explícita. No se absorben movimientos externos
en cuotas supuestas.

## Conciliación y reversión

La conciliación es una confirmación **manual** con referencia obligatoria,
única por empresa y tipo y sensible a mayúsculas. Conserva autor y fecha en
auditoría y cambia REGISTRADO a CONCILIADO. No consulta bancos, verifica
extractos ni certifica una conciliación bancaria automática. Repetir la misma
referencia es idempotente; sustituirla o reutilizarla devuelve 409.

La reversión exige fecha no anterior al movimiento ni futura, y motivo.
Admite REGISTRADO o CONCILIADO. Crea un contramovimiento económico por cada
aplicación, con importe opuesto, y cambia a REVERTIDO. Conserva movimientos,
aplicaciones, conciliación y referencia reservada; reabre el pendiente.
Repetir fecha y motivo es idempotente; otro contenido devuelve 409. La
corrección posterior utiliza otra clave y, si corresponde, otra referencia.
No hay borrado, edición de aplicaciones, reversión parcial ni desconciliación.

## Rectificativas y compatibilidad

Rectificar conserva el original y genera una factura negativa. No redistribuye
cobros ni cancela cuotas automáticamente. El original RECTIFICADO conserva su
plan; la rectificativa emitida admite cuotas y devoluciones negativas. Ambos
saldos se consultan separados; su suma representa el neto cuando se seleccionan
ambos documentos. Una devolución y su reversión conservan sus signos exactos.

No se compensa automáticamente deuda y crédito ni se genera caja al emitir
una rectificativa. No existe todavía compensación interna sin caja; no debe
simularse con cobros y devoluciones ficticios.

Las rutas anteriores `vencimientos-cliente/proveedor` conservan la fecha única
de cabecera y el saldo aritmético por factura; no enumeran las nuevas cuotas.
La planificación y selección de pendientes usan las rutas de tesorería. Los
informes conservan sus períodos y reglas; no se reinterpretan como conciliados.

## Permisos y transacciones

EMPRESA y FACTURAS_CLIENTE/FACTURAS_PROVEEDOR en lectura; COBROS/PAGOS en lectura
para GET y escritura para mutaciones. ADMIN conserva escritura, las cuentas
lectoras lectura y TRABAJADOR carece de acceso económico. Las listas omiten
referencias, motivos y autor; el detalle autorizado los contiene. No se guardan
datos bancarios.

Las escrituras comparten el bloqueo transaccional de facturación y usuarios,
revalidando permisos tras esperar. Movimiento, filas económicas, aplicaciones
y auditoría se confirman o revierten juntos. La serialización global prioriza
coherencia y no acredita capacidad con carga representativa. Los futuros
importadores deben usar el servicio, no escribir directamente en las tablas.

V9 incorpora cuatro tablas y tres entidades JPA, sin modificar filas históricas.
Retira cascadas de borrado de factura a Cobros/Pagos. Sus importes JPA admiten
signo para devoluciones y contramovimientos; el servicio valida importe no nulo,
distinto de cero, rango, signo y saldo. Las aplicaciones protegen los movimientos
económicos ante borrado mediante claves foráneas restrictivas.

## Contrato 0.16.0

Prefijo `B=/api/v1/empresas/{empresa}/tesoreria/{tipo}`; tipo CLIENTE o PROVEEDOR.
Importes de salida en texto con dos decimales. Entradas sin campos adicionales.
Se conserva el límite HTTP común de 16 KiB.

| Ruta | Método | Uso |
|---|---|---|
| `B/documentos/{id}/vencimientos` | GET, PUT | Consultar o crear plan inmutable |
| `B/vencimientos` | GET | Pendientes por defecto, orden fecha/ID |
| `B/movimientos` | GET, POST | Listar o registrar reparto |
| `B/movimientos/{id}` | GET | Detalle y aplicaciones |
| `B/movimientos/{id}/conciliacion` | PUT | Confirmación manual con referencia |
| `B/movimientos/{id}/reversion` | PUT | Contramovimientos con fecha y motivo |

Listados: `page` 0–100000, `size` 1–100 (defecto 20). Vencimientos admite además
`documento`, `tercero`, `desde`, `hasta`, `pendientes=true|false`. Se rechazan
parámetros adicionales o repetidos. Errores: 400 validación, 404 ámbito/permiso,
409 saldo, suma, idempotencia o transición.

Resultados y continuación en la documentación histórica archivada localmente.
