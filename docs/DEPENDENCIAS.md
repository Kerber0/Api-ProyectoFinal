# Dependencias y revisión de seguridad

Actualización vigente del 24 de octubre de 2026: inventario PostgreSQL de **157
paquetes**, consulta OSV sin paquetes con avisos conocidos. Se incorporan
PostgreSQL JDBC **42.7.13** y Testcontainers **2.0.5**, gestionados
por el BOM existente. Evidencias: `target/dependencias.json`,
`target/auditoria-dependencias.json` y `.local/etapa20-auditoria-dependencias.log`.
Las seis pruebas del auditor también pasan. El resultado no cubre el servidor
PostgreSQL, JDK, sistema operativo ni plugins de construcción. Lo que sigue
describe la revisión histórica anterior a esta migración.

Revisión local del 12 de octubre de 2026. No se ha enviado el proyecto a GitHub
ni ejecutado Actions. La consulta a OSV transmite únicamente nombres públicos
de paquetes Maven y versiones, sin fuentes, datos de empresas ni credenciales.

## Correcciones

El inventario inicial contenía 138 paquetes resueltos, incluidas dependencias de
prueba. OSV devolvió 15 identificadores de aviso en cinco paquetes; un aviso no
demuestra por sí solo que la configuración de esta API sea explotable.

| Componente | Anterior | Corrección |
|---|---|---|
| Spring Boot | 4.1.0 | 4.1.1, manteniendo la rama 4.1 |
| Spring Framework | 7.0.8 | 7.0.9, gestionado por Boot |
| Log4j API | 2.25.4 | 2.25.5, gestionado por Boot |
| Tomcat embebido | 11.0.22 | 11.0.26 mediante `tomcat.version` |
| Jackson 3 | 3.1.4 | BOM 3.1.7 mediante `jackson-bom.version` |

Boot 4.1.1 gestiona Tomcat 11.0.24 y Jackson 3.1.5: por eso se conservan dos
propiedades explícitas para incluir correcciones posteriores. Al actualizar
Boot de nuevo, comprobar si su BOM ya permite retirar esas propiedades.

Fuentes contrastadas:

- [Spring: CVE-2026-47884](https://spring.io/security/cve-2026-47884/)
  y [CVE-2026-47890](https://spring.io/security/cve-2026-47890/).
- [Avisos oficiales de Tomcat 11](https://tomcat.apache.org/security-11.html),
  incluidas correcciones de 11.0.26 que no aparecían en el primer resultado OSV.
- [Seguridad de Apache Logging](https://logging.apache.org/security.html).
- [Notas de Jackson 3.1.7](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.1.7).
- [BOM publicado de Boot 4.1.1](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom).

## Repetir la revisión

Con Java 21, PostgreSQL local y Node disponibles (ver [guía vigente](POSTGRESQL.md)):

```powershell
.\scripts\entorno-local.ps1 -Accion verificar
if ($LASTEXITCODE -ne 0) { throw 'Fallo la verificacion Java.' }
node --test scripts/auditar-dependencias.test.mjs
if ($LASTEXITCODE -ne 0) { throw 'Fallo el auditor.' }
node scripts/auditar-dependencias.mjs
if ($LASTEXITCODE -ne 0) { throw 'Revisar el informe de dependencias.' }
```

`verificar` prepara las bases DEMO y ejecuta Maven `verify` e inventario JSON.
La auditoría usa `target/dependencias.json`; hay que regenerarlo tras modificar
el POM. El resultado `target/auditoria-dependencias.json` incluye fecha UTC,
hashes del inventario/POM, ámbitos, paquetes e identificadores de avisos.
El informe inicial se conserva localmente en
`.local/auditoria-dependencias-antes.json`.

Resultado después de corregir versiones: **138 paquetes, cero avisos conocidos**
en OSV (consulta del 12 de octubre de 2026). La suite Java completa pasó con
**515 pruebas** y las **6 pruebas del auditor** también pasaron. El JAR actualizado
se verificó con HTTPS, permisos, sesiones y límites. Registros locales:
`.local/verificacion-dependencias-corregidas.log` y
`.local/https-dependencias-corregidas.log`; informe en
`target/auditoria-dependencias.json`.

Códigos del auditor: 0 sin avisos conocidos; 1 avisos pendientes de revisión;
2 auditoría incompleta por errores de red, inventario o respuesta. Un fallo no
se presenta como informe limpio y sustituye el resultado anterior. Se comprueba
la paginación de la [API de OSV](https://google.github.io/osv.dev/post-v1-querybatch/).
Las seis pruebas del auditor cubren pérdida de resultados, paginación, errores,
dependencias transitivas, ámbitos y lotes.

El workflow local incluye estos pasos; su ejecución remota queda aplazada por
indicación del usuario. No hay subida de artefactos ni publicación automática.

## Límites

Esta revisión es puntual y depende de la cobertura y actualización de OSV.
Un resultado sin avisos no certifica ausencia de vulnerabilidades ni analiza
alcanzabilidad. El inventario incluye librerías de pruebas y desarrollo aunque
no todas se empaqueten en el JAR. No incluye plugins Maven, Actions, JDK, MySQL
Server, Node ni sistema operativo. La revisión y actualización de la plataforma
final, incluida la imagen del servidor, siguen pendientes del destino elegido.
No se instalaron ni actualizaron componentes globales de esta estación.

## Metadatos heredados del POM

Los elementos vacíos de metadatos del POM (como licenses y developers)
impiden heredar los datos del proyecto padre de Spring Boot. Se conservan
para no atribuir a esta API autores, licencias o información SCM del padre.
