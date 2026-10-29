# Autenticación y acceso por empresa en la DEMO

La base PostgreSQL conserva esta política mediante catálogos y permisos; los SQL
MySQL citados abajo son antecedentes. [Configuración vigente](POSTGRESQL.md).

Actualización del 12 de octubre: [usuarios iniciales](USUARIOS_LOCALES.md),
ADMIN con todos los permisos, SOCIO con lectura global y cuentas por empresa
con lectura limitada. La migración `015_usuarios_perfiles.sql` fija esa política;
las claves se crean por provisión local explícita, no en migraciones ni al
arrancar normalmente la API.

Actualización del bloque 3: ya existen consultas autorizadas de clientes,
obras, contactos y direcciones. El contrato y las 60 pruebas de ese bloque se
documentan en [Consultas DEMO](CONSULTAS_DEMO.md). Los resultados de 28 pruebas
de este documento corresponden al cierre anterior del bloque de seguridad.

Actualización del bloque 4: se añaden presupuestos, líneas y descargas privadas
de documentos de obra. Véase [Presupuestos y documentos DEMO](EXPEDIENTES_DEMO.md)
y el resultado de verificación vigente en la documentación histórica archivada localmente.

Actualización del bloque 5: [Operarios y partes históricos](OPERARIOS_DEMO.md)
incorpora OPERARIOS y PARTES_MANO_OBRA. Las consultas de partes requieren también
OBRAS y pertenencia coherente de obra, cliente y operario a la misma empresa.

Actualización del bloque 6: [Economía](ECONOMIA_DEMO.md) distingue permisos
FACTURAS_CLIENTE/FACTURAS_PROVEEDOR y COBROS/PAGOS. Los saldos y vencimientos
requieren tanto el permiso de factura como el de sus movimientos. La referencia
mínima de tercero no expone datos bancarios ni concede acceso a su ficha completa.

Implementación del 10 de octubre de 2026. La API permite iniciar sesión, renovar
tokens, cerrar una sesión y consultar exclusivamente las fichas de empresa
autorizadas. Este bloque utiliza las bases ficticias locales; no integra el ERP
ni establece a qué empresa pertenecen sus clientes, obras o documentos.

## Cómo probarlo

En una terminal PowerShell, desde la raíz:

```powershell
.\scripts\entorno-local.ps1 -Accion ejecutar
```

En otra terminal, ejecutar el recorrido HTTP completo:

```powershell
.\scripts\comprobar-acceso.ps1
```

La comprobación realiza login, consulta de perfil y empresas, rechazo de otra
empresa, renovación y logout. Comprueba que los tokens anteriores ya no sirven
y termina cerrando su sesión. No muestra contraseñas ni tokens.

La primera ejecución del perfil `dev`, si `Usuarios` está vacía, crea `demo` con
una contraseña aleatoria de 64 caracteres, almacenada en
`.local/demo-app.credential.xml` mediante DPAPI de Windows. La base contiene
únicamente su hash BCrypt con coste 12. Se le concede la consulta de la
empresa 1, Dorna Metal [DEMO], y sus clientes/obras mediante el rol
`CONSULTA_RECURSOS_DEMO`. El inicializador exige `dorna_dev` y su marca DEMO;
no se ejecuta en el perfil `test`. Arranques posteriores no reemplazan claves,
reactivan cuentas ni restauran accesos retirados.

Para usar la API directamente desde PowerShell sin imprimir credenciales:

```powershell
$credencialDemo = Import-Clixml .\.local\demo-app.credential.xml
$cuerpoLogin = @{
    usuario = $credencialDemo.UserName
    password = $credencialDemo.GetNetworkCredential().Password
} | ConvertTo-Json -Compress
$sesionDemo = Invoke-RestMethod -Method Post `
    -Uri 'http://127.0.0.1:8080/api/v1/auth/login' `
    -ContentType 'application/json' -Body $cuerpoLogin
$cabecerasDemo = @{ Authorization = "Bearer $($sesionDemo.accessToken)" }
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/v1/empresas' -Headers $cabecerasDemo
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/v1/auth/logout' -Headers $cabecerasDemo
$credencialDemo = $cuerpoLogin = $sesionDemo = $cabecerasDemo = $null
```

## Contrato disponible

Prefijo: `/api/v1`. Los cuerpos de POST son JSON. No se utilizan cookies,
sesiones HTTP, formulario de login ni HTTP Basic. La autenticación de las rutas
protegidas requiere `Authorization: Bearer <accessToken>`.

| Método y ruta | Entrada | Resultado |
|---|---|---|
| `POST /auth/login` | `{"usuario":"demo","password":"..."}` | 200: `accessToken`, `refreshToken`, `tokenType: "Bearer"`, `expiresIn` en segundos. |
| `POST /auth/refresh` | `{"refreshToken":"..."}` | 200: nuevo par de tokens; no requiere access token. |
| `POST /auth/logout` | Bearer vigente, sin cuerpo | 204: revoca la sesión de ese token. |
| `GET /auth/me` | Bearer vigente | 200: `id` y `usuario`; sin hash, contraseña ni datos personales. |
| `GET /empresas` | Bearer vigente | 200: lista de `{id, nombre}` autorizadas, ordenada por ID; puede estar vacía. |
| `GET /empresas/{id}` | Bearer vigente | 200: `{id, nombre}`; 404 si no existe o no es accesible. |

Errores JSON: `codigo` y `detalle`. Datos inválidos producen 400; credenciales,
JWT o sesiones inválidas, 401; operaciones no habilitadas, 403 para un usuario
autenticado. Todas las demás rutas se deniegan por defecto, incluidas futuras
rutas de negocio hasta habilitarlas expresamente. Una petición sin credenciales
a una ruta protegida devuelve 401. No se devuelven SQL, trazas ni valores de
contraseñas en errores de validación.

## Sesiones y autorización

Los access tokens se firman con HS256 y duran como máximo diez minutos.
Se validan firma, algoritmo, emisor `dorna-api`, audiencia `dorna-mobile`,
emisión y caducidad. Además, cada petición consulta la sesión en MySQL y exige
usuario activo, sesión no revocada/no vencida y versión vigente. Los permisos
no se copian al JWT: las consultas comprueban las asignaciones actuales.

La sesión tiene un límite absoluto de siete días. El refresh token incorpora
un identificador de sesión y 32 bytes aleatorios; solo se guarda su hash SHA-256.
La renovación bloquea la fila en una transacción, sustituye el hash e incrementa
la versión. Solo una renovación concurrente puede prosperar. Tanto el refresh
como el access token anteriores dejan de servir; la fecha límite no se amplía.
El cliente debe coordinar la renovación y sustituir ambos tokens juntos.
Reutilizar un refresh consumido devuelve 401; no revoca automáticamente toda la
sesión. El logout invalida ambos tokens de esa sesión para peticiones posteriores,
sin cerrar otras sesiones del mismo usuario. No interrumpe peticiones en curso.

Cada perfil local tiene una clave de firma aleatoria propia, cifrada en
`.local/dorna_dev-signing.credential.xml` o
`.local/dorna_test-signing.credential.xml`. El script la inyecta mediante
`DORNA_JWT_SECRET` (64 dígitos hexadecimales). No existe valor predeterminado
incrustado en el código. Al ejecutar fuera del script deben proporcionarse esta
variable y las variables de conexión. No se implementa rotación de claves con
solapamiento: reemplazar la clave invalida los JWT firmados con la anterior.

| Condición | Acceso a ficha de empresa |
|---|---|
| Usuario, empresa, rol y permiso activos; asignación explícita a esa empresa; permiso `EMPRESA` en nivel `LECTURA`, `ESCRITURA` o `ADMIN` | Permitido. |
| Rol ADMIN o SOCIO asignado en `Usuario_Rol` y registrado en `API_Roles_Globales`, con empresa, usuario, rol y permiso activos | Acceso a todas las empresas activas; solo ADMIN tiene escritura. |
| Rol de `Usuario_Rol` no registrado como global | No concede acceso a empresas sin asignación local. |
| Permiso concedido en otra empresa o mediante otro rol sin asignación a esta empresa | Denegado. |
| Asignación retirada, rol/permiso/empresa desactivados | Deja de aparecer y su detalle devuelve 404. |
| Usuario desactivado o sesión revocada/vencida | 401. |

## Almacenamiento y migraciones

El esquema principal añade `API_Empresas`,
`API_Accesos_Empresa` y `API_Sesiones`. Las cuentas y catálogos de roles/permisos
reutilizan las tablas existentes. No se reutiliza `Entidad` como tenant ni se
asignan recursos antiguos a una empresa por suposición.

El script prepara primero el esquema y la semilla originales, comprueba sus
firmas y aplica las migraciones ordenadas en ambas bases. Registra nombre y
SHA-256 en `dorna_demo_migrations`. Si una migración ya aplicada cambia, aborta;
se debe añadir otra. MySQL no hace rollback de DDL: la primera migración admite
reintento mediante `IF NOT EXISTS` e inserciones que conservan filas existentes.
Esta infraestructura está limitada al entorno DEMO y a un preparador a la vez.

Archivos principales: `security/SecurityConfig.java`, `AuthService.java`,
`TokenService.java`, `AccesoRepository.java`, `AuthController.java`,
`EmpresaController.java`, `SecurityErrors.java` y `DemoUserInitializer.java`.
Los DTO antiguos de creación/actualización de usuarios excluyen la contraseña
de `toString()` y de la serialización JSON.

## Verificación y límites

Resultado de `entorno-local.ps1 -Accion probar`: **BUILD SUCCESS**, 28 pruebas,
cero fallos, cero errores y cero omitidas (10 de octubre de 2026).

La suite incluye las nueve pruebas de persistencia previas, dieciocho pruebas
HTTP/DTO de seguridad y una prueba de renovación concurrente con transacciones
reales de MySQL. Los usuarios de prueba se eliminan por rollback o limpieza
explícita en el caso concurrente. La cuenta de desarrollo no entra en `dorna_test`.

También se ejecutó `comprobar-acceso.ps1` contra Tomcat en `127.0.0.1:8080`:
401 sin credenciales, login correcto, lista limitada a empresa 1, 404 para
empresa 2, renovación correcta, rechazo del JWT anterior y 401 tras logout.
Se detuvo la aplicación de comprobación al terminar; MySQL conserva los datos.

El aislamiento probado se amplió en el bloque 3 a clientes y obras con
pertenencia explícita en la DEMO. El bloque 4 lo amplía a presupuestos, sus
líneas y documentos de obra; la integración de las pertenencias reales del ERP
sigue pendiente. Véase [Presupuestos y documentos DEMO](EXPEDIENTES_DEMO.md).

Quedan para despliegue real la integración de identidades/empresas del ERP,
administración de accesos, recuperación/cambio de contraseña, limitación de
intentos de login, auditoría, limpieza de sesiones caducadas y HTTPS. La DEMO
continúa vinculada a loopback.

La validación JWT se apoya en los componentes de Spring Security, documentados
en [Resource Server JWT](https://docs.spring.io/spring-security/reference/7.1-SNAPSHOT/servlet/oauth2/resource-server/jwt.html)
y [NimbusJwtEncoder](https://docs.spring.io/spring-security/reference/7.0/api/java/org/springframework/security/oauth2/jwt/NimbusJwtEncoder.html).
La revocación y la autorización por empresa descritas arriba son lógica propia
del proyecto, verificadas por las pruebas locales.
