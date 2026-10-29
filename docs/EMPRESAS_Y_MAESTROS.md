# Empresas y maestros compartidos — etapa 3

`Empresa` representa `API_Empresas` en JPA. La relación opcional y única con
`Entidad` reutiliza razón social, NIF/CIF y canales de contacto sin copiar datos.
La migración estructural no adivina vínculos fiscales; `datosFiscales` es nulo
hasta que exista una vinculación explícita. Solo los fixtures vinculan las
cuatro identidades ficticias conocidas.

## Propiedad y visibilidad

| Dominio | Relación con empresa | Efecto |
|---|---|---|
| Cliente, proveedor, artículo | `empresasVisibles`, N:M sin cascadas | Un maestro puede consultarse desde varias empresas autorizadas |
| Contactos y direcciones | Pertenecen a la entidad del tercero | Las consultas existentes de cliente heredan su visibilidad; conservan permisos y DTO mínimos |
| Obra, presupuesto, factura, pedido, albarán, documento | `empresa`, única e inmutable mediante JPA | Compartir un maestro no comparte su expediente ni sus importes |
| Operario, almacén, incidencia, evento, tarea | `empresa`, única e inmutable mediante JPA | Conservan su ámbito operativo |
| Líneas, cobros, pagos, partes y movimientos | Ámbito derivado de sus documentos o almacenes | No reciben visibilidad por el mero hecho de compartir un maestro |

Los trece agregados con propiedad explícita usan `@SecondaryTable` sobre las
tablas de asignación existentes: no se duplica físicamente `id_empresa` ni se
reescriben datos históricos. Cada fila de asignación tiene una sola empresa
por PK del recurso y FK obligatoria. El modelo JPA exige empresa en futuras
escrituras; no se afirma que SQL directo impida crear una fila principal sin
asignación. Los recursos sin asignación siguen fuera de las consultas API.
La clave privada del archivo se mapea en `Documento` sin exponerla en DTO.

Se revisaron los joins de consulta, economía, compras, albaranes, expedientes,
laboral, stock, calendario e incidencias. Los joins entre maestro y documento
comparan también la empresa propietaria; los `EXISTS` y agregados mantienen
ese filtro. La PK compuesta evita duplicados dentro de la misma empresa.
No fue necesario reescribir esos joins. Las pruebas comparan listados,
informes y saldos antes y después de compartir, además de los 404 ajenos.

## Operaciones nuevas

Contrato OpenAPI `0.11.0`, aditivo respecto de `0.10.0`:

- `GET /api/v1/empresas/{empresa}/ficha`: permiso de lectura `EMPRESA`;
  devuelve nombre y datos fiscales explícitos, sin observaciones ni auditoría privada.
- `PUT /api/v1/empresas/{empresa}/clientes/{id}/visibilidad/{destino}`.
- `PUT /api/v1/empresas/{empresa}/proveedores/{id}/visibilidad/{destino}`.

Los PUT no reciben cuerpo y devuelven 204. Requieren lectura `EMPRESA` y
escritura `CLIENTES` o `PROVEEDORES` en ambas empresas activas; el recurso debe
ser visible en origen. ADMIN tiene esas facultades según su asignación vigente;
SOCIO y los administradores de empresa actualmente configurados como lectores
no las adquieren por el nombre del rol. La falta de permiso, empresa o recurso
devuelve 404. Repetir una concesión o indicar el mismo origen y destino no
duplica relaciones ni auditoría, y sigue comprobando permisos.

`MaestroService` delimita la transacción y `MaestroRepository` usa JPA.
Un bloqueo pesimista sobre el maestro serializa concesiones simultáneas; la
colección se consulta después de adquirirlo. La nueva relación y el registro
en `API_Visibilidad_Auditoria` se confirman juntos. El registro guarda el usuario
autenticado, origen, destino, tercero e instante. Es trazabilidad de estas
concesiones, no un registro inmutable frente a administradores de base de datos.
No se reciben autor ni fechas del cliente HTTP.

Esta etapa no expone revocación de visibilidad: requiere definir el efecto
sobre documentos existentes. Tampoco publica CRUD fiscal, gestión de permisos,
contactos de proveedor o modificación global de maestros; sus reglas se
resolverán al habilitar esos casos. El artículo ya admite visibilidad N:M en
JPA y SQL; su escritura HTTP no se incorpora aquí.

## Migraciones y verificación

`V4` conserva datos y convierte las tres PK de visibilidad en compuestas,
añade el vínculo fiscal y la auditoría. Los datos de prueba vinculan únicamente
las fichas sintéticas incluidas en el segundo SQL.

El ensayo de preparación usa dos bases nuevas independientes: estructura de
producción sin fixtures, y esquema con demo desde cero. Así no necesita aplicar
El SQL de estructura no carga empresas ni fichas de demostración.

Las pruebas cubren conservación de datos,
unicidad por empresa, propiedad exclusiva, permisos de ambas empresas,
revocación de permisos con token vigente, empresas inactivas, repetición y
concurrencia real, JPA y aislamiento de documentos, informes y stock. La
evidencia de ejecución y las limitaciones operativas están en
la documentación histórica archivada localmente.

Cierre local del 14 de octubre de 2026: 575 pruebas correctas en 21 suites,
JAR con contrato `0.11.0`, ensayo HTTPS con las seis cuentas y restauración
de 76 tablas, nueve vistas y seis migraciones. El ensayo HTTPS verifica los
PUT mediante concesiones ya existentes y respeta `Retry-After` durante el
recorrido funcional; prueba el rechazo 429 de forma separada. Las concesiones
nuevas y simultáneas se comprueban sobre la base de pruebas PostgreSQL.
