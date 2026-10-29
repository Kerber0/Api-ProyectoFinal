# Gestión desde escritorio — núcleo de la etapa 11

La API incorpora gestión de clientes, proveedores, empleados, obras y
presupuestos bajo `/api/v1/empresas/{empresa}/gestion`. El contrato vigente es
`0.20.5-candidato`, con 193 operaciones totales. La migración V12 añade versiones de
edición, la marca de presupuesto gestionado y el historial de gestión; mantiene
las 63 entidades JPA. No hay aplicación de escritorio en este repositorio.

Este núcleo complementa usuarios, asignaciones, ausencias, observaciones,
facturación, tesorería y documentos de las etapas anteriores. V15 añade la
gestión de contactos y direcciones de clientes y proveedores; V16 añade altas y
sustituciones de incidencias, tareas y eventos. Las consultas Android existentes
conservan sus contratos.

## Operaciones y permisos

Todas las rutas requieren sesión vigente, EMPRESA en lectura y escritura en el
permiso del recurso, también para GET. Las consultas Android conservan sus rutas,
permisos y respuestas anteriores; los listados existentes permiten seleccionar
la ficha que se quiere gestionar. Los errores de ámbito o permisos usan 404.

| Recurso | Permiso | Operaciones relativas a la base de gestión |
|---|---|---|
| Clientes | CLIENTES | POST `/clientes`, GET/PUT `/clientes/{id}`, PUT `/clientes/{id}/actividad` |
| Proveedores | PROVEEDORES | POST `/proveedores`, GET/PUT `/proveedores/{id}`, PUT `/proveedores/{id}/actividad` |
| Empleados | OPERARIOS | POST `/operarios`, GET/PUT `/operarios/{id}`, PUT `/operarios/{id}/actividad` |
| Obras | OBRAS | POST `/obras`, GET/PUT `/obras/{id}`, PUT `/obras/{id}/estado` |
| Presupuestos | PRESUPUESTOS | POST `/presupuestos`, GET/PUT `/presupuestos/{id}`, PUT `/presupuestos/{id}/estado` |
| Series de obra/presupuesto | OBRAS o PRESUPUESTOS | GET/POST `/series/{tipo}`, con `OBRA` o `PRESUPUESTO` |

Crear obra o presupuesto exige también lectura de CLIENTES. Asociar un
presupuesto a una obra requiere lectura de OBRAS y el mismo cliente. Las
referencias deben pertenecer a la empresa indicada. Los maestros compartidos
requieren escritura en **todas** sus empresas visibles, tanto para obtener la
ficha de gestión como para modificarla o desactivarla.

## Edición y conservación

POST exige `version` ausente o null. GET devuelve la versión vigente. PUT recibe
esa versión y todos los campos editables; no funciona como parche. La versión
ausente o negativa devuelve 400 y una versión obsoleta devuelve 409. Cada
modificación confirmada incrementa la versión. La empresa y la autoría proceden
del contexto autorizado, nunca del cuerpo.

Se reutiliza el bloqueo transaccional de administración y se comprueban otra
vez los permisos después de adquirirlo. Así se coordinan edición, concesiones
de visibilidad, cambios de roles, operaciones laborales y facturación. Es un
bloqueo global de escrituras; no se afirma capacidad para una carga de producción.

Clientes y proveedores tienen código e identidad fiscal, con comprobación de
duplicados según la collation PostgreSQL existente. El tipo de identidad no se
cambia. Una identidad usada por otra ficha, empresa o cuenta no se modifica
indirectamente: devuelve 409. El correo se valida antes de persistir utilizando
también la restricción de la entidad, para devolver 400 ante formatos inválidos.

Los clientes permiten descuento entre 0 y 100, límite de crédito, zona y forma
de pago activas. Los proveedores admiten EMPRESA/AUTONOMO; la operación no recibe
ni devuelve el IBAN. Los empleados requieren identidad PERSONA, alta, baja
opcional y categoría activa; no reciben NSS, costes ni horas. Cambiar el periodo
laboral no puede dejar asignaciones, ausencias o partes fuera del periodo.

La baja lógica modifica la actividad de la identidad y conserva la ficha y sus
relaciones. No cancela automáticamente asignaciones, documentos ni movimientos.
Las reglas laborales y económicas vigentes siguen comprobando actividad al
crear operaciones. No se expone DELETE.

Se revisaron las cascadas: estas escrituras no eliminan maestros ni alteran las
colecciones de direcciones/contactos de Entidad. La única eliminación de hijos
expuesta es la sustitución completa de líneas de un presupuesto nuevo pendiente,
mediante la colección JPA con `orphanRemoval`. Los presupuestos históricos y
los aceptados, rechazados o anulados no admiten esa sustitución.

## Series, obras y presupuestos

Las series son propias de empresa, tipo y ejercicio. El nombre y el prefijo
no se repiten dentro de ese ámbito. El prefijo tiene de una a tres letras
mayúsculas, la longitud entre uno y seis dígitos y el ejercicio entre 2000 y 9999.
El contador comienza en uno, avanza con la transacción y devuelve 409 al
agotarse. La consulta usa `page`/`size`, orden por ID y tamaño máximo 100;
rechaza filtros desconocidos o repetidos.

Las obras se numeran como `prefijo + empresa + '-' + ejercicio + '-' + número`,
respetando los 20 caracteres del código. Nacen en PRESUPUESTO. Permiten pasar a
EN_CURSO o CANCELADA; desde EN_CURSO, a FINALIZADA o CANCELADA. Los estados
terminales no se reabren. Cada transición registra historial y auditoría. La
fecha de transición no puede ser futura ni anterior al inicio. Serie y cliente
son inmutables. La fecha de inicio de una obra en curso también lo es.

Los presupuestos se numeran dentro de su serie con prefijo y número. La fecha
debe coincidir con el ejercicio y la validez no puede precederla. Las fechas de
entrada del módulo están limitadas a los años 2000–9999. El servidor calcula
de una a 50 líneas con BigDecimal y HALF_UP por línea, reutilizando el cálculo
de facturación. Cantidades positivas, porcentajes entre 0 y 100 e importes
limitados a la precisión SQL; los decimales se transmiten como cadenas.

Solo los presupuestos creados por gestión llevan `gestionado=true`. Nacen en
PENDIENTE y admiten edición o una transición a ACEPTADO, RECHAZADO o ANULADA.
Serie, cliente y obra son inmutables. No se convierten presupuestos anteriores
en gestionados ni se recalculan importes históricos.

## Auditoría y verificación

Cada escritura inserta en `API_Gestion_Auditoria`; un trigger incorpora el evento
GESTION a `API_Auditoria` dentro de la misma transacción. Un fallo en cualquier
registro de auditoría revierte también los datos, líneas, historial y contador.
La consulta central mantiene su permiso AUDITORIA ADMIN global.

Los cambios guardan versiones y actividad; en obras, estado; en presupuestos,
estado y total; en series, tipo y ejercicio. No guardan nombres, NIF, contacto,
textos libres, datos bancarios ni información salarial. Una actualización fiscal
se acredita por autor, fecha y versión, no por una copia de sus valores privados.

`GestionNucleoTests` comprueba reglas, autorización HTTP, privacidad,
compatibilidad de lectura y validación. `GestionConcurrenteTests` comprueba
ediciones simultáneas, numeración de ambos documentos, revocación mientras se
espera el bloqueo y rollback ante fallo de auditoría. Usa PostgreSQL real con la
cuenta de aplicación sin DDL. Los resultados completos y registros están en
la documentación histórica archivada localmente.

El ensayo HTTPS comprueba lectura privada de gestión y rechazo de escritura
para las cuentas lectoras. La preparación nueva conserva los presupuestos
históricos sin convertirlos. La recuperación incluye la cuarta función de
auditoría y los nueve triggers, además de las tablas, secuencias y archivos.

## Alcance cerrado y límites

El alcance local incluye contactos y direcciones de clientes y proveedores,
contactos de obra, y gestión de incidencias, tareas y eventos. También están
cerrados los informes operativos por estado, el dashboard consolidado y la
previsión de tesorería. Todo ello conserva validación, permisos por empresa,
auditoría y protección de históricos.

No se declara CRUD genérico de compras, albaranes, stock ni control horario: sus
reglas adicionales requieren una fuente empresarial y casos de uso confirmados.
La integración con el ERP, el cliente Android y el despliegue definitivo quedan
fuera del cierre local; no bloquean las operaciones documentadas aquí.
