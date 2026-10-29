# Facturación — etapa 7

Gestión interna en EUR de documentos nuevos de cliente y proveedor. Los
históricos se conservan sin recalcular, renumerar ni convertir sus estados.
El registro de proveedor conserva su referencia externa obligatoria: el número
interno asignado por la API no sustituye el número del documento recibido.
Este módulo no acredita facturación fiscal completa, comunicación tributaria,
factura electrónica, firma, impresión ni cumplimiento de un régimen fiscal.

## Reglas de cálculo y ciclo

Cada línea contiene descripción, cantidad positiva, precio no negativo,
descuento porcentual e IVA porcentual entre 0 y 100. Son decimales exactos
en texto, hasta dos decimales; cantidad y precio admiten ocho enteros. Se
admiten 1–50 líneas y el límite HTTP común de 16 KiB sigue aplicándose.
No se interpretan tipos fiscales ni se selecciona automáticamente un IVA.

Regla `EUR_LINEA_HALF_UP_V1`, dos decimales y HALF_UP en cada paso:

1. Bruto = redondear(cantidad × precio).
2. Rebaja = redondear(bruto × descuento / 100).
3. Base = bruto − rebaja.
4. Cuota = redondear(base × IVA / 100).
5. Total = base + cuota.

La cabecera suma brutos, rebajas, cuotas y totales ya redondeados. Se rechaza
cualquier desbordamiento de NUMERIC(12,2). El `importe` de la línea económica
emitida es su total con IVA. Los históricos mantienen su significado original.
No hay descuentos globales, retenciones, recargo, divisas ni cálculos enviados
por el cliente. Se rechazan campos adicionales en los nuevos cuerpos JSON.

La creación guarda un BORRADOR sin número ni efecto en las consultas económicas.
No se edita: para sustituirlo se anula y se crea otro. La emisión asigna número
y materializa cabecera y líneas mediante las entidades JPA existentes, todo en
la misma transacción que el contador y la auditoría. Repetir PUT de emisión
devuelve el mismo documento sin consumir número ni duplicar la factura.

Solo un borrador se puede ANULAR, con motivo obligatorio; no consume número.
Repetir la misma anulación es idempotente; otro motivo produce 409.
Las emitidas no se borran, editan ni anulan directamente.

La rectificación es total: crea un borrador en una serie rectificativa,
vinculado a una factura nueva EMITIDA del mismo tipo y empresa. Copia
exactamente sus líneas e identidad y niega cantidades y todos los importes,
sin volver a redondear. Al emitirlo, el original pasa a RECTIFICADA en gestión;
su factura económica conserva sus importes. Original y rectificativa suman cero
en los informes que abarcan ambos documentos. No se ajustan cobros ni pagos
automáticamente: sus aplicaciones y devoluciones se gestionan en
[Tesorería — etapa 8](TESORERIA.md). La corrección sustitutiva requiere
después un nuevo documento ordinario. No hay rectificaciones parciales,
encadenadas ni de históricos sin procedencia gestionada.

Solo puede existir una rectificación no anulada por original. Un borrador
rectificativo anulado permite preparar otro. El motivo de creación y el de
anulación quedan conservados en sus respectivas entradas de auditoría.

## Series, fechas y ámbito

Las series nuevas pertenecen a una empresa, tipo y ejercicio. Nombre y prefijo
son únicos dentro de ese ámbito. Prefijo de 1–20 caracteres A–Z, 0–9 o guion;
longitud de 1–9 dígitos, primer número 1, relleno con ceros. No se reutilizan
ni asignan empresas a las series históricas. Una serie agotada devuelve 409.
La naturaleza ordinaria/rectificativa es inmutable por API.

Fecha obligatoria del ejercicio, no futura respecto al reloj UTC; emisión sin
retroceder respecto a la última fecha de la serie. Rectificación no anterior al
original. El vencimiento único opcional no puede preceder a la fecha; las
rectificativas no generan vencimiento automáticamente.

La empresa debe estar activa y disponer de identidad fiscal; el tercero debe
ser visible en ella y activo para documentos ordinarios. Obra opcional solo
para cliente, de la misma empresa y cliente. Se conserva una copia de nombre
y NIF de empresa y tercero, actualizada al emitir el ordinario e inmutable
después. No es una ficha fiscal completa ni incluye domicilios. Las
rectificativas conservan la identidad del original, incluso si cambió el maestro.

El permiso EMPRESA y FACTURAS_CLIENTE o FACTURAS_PROVEEDOR controla cada tipo.
Lectura para consultar; ESCRITURA o ADMIN para todas las mutaciones, incluidas
series. Las cuentas lectoras y TRABAJADOR no reciben escritura. Autorización
revalidada después del bloqueo compartido con gestión de usuarios; las series
se bloquean también en PostgreSQL. Recursos ajenos o sin permiso devuelven 404.

## Contrato y persistencia

Prefijo `B=/api/v1/empresas/{empresa}/facturacion/{tipo}`;
`tipo` es `CLIENTE` o `PROVEEDOR` en mayúsculas. Los ID de gestión se distinguen
del `facturaId` económico, que solo aparece al emitir. Rutas incorporadas en
OpenAPI 0.15.0 y conservadas en el contrato vigente 0.16.0.

| Ruta | Métodos | Uso |
|---|---|---|
| `B/series` | GET, POST | Consultar y crear series |
| `B/documentos` | GET, POST | Consultar y preparar borradores |
| `B/documentos/{id}` | GET | Detalle, líneas e identidad conservada |
| `B/documentos/{id}/emision` | PUT sin cuerpo | Emitir o registrar una factura |
| `B/documentos/{id}/anulacion` | PUT | Anular borrador con motivo |
| `B/documentos/{id}/rectificacion` | POST | Preparar rectificación total |

Listados paginados (`page` 0–100000, `size` 1–100, defecto 20), por ID ascendente.
Los listados no incluyen textos de motivos ni identidades fiscales. Importes
de salida en cadenas de dos decimales, conservando el contrato económico previo.

V8 añade dos entidades JPA y tres tablas: preparación, líneas calculadas y
auditoría transaccional de creación de series/documentos, emisión, anulación
y rectificación. Las claves foráneas y unicidades acotan empresa, serie,
tercero, obra y original; la coherencia obra-cliente y las transiciones se
validan en servicio. Se retiran cascadas de borrado de líneas económicas y
`orphanRemoval` de sus entidades. No se habilitan escrituras SQL externas:
los futuros importadores deben respetar este servicio y su protocolo de bloqueo.

Resultados y límites de validación en la documentación histórica archivada localmente.
