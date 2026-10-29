# Preparación de despliegue: bloque 8

> Antecedente del despliegue MySQL. Para el backend PostgreSQL consultar
> [la guía vigente](POSTGRESQL.md): la estructura se instala antes del despliegue y
> el servidor recibe únicamente la cuenta DML. No ejecutar los comandos de
> copia/restauración MySQL siguientes sobre el nuevo entorno.

El proyecto dispone de un JAR ejecutable con Java 21 y un perfil `despliegue`
para una instancia propia del esquema DEMO ya migrado. Se verifican HTTPS,
límites, observabilidad local, recuperación y contrato JSON. No se ha publicado
la API ni conectado un ERP real. El servidor de destino no está definido.

## Construir y arrancar

Las pruebas necesitan `dorna_test` preparada y sus variables de entorno, según
[Entorno local](ENTORNO_LOCAL.md). El flujo reproducible de construcción es:

```text
./mvnw -B verify
```

En Windows, `scripts/entorno-local.ps1 -Accion verificar` prepara el entorno,
ejecuta la suite, construye el JAR y genera el inventario JSON de dependencias.
Así, pruebas y empaquetado se realizan en una misma ejecución de Maven.
El resultado es `target/api-0.0.1-SNAPSHOT.jar`, sin DevTools en su interior.
La versión comercial del producto sigue pendiente; el contrato es `0.9.4`.

Configurar estas variables mediante el gestor de servicios o secretos del
servidor, sin poner sus valores en argumentos, Git ni logs:

| Variable | Uso |
|---|---|
| `DORNA_DB_URL` | URL JDBC de la base propia preparada; para MySQL remoto, TLS con `sslMode=VERIFY_IDENTITY` y CA de confianza. |
| `DORNA_DB_USER`, `DORNA_DB_PASSWORD` | Cuenta de ejecución con permisos mínimos. |
| `DORNA_JWT_SECRET` | 32 bytes aleatorios representados con 64 caracteres hexadecimales; propia del entorno. |
| `DORNA_TLS_KEYSTORE` | Ruta externa o URL `file:` al PKCS12 con certificado, cadena y clave privada. |
| `DORNA_TLS_PASSWORD` | Contraseña del PKCS12. |
| `DORNA_BIND_ADDRESS` | Interfaz de escucha; por defecto `127.0.0.1`. |
| `DORNA_HTTPS_PORT` | Puerto HTTPS; por defecto `8443`. |
| `DORNA_MANAGEMENT_PORT` | Puerto interno de gestión; por defecto `9091`. |

```text
java -jar target/api-0.0.1-SNAPSHOT.jar --spring.profiles.active=despliegue
```

No combinar con `dev` o `test`. El perfil no ejecuta el inicializador de usuarios
DEMO ni crea tablas. El esquema y las pertenencias deben estar preparados;
Hibernate los valida. La provisión de identidades reales pertenece a la
integración pendiente. La cuenta de ejecución no necesita DDL: lectura de las
tablas consultadas y lectura/inserción/actualización de `API_Sesiones` son las
operaciones actuales. Las cuentas locales de desarrollo y CI tienen más
permisos para sus pruebas y no deben copiarse a producción.

El perfil usa TLS 1.2/1.3 y no abre un conector HTTP para la API. El certificado
del servidor final debe ser válido para su nombre y estar reconocido por los
clientes. El ensayo local genera un certificado de dos días y confía solo en su
certificado público dentro del proceso de prueba, sin instalarlo en Windows ni
desactivar la validación TLS.

Se ignoran `Forwarded` y `X-Forwarded-*`; esta configuración utiliza TLS directo
y la IP de conexión. Un proxy requiere un diseño posterior de red y confianza.
El guard de arranque rechaza HTTPS o límites desactivados, perfiles mezclados,
cabeceras reenviadas habilitadas o gestión fuera de loopback/en el mismo puerto.
Las opciones TLS proceden de la [documentación de Spring Boot](https://docs.spring.io/spring-boot/how-to/webserver.html).

## Límites y errores

| Recurso | Valor inicial |
|---|---|
| Solicitudes por IP real | 300 por ventana de 60 segundos. |
| Escrituras por IP real | 20 por ventana de 60 segundos; incluye login, refresh y logout. |
| Clientes activos en el limitador | 10000; al llenarse, nuevas IP reciben 429. |
| Cuerpo HTTP | 16384 bytes, incluidos cuerpos chunked/sin Content-Length. |
| Cabeceras HTTP | 16 KB en Tomcat. |
| Conexiones / hilos / cola Tomcat | 200 / 40 / 50. |
| Timeout conexión / keep-alive | 10 / 15 segundos. |
| Pool JDBC / espera de conexión | 10 conexiones / 5 segundos. |
| Consulta JDBC/JPA | 15 segundos. |

Los límites de aplicación se configuran mediante `dorna.limits.*`. No confían
en cabeceras de IP y son atómicos ante solicitudes concurrentes. Las ventanas
caducan y el mapa tiene tamaño acotado. Se conservan contadores en memoria por
proceso: reinicios los reinician, réplicas no los comparten, y clientes detrás
de una misma NAT comparten cupo. Es necesario ajustar los valores con tráfico
del destino; esto no sustituye protección perimetral ni un limitador distribuido.

413 (`CUERPO_DEMASIADO_GRANDE`) y 429 (`LIMITE_SOLICITUDES`) usan el contrato
`Error`, `X-Request-Id` y `Cache-Control: no-store`. 429 incluye `Retry-After` en
segundos. Pueden ocurrir antes de autenticar. Los límites propios del conector
(por ejemplo, una cabecera demasiado grande) pueden producir una respuesta
genérica de Tomcat y no están garantizados como JSON de la aplicación.
Se deshabilita multipart: esta API no ofrece subidas de archivos.

## Salud, métricas y registros

La gestión escucha únicamente en `127.0.0.1:9091`, por HTTP local. Solo se
exponen operaciones de lectura de health y metrics:

```text
GET http://127.0.0.1:9091/actuator/health/liveness
GET http://127.0.0.1:9091/actuator/health/readiness
GET http://127.0.0.1:9091/actuator/metrics
GET http://127.0.0.1:9091/actuator/metrics/http.server.requests
```

Liveness comprueba la disponibilidad del proceso; readiness añade la base de
datos. Las respuestas de salud solo muestran `status`. Las métricas no están
accesibles por el puerto HTTPS de negocio; `env`, `configprops`, `heapdump` y
otros endpoints no se exponen. El acceso de gestión exige además origen
`127.0.0.1`, puerto interno y método GET. Configuración basada en
[Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/monitoring.html).

Cada solicitud registra únicamente `requestId`, estado y duración. No se
registran URL, parámetros, cuerpos, cabeceras de autorización ni IP personales.
La correlación se realiza con el `X-Request-Id` generado por el servidor.
El logging SQL y de parámetros JDBC está deshabilitado. El gestor del servicio
debe recoger stdout/stderr, limitar tamaño y retención y restringir su lectura.

Configurar supervisión de readiness, tasa 5xx/429, latencia, saturación del pool,
memoria y caducidad del certificado. El perfil habilita cierre ordenado con
30 segundos de gracia; el gestor debe enviar SIGTERM y esperar ese intervalo
en Linux. El ensayo de Windows detiene su proceso Java al terminar y no acredita
el cierre ordenado bajo el gestor de servicios del destino.

## Copias y recuperación

```powershell
# Con MySQL local encendido y las aplicaciones de comprobación detenidas:
.\scripts\ensayar-recuperacion.ps1
```

El script verifica la instancia privada y la marca DEMO de `dorna_dev`, crea
un volcado consistente con `mysqldump --single-transaction`, copia el JAR y
escribe hashes SHA-256. La carpeta queda bajo `.local/`, protegida por la ACL
del entorno local y excluida de Git. **Contiene hashes de contraseñas y sesiones;
es una copia privada, no un artefacto de CI.**

Se crea una base nueva `dorna_restore_<id>` y se restaura allí, sin borrar ni
sobrescribir bases existentes. Las referencias de las vistas al esquema origen
se trasladan al nuevo esquema. Se comparan conteos y checksums extendidos de
todas las tablas, se comprueban las vistas y se revocan las sesiones restauradas.
El manifiesto conserva el resultado y la duración. La base de ensayo y su
copia se conservan para revisión; no se eliminan automáticamente.

El script es un ensayo del volumen DEMO de Windows y carga el volcado en memoria.
Para el servidor final, definir copias cifradas fuera de la máquina, retención,
RPO/RTO, responsable y ensayo periódico de restauración. Mantener por separado
el historial de migraciones, artefacto, documentos y recuperación de secretos.
En esta DEMO los documentos son fixtures privados incluidos en el JAR. No se
ejecutan migraciones/DDL durante la copia; `--single-transaction` presupone
tablas transaccionales y no garantiza consistencia frente a cambios de esquema,
como describe [MySQL](https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html).

Una recuperación real debe validar primero una base nueva, revocar sesiones y
rotar la clave JWT antes de cambiar la conexión del servicio. Conservar el JAR
anterior para revertir la aplicación; no ejecutar migraciones inversas sin un
plan probado de datos. No restaurar encima del ERP.

## CI y dependencias

`.github/workflows/ci.yml` prepara Java 21 y MySQL 8.0.44 aislado, genera claves
efímeras, aplica esquema/semilla/migraciones en una base nueva y ejecuta Maven
`verify`. Usa `lower_case_table_names=1` al inicializar MySQL para reproducir las
reglas de nombres del entorno Windows. No cambiar esa opción sobre un directorio
de datos ya creado. El script `preparar-ci.mjs` se detiene si la base existe.

El flujo construye el JAR e inventario de dependencias dentro del runner y no
publica ni sube artefactos. Tiene permisos GitHub de solo lectura y timeout.
Dependabot propone actualizaciones semanales de Maven y Actions. El inventario
local se genera con `mvnw.cmd -B dependency:tree -DoutputType=json -DoutputFile=target/dependencias.json`.
El workflow incluye las pruebas del auditor y la consulta de avisos OSV. Véase
[Dependencias](DEPENDENCIAS.md) para correcciones, comandos y alcance.
La revisión debe repetirse antes del despliegue final y completarse con la
plataforma del servidor.
El uso de contenedores de CI sigue las [condiciones de GitHub Actions](https://docs.github.com/en/actions/tutorials/use-containerized-services/use-docker-service-containers).

Se comprueba localmente la creación de una base nueva con el script de CI:

```powershell
.\scripts\ensayar-preparacion-ci.ps1
```

El ensayo verifica el directorio real de la instancia, crea una base aleatoria,
comprueba las once migraciones con sus hashes y los ejemplos recientes, rechaza
una instancia distinta y una base existente, y elimina su cuenta temporal.
La base se conserva para inspección y el resumen queda en
`.local/preparacion-ci-final.json`. No modifica `dorna_dev` ni `dorna_test`.
El workflow no se ha ejecutado en GitHub: requiere que el repositorio y Actions
estén configurados. No hay Docker disponible en esta estación, por lo que la
rama Docker y la ejecución Linux quedan pendientes del primer run de CI.

## Contrato móvil y verificaciones

OpenAPI `0.10.0` incluye calendario, tareas y albaranes con factura opcional, y documenta 413/429 en todas
las operaciones. Android debe conservar importes como cadenas/BigDecimal,
fechas como LocalDate, horas locales de eventos como LocalDateTime sin asignarles
una zona y valores nulos; respetar `Retry-After`, evitar
refresh simultáneos y eliminar credenciales tras cierre o revocación. Debe
usar confianza TLS del sistema y almacenamiento seguro de tokens. No hay
proyecto Android en este repositorio: su compilación y prueba en dispositivo
contra el certificado del destino quedan pendientes.

```powershell
# JAR construido, cuenta DEMO existente y MySQL local preparado:
.\scripts\ensayar-despliegue.ps1
```

El ensayo arranca el JAR con el perfil `despliegue`, comprueba la cadena TLS y
el nombre localhost, salud y métricas privadas, contrato JSON, informes,
paginación, stock, compras, albaranes, incidencias, calendario y tareas,
aislamiento entre empresas, refresh/logout, cuerpos con longitud
conocida y chunked, y límites 413/429. Detiene su propio proceso al terminar.
El certificado temporal y los logs quedan en `.local/despliegue-<id>/`.

Verificación histórica del bloque 8, anterior a las ampliaciones del bloque 9,
del 11 de octubre de 2026: suite de **247 pruebas correctas** y
posterior ampliación del contrato a **5 pruebas OpenAPI correctas** (248 pruebas
en total entre las suites vigentes), cero fallos/errores/omitidas. JAR construido
y ensayo HTTPS correcto. Carga local: 100 consultas con concurrencia 4,
p95 7.1 ms y máximo 38.3 ms en la última ejecución; es una muestra funcional,
no capacidad garantizada.
Recuperación: **65 tablas y 9 vistas**, 15.41 segundos en la última ejecución
en esta estación, con copia del JAR final.

Verificación del 12 de octubre de 2026, tras incorporar calendario y tareas:
**515 pruebas correctas**, cero fallos/errores/omitidas, JAR actualizado y ensayo
HTTPS correcto con los módulos del bloque 9. Carga local: 100 consultas con
concurrencia 4, p95 de 7,5 ms y máximo de 23,3 ms. Se verificaron también salud
privada, certificado, permisos, sesiones y límites 413/429. La aplicación se
detuvo al terminar. Este cierre no repitió el ensayo histórico de recuperación.
Registros en `.local/pruebas-calendario-cierre.log`,
`.local/empaquetado-calendario-cierre.log` y `.local/https-calendario-cierre.log`.

La preparación verificable del repositorio está terminada. El cierre operativo
requiere servidor/dominio/certificado, permisos de base del destino, red y
supervisión, copia externa y objetivos de recuperación, primer run de CI,
revisión de la plataforma del destino y validación Android. La integración real sigue
condicionada al bloque 0. Ver la documentación histórica archivada localmente.

El cierre local posterior del 12 de octubre actualizó las dependencias con
avisos, pasó las 515 pruebas Java y 6 del auditor, y volvió a verificar el JAR por
HTTPS. OSV devolvió cero avisos conocidos en 138 paquetes. Se verificó la
preparación desde cero con 11 migraciones, 74 tablas y 9 vistas y se repitió la
restauración completa de 74 tablas y 9 vistas en 17,43 segundos, conservando el
origen y comprobando que no quedaban sesiones restauradas activas. Evidencias:
`.local/verificacion-dependencias-corregidas.log`,
`.local/https-dependencias-corregidas.log`, `.local/preparacion-ci-final.json` y
`.local/recuperacion-final.log`. GitHub/Actions siguen aplazados por indicación
del usuario; los pendientes externos descritos en el README permanecen abiertos.

La continuación del 12 de octubre cerró el cambio de albaranes a facturas con
contrato `0.10.0`, 524 pruebas correctas y JAR comprobado por HTTPS. Preparación
nueva: 14 migraciones, 75 tablas y 9 vistas. Restauración: 75 tablas y 9 vistas
con conteos/checksums coincidentes, sesiones revocadas y origen conservado,
en 18,73 segundos. Evidencias en `.local/continuacion-verificacion.log`,
`.local/continuacion-https.log`, `.local/continuacion-preparacion-ci.log` y
`.local/continuacion-recuperacion.log`. La aplicación del ensayo quedó detenida.

El cierre conjunto posterior mediante `scripts/verificar-entrega.ps1` pasó las
538 pruebas en una sola ejecución y verificó por HTTPS las seis cuentas y el
recorrido general. Preparación y restauración actualizadas a 15 migraciones,
75 tablas y 9 vistas, incluida la política de usuarios; recuperación en 18,51
segundos. Resumen y logs en
`.local/entrega-c45db64d1c324b2da3fbd9b8d98cbce8/`. El proceso HTTPS se detuvo.

El cierre local vigente del 24 de octubre ejecutó 917 pruebas en 45 suites y
verificó el contrato `0.20.5-candidato`, HTTPS con las seis cuentas, una base
nueva con 18 migraciones, 94 tablas y 9 vistas, y la recuperación completa en
26,96 segundos. El workflow comprueba además que la copia documental de OpenAPI
y el inventario de DTO sean reproducibles antes de ejecutar Maven. Evidencia en
`.local/entrega-7ba5a20b03354ec595a9d40c7ef326f1/`. La primera ejecución real
de GitHub Actions y la validación Docker/Linux siguen pendientes de publicación.
