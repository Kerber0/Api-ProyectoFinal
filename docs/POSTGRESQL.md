# Base de datos PostgreSQL

## Archivos

La instalación utiliza dos recursos en `src/main/resources/db`:

- `base de datos.sql` crea las tablas, vistas, funciones, índices, restricciones y catálogos necesarios;
- `base de datos de prueba.sql` añade información sintética para demostraciones y pruebas.

El primer archivo no contiene empresas, usuarios ni credenciales. El segundo es opcional, no crea cuentas de acceso y rechaza una segunda carga.

## Instalación

Crear una base vacía con una cuenta administradora y ejecutar:

```powershell
psql -v ON_ERROR_STOP=1 -d dorna_nueva -f "src/main/resources/db/base de datos.sql"
```

Para un entorno de demostración:

```powershell
psql -v ON_ERROR_STOP=1 -d dorna_nueva -f "src/main/resources/db/base de datos de prueba.sql"
```

También puede usarse `scripts/postgresql-local.ps1 crear` con un nombre nuevo y la confirmación expresa. El script rechaza `dorna_dev`, `dorna_test` y las bases internas de PostgreSQL.

## Permisos

La cuenta que instala la estructura debe estar separada de la cuenta de la API. La aplicación necesita conexión, uso del esquema, operaciones sobre las tablas autorizadas y acceso a sus secuencias. No necesita crear tablas ni modificar la estructura.

La marca `dorna_demo_metadata` solo indica que se cargaron los datos sintéticos. La cuenta de la API puede leerla, pero no debe insertarla, modificarla ni borrarla.

## Comprobación

Las pruebas de integración crean una base desechable, instalan ambos SQL y validan entidades JPA, claves, restricciones, permisos, vistas y reglas de negocio. Una URL externa solo se acepta en loopback, con una base llamada `dorna_test` y la confirmación `DORNA_TEST_DB_DISPOSABLE=true`.

La aplicación usa `spring.jpa.hibernate.ddl-auto=validate`: comprueba el esquema al arrancar y nunca lo genera automáticamente.
