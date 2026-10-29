# API Dorna

## 1. Descripción

API REST para la gestión y consulta de la actividad de Grupo Dorna. El proyecto expone los datos que utiliza la aplicación Android y está preparado para otros clientes autorizados.

## 2. Funcionalidades

- autenticación mediante JWT, sesiones y control de permisos;
- separación de datos por empresa;
- consultas de clientes, proveedores, operarios, obras y documentos;
- gestión de usuarios, contactos, direcciones, incidencias, tareas y calendario;
- facturación de clientes y proveedores;
- cobros, pagos, vencimientos y tesorería;
- observaciones de obra y auditoría de operaciones;
- contrato OpenAPI disponible en `src/main/resources/static/api/v1/openapi.json`.

## 3. Tecnologías

- Java 21;
- Spring Boot y Spring Security;
- Spring Data JPA y JDBC;
- PostgreSQL 18;
- Maven;
- JUnit 5 y Testcontainers.

## 4. Organización del proyecto

El código común se divide en modelos, DTO y seguridad. Las áreas funcionales se agrupan en paquetes como `consulta`, `gestion`, `facturacion`, `tesoreria`, `laboral`, `documentos`, `observaciones` y `auditoria`. Cada área contiene sus controladores, servicios y repositorios cuando los necesita.

## 5. Base de datos

La carpeta `src/main/resources/db` contiene dos archivos:

- `base de datos.sql`: estructura completa y catálogos necesarios;
- `base de datos de prueba.sql`: datos ficticios opcionales, sin usuarios ni contraseñas.

Para preparar una base PostgreSQL nueva:

```powershell
psql -v ON_ERROR_STOP=1 -d nombre_base -f "src/main/resources/db/base de datos.sql"
```

Para añadir los datos ficticios:

```powershell
psql -v ON_ERROR_STOP=1 -d nombre_base -f "src/main/resources/db/base de datos de prueba.sql"
```

La aplicación no crea ni modifica el esquema al arrancar. El segundo archivo es opcional y rechaza una carga repetida.

## 6. Configuración y ejecución

El perfil `dev` utiliza `DORNA_DEV_DB_URL`, `DORNA_DEV_DB_USER`, `DORNA_DEV_DB_PASSWORD` y `DORNA_JWT_SECRET`. Ningún secreto se guarda en el repositorio.

```powershell
.\scripts\entorno-local.ps1 comprobar
.\scripts\entorno-local.ps1 ejecutar
```

El perfil `despliegue` añade la configuración TLS, límites y rutas de documentos necesarias para el servidor.

## 7. Pruebas

La suite usa una base PostgreSQL aislada. Cuando Docker está disponible, Testcontainers crea la base y carga los dos SQL automáticamente.

```powershell
.\mvnw.cmd -B test
```

También puede utilizarse una base local desechable en loopback mediante las variables `DORNA_TEST_DB_*`. La infraestructura exige `DORNA_TEST_DB_DISPOSABLE=true` y rechaza otras direcciones o nombres de base.
