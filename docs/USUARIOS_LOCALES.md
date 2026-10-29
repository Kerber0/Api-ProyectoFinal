# Usuarios y permisos iniciales

Desde la etapa 1, la provisión utiliza PostgreSQL en `127.0.0.1:15432` y conserva
las claves cifradas existentes. Véase [ejecución vigente](POSTGRESQL.md).
La matriz siguiente se mantiene; las referencias a migraciones MySQL son históricas.

Configuración acordada el 12 de octubre de 2026 para la base local `dorna_dev`.

| Usuario | Empresas | Permisos |
|---|---|---|
| `ADMIN` | Todas las activas | Todos los permisos actuales en nivel ADMIN: lectura y escritura |
| `SOCIO` | Todas las activas | Solo lectura |
| `ADMIN-DORNA` | 1: Dorna Metal | Solo lectura |
| `ADMIN-SOLUTION` | 2: Dorna Solution | Solo lectura |
| `ADMIN-ANROD` | 3: Anrod Latem | Solo lectura |
| `ADMIN-KELTOR` | 4: Keltor | Solo lectura |

El nombre `ADMIN-<EMPRESA>` no concede privilegios: estas cuatro cuentas reciben
el rol `LECTURA_EMPRESA` y una asignación explícita a su empresa. `ADMIN` y
`SOCIO` reciben los roles globales del mismo nombre mediante `Usuario_Rol`.
Solo los roles registrados en `API_Roles_Globales` tienen ámbito global.
`SOCIO_ADMIN`, preparado anteriormente, queda en lectura y sin ámbito global;
no se crea una cuenta con ese nombre.

La auditoría transversal de etapa 10 está reservada a ADMIN global mediante
`AUDITORIA=ADMIN`; los lectores no reciben ese permiso. Se mantienen también
las restricciones de privacidad laboral y documental de sus módulos.
Véase [Auditoría transversal](AUDITORIA.md).

La API publica consultas de negocio y las escrituras concretas de visibilidad
de maestros y gestión central de usuarios. ADMIN puede administrar cuentas y
asignaciones; SOCIO solo consultar. Las cuatro cuentas de empresa no obtienen
gestión de identidad. Véase [Usuarios y permisos](USUARIOS_Y_PERMISOS.md).
Login, refresh y logout siguen disponibles para todos los usuarios activos.
La provisión siguiente converge expresamente estas seis cuentas a la matriz
inicial y revoca sesiones si cambia asignaciones; no sustituye la gestión HTTP.

## Crear o comprobar las cuentas

Desde la raíz del proyecto:

```powershell
.\scripts\entorno-local.ps1 -Accion verificar
.\scripts\crear-usuarios-locales.ps1
```

El segundo script verifica la instancia MySQL local y sus migraciones, genera
claves aleatorias individuales de 32 bytes y ejecuta el JAR con el perfil
`provisionar`. El proceso escucha temporalmente en un puerto aleatorio de
loopback y se cierra tras terminar. No es un endpoint de administración y no
se ejecuta al iniciar normalmente la API.

Las claves se conservan cifradas mediante DPAPI de Windows en
`.local/usuarios/<USUARIO>.credential.xml`, dentro del directorio privado
excluido de Git. MySQL almacena únicamente hashes BCrypt con coste 12. No se
imprimen claves ni tokens en los registros.

Para consultar la clave de una cuenta en tu propia terminal:

```powershell
$cuenta = Import-Clixml .\.local\usuarios\ADMIN.credential.xml
$cuenta.GetNetworkCredential().Password
$cuenta = $null
```

Sustituí `ADMIN` por el usuario deseado. Esos archivos solo se pueden descifrar
con el usuario Windows que los creó; no son credenciales portables.

Repetir la provisión conserva IDs, hashes y claves. Si la asignación de alguna
de estas seis cuentas difiere de la tabla, la corrige y revoca sus sesiones.
Si no hay cambios, conserva las sesiones. No modifica otras cuentas, no
reactiva usuarios deshabilitados y no reemplaza una clave existente que no
coincida con la credencial local: en ese caso cancela la transacción completa.

`dorna_test` recibe la política de roles, pero las seis cuentas solo existen
dentro de pruebas con rollback. No se copian credenciales de desarrollo a las
pruebas o a CI. La cuenta `demo` anterior mantiene su acceso limitado.

## Verificar

`UsuariosInicialesTests` comprueba lectura/escritura, separación por empresa,
revocación inmediata, usuarios inactivos, claves, repetición y retirada de
privilegios sobrantes. La suite general sigue cubriendo los recursos y sesiones.

Después de crear las cuentas y con la cuenta `demo` preparada para el ensayo:

```powershell
.\scripts\ensayar-despliegue.ps1 -UsuariosIniciales
```

Verifica login, empresas autorizadas, accesos ajenos y logout de las seis cuentas
por HTTPS con certificado validado, además del recorrido general. El proceso
del ensayo se detiene al terminar. Resultados en la documentación histórica archivada localmente.
