# Clientes y obras por empresa: bloque 3

Implementado y verificado el 10 de octubre de 2026 sobre datos sintéticos.
Se ofrecen listados y detalles de clientes/obras y colecciones paginadas de
contactos/direcciones. Todas las operaciones son de lectura y requieren JWT.

## Pertenencia explícita

El esquema PostgreSQL incluye
`API_Cliente_Empresa` y `API_Obra_Empresa`. Cada cliente u obra tiene, como máximo,
una empresa propietaria en esta DEMO. Los IDs siguen siendo los locales del
esquema existente; no resuelven colisiones de IDs entre archivos reales del ERP.

| Empresa DEMO | Clientes | Obras |
|---|---|---|
| 1: Dorna Metal | 101 | 1001, 1002 |
| 2: Dorna Solution | 102 | 1003 |
| 3: Anrod Latem | Ninguno asignado | Ninguna asignada |
| 4: Keltor | Ninguno asignado | Ninguna asignada |

La asignación describe exclusivamente los ejemplos. Un recurso sin asignación
no se expone. Una obra cuyo cliente esté asignado a otra empresa queda oculta,
tanto en el listado/conteo como en el detalle y sus contactos. Las claves
foráneas garantizan que existan los recursos; la coherencia obra–cliente–empresa
se exige adicionalmente en los joins de lectura.

Las direcciones y contactos se obtienen por la entidad del cliente autorizado.
La dirección de una obra solo se muestra si pertenece a ese cliente. Su endpoint
de contactos devuelve únicamente contactos activos de ese mismo cliente,
asociados a la obra mediante `Obra_Contacto`. No incluye contactos de proveedores
u otras entidades hasta definir expresamente su pertenencia/autorización.

## Permisos y rutas

Prefijo: `/api/v1/empresas/{empresa}`. Requieren el encabezado
`Authorization: Bearer <accessToken>`.

| Ruta GET | Permiso en la empresa | Respuesta |
|---|---|---|
| `/clientes` | CLIENTES | Página de resúmenes. |
| `/clientes/{id}` | CLIENTES | Ficha de contacto comercial mínima. |
| `/clientes/{id}/contactos` | CLIENTES | Página de contactos activos. |
| `/clientes/{id}/direcciones` | CLIENTES | Página de direcciones. |
| `/obras` | OBRAS | Página de resúmenes. |
| `/obras/{id}` | OBRAS | Detalle, referencia mínima al cliente y dirección coherente. |
| `/obras/{id}/contactos` | OBRAS y CLIENTES | Página de asociaciones con contacto y rol en la obra. |

Los permisos se comprueban mediante roles asignados al usuario en esa empresa,
con usuario, empresa, rol y permiso activos. Los niveles LECTURA, ESCRITURA y
ADMIN permiten leer; ninguna ruta de escritura se habilita en este bloque.
El permiso EMPRESA por sí solo no permite consultar clientes ni obras.
Falta de permiso, recurso ajeno y recurso inexistente devuelven el mismo 404,
sin revelar si el recurso existe. Las credenciales inválidas devuelven 401.

En este bloque, el rol `CONSULTA_RECURSOS_DEMO` incorpora EMPRESA, CLIENTES y OBRAS. La
migración se lo asigna a la cuenta local `demo` en empresa 1; en una base nueva
lo hace el inicializador. Una migración ya aplicada no se repite ni restaura
accesos retirados. Las pruebas crean sus propios usuarios y revierten sus datos.

El detalle de cliente omite fecha de nacimiento, crédito y observaciones.
El detalle de obra devuelve `presupuesto` como cadena decimal exacta, por ejemplo
`"3000.00"`, sin atribuir una moneda que el esquema no proporciona. Las fechas
usan `YYYY-MM-DD`. Los datos ausentes siguen siendo `null`; las colecciones
sin resultados contienen `items: []`.

## Paginación y filtros

Formato estable para todos los listados nuevos:

```json
{
  "items": [{"id": 1001, "codigo": "DEMO-OB-001", "titulo": "Montaje de estructura [DEMO]", "estado": "EN_CURSO", "clienteId": 101}],
  "page": 0,
  "size": 1,
  "totalElements": 2,
  "totalPages": 2,
  "sort": "id,asc"
}
```

- `page`: base cero, entre 0 y 100000; predeterminado 0.
- `size`: entre 1 y 100; predeterminado 20.
- `sort`: un campo y dirección `asc`/`desc`; en la URL se escribe `id,asc`.
  Clientes admiten `id`, `codigo`, `nombre`; obras, `id`, `codigo`, `titulo`;
  contactos/direcciones admiten `id`. Siempre se desempata por ID en la misma
  dirección. El orden predeterminado es `id,asc`.
- `q`: solo clientes/obras, hasta 100 caracteres; busca parcialmente por código
  y nombre/título. Se recortan espacios de extremos. `%`, `_` y `!` se buscan
  literalmente. La comparación usa la colación de MySQL del esquema DEMO.
- `estado`: solo obras, hasta 300 caracteres, descripción exacta del catálogo.
- `cliente`: solo obras, ID entero positivo del cliente.

Filtros sin coincidencias devuelven una página vacía. El total corresponde a
los mismos filtros y empresa que los resultados. Una página posterior al final
conserva el total y devuelve `items: []`. Parámetros desconocidos, repetidos o
fuera de rango devuelven 400 en estos listados. No hay filtro de ejercicio:
debe acordarse con el origen; no se deduce del número o de la fecha de la obra.

Ejemplos:

```text
GET /api/v1/empresas/1/clientes?q=Norte&sort=nombre,asc&page=0&size=20
GET /api/v1/empresas/1/obras?estado=EN_CURSO&cliente=101&sort=codigo,desc
GET /api/v1/empresas/1/obras/1001
GET /api/v1/empresas/1/clientes/101/contactos?page=0&size=20
```

## Errores y OpenAPI

Los errores manejados por la aplicación y los rechazos de seguridad comparten
`status`, `codigo`, `detalle`, `campos` y `requestId`. `campos` contiene nombres
de campos, nunca valores de contraseña. El servidor genera un UUID nuevo por
petición y lo devuelve también en `X-Request-Id`, incluso para errores 401/403.
Los errores de persistencia devuelven un 500 genérico sin SQL ni trazas.

El contrato está en `src/main/resources/static/api/v1/openapi.json` y se obtiene
por `GET /api/v1/openapi.json` con Bearer. En el bloque 3 describía trece rutas de
autenticación, empresas, clientes y obras, con diecinueve esquemas. El bloque 4
lo amplía a veinte rutas y veintiséis esquemas; ver [Presupuestos y documentos](EXPEDIENTES_DEMO.md). Se usa el formato
[OpenAPI 3.1.1](https://spec.openapis.org/oas/v3.1.1.html).
Se comprobaron las referencias internas y los parámetros de ruta; el contrato
es estático y debe actualizarse junto con futuras modificaciones de endpoints.

## Comprobaciones

```powershell
.\scripts\entorno-local.ps1 -Accion probar
# En otra terminal, con la API iniciada mediante -Accion ejecutar:
.\scripts\comprobar-acceso.ps1
```

Resultado: **60 pruebas correctas**, cero fallos, errores u omitidas: 9 de
persistencia, 18 de seguridad, 1 de renovación concurrente y 32 del nuevo
recorrido de consultas. Se verifican detalle, paginación, orden con empates,
filtros, datos nulos, listas vacías, permisos retirados, acceso a dos empresas,
cambio de IDs en URL, relaciones inconsistentes y parámetros malformados.

La prueba de consultas SQL registra cuatro sentencias para el listado de obras
con tamaño de página 1 y 100: sesión vigente, permiso, conteo y datos. No hay
consultas por fila. Es una medición sobre la semilla pequeña, no una prueba de
carga ni una evaluación del rendimiento con datos reales.

El recorrido HTTP real también comprobó login, clientes, obras ordenadas,
contactos, respuestas 404 entre empresas, 400 para tamaño excesivo, OpenAPI,
renovación y logout. Se detuvo la aplicación al terminar.

La integración real del ERP sigue pendiente. El bloque 4 añade lectura de
[presupuestos y archivos DEMO](EXPEDIENTES_DEMO.md). Los bloques posteriores
añaden [operarios](OPERARIOS_DEMO.md) y [economía](ECONOMIA_DEMO.md). Los demás
recursos conservan sus rutas denegadas hasta sus bloques respectivos.
Ver la documentación histórica archivada localmente.
