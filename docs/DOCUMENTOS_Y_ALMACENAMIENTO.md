# Documentos y almacenamiento — etapa 9

Los nuevos archivos son privados, inmutables y pertenecen a una empresa. Se
asocian al subirlos a una obra, presupuesto o ausencia de esa empresa. La base
guarda nombre, MIME, tamaño, SHA-256, proveedor, clave opaca, autor y fecha UTC;
los bytes quedan fuera de PostgreSQL. No se aceptan rutas, URL, autor ni fechas
del cliente. Cada archivo tiene una asociación exclusiva, que no se puede
trasladar a otra empresa o recurso.

V10 añade `API_Documentos` y `API_Documentos_Auditoria`, con una entidad JPA.
Las claves foráneas compuestas aseguran la empresa de cada asociación; una
restricción exige exactamente un destino. No se inventan archivos ni
asociaciones para datos históricos. Los contratos anteriores de
`/obras/{obra}/documentos` y sus descargas classpath se conservan. El nuevo
catálogo se consulta en las rutas indicadas abajo; sus identificadores son
independientes. Las entidades históricas exigen obra o carecen de propiedad y
metadatos suficientes para representar justificantes privados, por lo que no
se usan como almacén general ni se interpretan sus rutas libres.

## Permisos

Las rutas generales exigen EMPRESA y DOCUMENTOS, además de OBRAS,
PRESUPUESTOS u OPERARIOS, según el recurso. Subir requiere escritura en los
dos permisos específicos. SOCIO y las cuentas lectoras pueden consultar los
archivos de obras y presupuestos autorizados, sin subirlos. El trabajador no
recibe acceso al catálogo general por estar asignado a una obra.

Los justificantes exigen además USUARIOS ADMIN global en las rutas generales,
igual que la gestión de ausencias. El titular tiene consulta y descarga por
`mi-trabajo`, con TRABAJO_PROPIO, vínculo vigente, empresa y empleado activos
y alta laboral vigente. No se concede lectura a compañeros ni a lectores
de OPERARIOS. Administración incorpora los justificantes; esta etapa no abre
solicitudes ni subidas del trabajador. Cancelar una ausencia conserva sus
archivos consultables y bloquea nuevas incorporaciones. Retirar el vínculo,
los permisos o la vigencia laboral retira la consulta propia.

## Contrato HTTP

OpenAPI **0.17.0** añade siete operaciones, **146 en total**. Sea
`B=/api/v1/empresas/{empresa}` y `tipo=obras|presupuestos|ausencias`:

| Ruta | Método | Resultado |
|---|---|---|
| `B/documentos/{tipo}/{referencia}` | GET | Página de archivos asociados |
| `B/documentos/{tipo}/{referencia}?nombre=informe.pdf` | POST | Subida; 201 con metadatos |
| `B/documentos/{tipo}/{referencia}/{id}` | GET | Metadatos privados |
| `B/documentos/{tipo}/{referencia}/{id}/contenido` | GET | Descarga adjunta |
| `B/mi-trabajo/ausencias/{ausencia}/justificantes` | GET | Página propia |
| `B/mi-trabajo/ausencias/{ausencia}/justificantes/{id}` | GET | Metadatos propios |
| `B/mi-trabajo/ausencias/{ausencia}/justificantes/{id}/contenido` | GET | Descarga propia |

Las páginas usan `page` 0–100000 y `size` 1–100 (defecto 20), orden por ID
ascendente. Rechazan parámetros desconocidos o repetidos. Subir acepta solo
el parámetro `nombre`. La asociación se valida antes de acceder al archivo.
404 oculta falta de permisos, recurso ajeno o inexistente. No se exponen
proveedor, clave, rutas físicas, URL públicas ni datos de la ausencia.

El POST contiene **los bytes originales**, con `Content-Type` PDF, PNG o JPEG;
no JSON, base64 ni multipart. Esto permite subir desde Android o escritorio
sin habilitar el procesador multipart. El límite es **5 MiB (5242880 bytes)**,
con y sin Content-Length. Solo esas rutas y tipos reciben el límite ampliado;
el resto mantiene 16 KiB y los cupos por IP existentes. No hay escritura por
PUT, reemplazo ni eliminación HTTP. Repetir POST crea un archivo independiente:
el cliente debe comprobar el listado ante una respuesta perdida.

La extensión debe corresponder al MIME. Se rechazan archivos vacíos, nombres
con rutas, controles, caracteres de formato, espacios extremos o más de 200
unidades UTF-16. PDF comprueba cabecera de versión y marcador final; PNG/JPEG
se decodifican y se acotan a 16 millones de píxeles. La comprobación PDF no
valida toda su estructura ni excluye JavaScript, adjuntos o contenido activo.
No hay antivirus, desarmado de documentos ni promesa de archivos inocuos.

Las descargas verifican tamaño y SHA-256, usan `attachment`, `no-store`,
`nosniff` y CSP `sandbox`. No se incrustan en HTML. Un archivo ausente devuelve
404; corrupción o fallo del almacén, 503. Un exceso devuelve 413; MIME no
admitido, 415; otras entradas inválidas, 400, con el error común. Los registros
HTTP no contienen nombres, parámetros ni cuerpos.

## Almacenamiento, transacciones y recuperación

`ArchivoStore` permite registrar adaptadores; `DocumentoService` selecciona
el proveedor configurado para nuevas escrituras y usa el proveedor persistido
para lecturas. Se entrega el adaptador `local`; S3 y otros servicios requieren
su implementación. Cambiar la configuración no mueve archivos existentes.

`DORNA_DOCUMENTOS_RAIZ` fija el directorio privado. En desarrollo se usa
`.local/documentos`; el perfil de despliegue exige configuración explícita.
Las pruebas usan raíces temporales independientes. El directorio debe ser
accesible exclusivamente por la cuenta de servicio y el operador de copias,
fuera de la raíz pública de cualquier servidor web. El adaptador rechaza
enlaces/junctions en su árbol, abre archivos sin seguir enlaces y escribe con
creación exclusiva. Los nombres físicos son UUID del servidor. No sirve
contenido directamente por rutas del sistema operativo.

La subida valida permisos, contenido y destino, toma el bloqueo compartido
de administración y revalida permisos. Publica bytes completos antes de
confirmar metadatos y auditoría en una transacción. Un rollback elimina la
clave recién creada; fallos de escritura no publican metadatos. Se comprueban
subidas concurrentes y revocación durante la espera. Una caída del proceso
entre escritura y commit, un resultado de commit desconocido o un fallo al
limpiar pueden dejar un objeto huérfano. No es accesible por HTTP sin
metadatos autorizados. La limpieza de huérfanos requiere contraste operativo
con la base, con escrituras detenidas; no se borra automáticamente un objeto
ante incertidumbre del commit. No existe transacción distribuida disco/SQL.

`ensayar-recuperacion.ps1` exige detener la API, guarda dump y JAR, copia
solo las claves referenciadas y contrasta tamaño/hash. Restaura la base y
otra copia de los archivos; registra el inventario y la raíz restaurada en
el manifiesto privado. Para arrancar la copia se deben configurar **ambas**:
base restaurada y `DORNA_DOCUMENTOS_RAIZ=.../archivos-restaurados`. Los fixtures
históricos classpath se conservan en el JAR. El ensayo falla si falta un
archivo, está alterado o utiliza un proveedor no soportado por ese script.

`scripts/ensayar-archivos.ps1` comprueba una copia y restauración con contenido,
la lectura de un catálogo JSON vacío,
y los rechazos de destino existente, ruta inválida, proveedor desconocido,
integridad incorrecta y archivo ausente. Conserva evidencia en `.local/`.
Las políticas de conservación, borrado administrativo, cifrado del volumen y
copias externas se concretarán para el entorno de producción.

Resultados de compilación, pruebas y cierre en
la documentación histórica archivada localmente.
