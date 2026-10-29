# Entorno local

## Requisitos

- Java 21;
- PostgreSQL 18;
- Maven Wrapper incluido en el proyecto.

## Base de datos

Preparar una base nueva siguiendo [POSTGRESQL.md](POSTGRESQL.md). No utilice el script de creación sobre una base que ya contenga datos.

Los datos ficticios son opcionales. Las cuentas de demostración se provisionan después y sus contraseñas se reciben únicamente mediante variables locales.

## Variables de desarrollo

El perfil `dev` requiere:

- `DORNA_DEV_DB_URL`;
- `DORNA_DEV_DB_USER`;
- `DORNA_DEV_DB_PASSWORD`;
- `DORNA_JWT_SECRET`;
- `DORNA_DEMO_PASSWORD` solo si se necesitan cuentas de demostración.

La URL debe usar PostgreSQL local. Los secretos no se guardan en archivos versionados.

## Ejecución

```powershell
.\scripts\entorno-local.ps1 comprobar
.\scripts\entorno-local.ps1 ejecutar
```

El primer comando valida la configuración sin abrir la aplicación. El segundo inicia Spring Boot con el perfil `dev`. Ninguno crea ni modifica el esquema.

## Pruebas

Con Docker disponible:

```powershell
.\mvnw.cmd -B test
```

Testcontainers crea una base aislada. Para usar un clúster local desechable deben configurarse `DORNA_TEST_DB_URL`, `DORNA_TEST_DB_USER`, `DORNA_TEST_DB_PASSWORD`, `DORNA_TEST_DB_ADMIN_USER`, `DORNA_TEST_DB_ADMIN_PASSWORD` y `DORNA_TEST_DB_DISPOSABLE=true`.
