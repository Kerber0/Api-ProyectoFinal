-- Datos sintéticos opcionales para demostraciones y pruebas de la API Dorna.
-- No contiene contraseñas, credenciales ni información de empresas reales.
-- Ejecutar después de "base de datos.sql" sobre una base nueva.

BEGIN;

DO $$
DECLARE faltantes integer;
BEGIN
    IF to_regclass('public.dorna_demo_metadata') IS NULL THEN
        RAISE EXCEPTION 'La base de datos Dorna no está inicializada';
    END IF;
    IF EXISTS (SELECT 1 FROM dorna_demo_metadata) THEN
        RAISE EXCEPTION 'Los datos de prueba ya se cargaron o existe una carga anterior';
    END IF;
    IF to_regclass('public.api_tesoreria') IS NULL
       OR to_regclass('public.api_facturacion') IS NULL
       OR to_regclass('public.api_auditoria') IS NULL
       OR to_regclass('public.api_observaciones_obra') IS NULL
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_schema='public' AND table_name='clientes' AND column_name='iban')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_schema='public' AND table_name='operarios' AND column_name='iban') THEN
        RAISE EXCEPTION 'La estructura de la base de datos Dorna está incompleta';
    END IF;
    SELECT count(*) INTO faltantes
    FROM unnest(ARRAY[
        'EMPRESA','CLIENTES','OBRAS','PRESUPUESTOS','DOCUMENTOS','OPERARIOS',
        'PARTES_MANO_OBRA','FACTURAS_CLIENTE','FACTURAS_PROVEEDOR','COBROS','PAGOS',
        'INFORMES','ARTICULOS','ALMACENES','STOCK','PROVEEDORES','PEDIDOS',
        'ALBARANES_CLIENTE','ALBARANES_PROVEEDOR','INCIDENCIAS','EVENTOS','TAREAS'
    ]) AS requerido(nombre)
    LEFT JOIN Permisos p ON p.nombre = requerido.nombre
    WHERE p.id_permiso IS NULL;
    IF faltantes <> 0 THEN
        RAISE EXCEPTION 'Faltan % permisos estructurales requeridos por los datos de prueba', faltantes;
    END IF;
END $$;

INSERT INTO dorna_demo_metadata VALUES
    (1, 'dorna-demo-synthetic-2026-10-29');
INSERT INTO Entidades
    (id_entidad, tipo_entidad, razon_social, nombre_comercial, nif_cif,
     email, observaciones, fecha_alta)
VALUES
    (1, 'EMPRESA', 'Empresa Demostración 01 SL', 'Empresa Demostración 01', 'DEMO-EMP-001',
     'empresa01@example.com', 'Datos sintéticos de demostración', '2026-01-01'),
    (2, 'EMPRESA', 'Empresa Demostración 02 SL', 'Empresa Demostración 02', 'DEMO-EMP-002',
     'empresa02@example.org', 'Datos sintéticos de demostración', '2026-01-01'),
    (3, 'EMPRESA', 'Empresa Demostración 03 SL', 'Empresa Demostración 03', 'DEMO-EMP-003',
     'empresa03@example.net', 'Datos sintéticos de demostración', '2026-01-01'),
    (4, 'EMPRESA', 'Empresa Demostración 04 SL', 'Empresa Demostración 04', 'DEMO-EMP-004',
     'empresa04@example.com', 'Datos sintéticos de demostración', '2026-01-01'),
    (101, 'EMPRESA', 'Cliente Ejemplo 001 SL', 'Cliente Ejemplo 001', 'DEMO-CLI-001',
     'cliente001@example.com', 'Cliente sintético para pruebas', '2026-01-01'),
    (102, 'EMPRESA', 'Cliente Ejemplo 002 SL', 'Cliente Ejemplo 002', 'DEMO-CLI-002',
     NULL, 'Cliente sintético sin correo', '2026-01-01'),
    (201, 'EMPRESA', 'Proveedor Ejemplo 001 SL', 'Proveedor Ejemplo 001', 'DEMO-PRO-001',
     'proveedor001@example.org', 'Proveedor sintético para pruebas', '2026-01-01');

INSERT INTO Entidades
    (id_entidad, tipo_entidad, nombre, apellidos, nif_cif, email, fecha_alta, observaciones)
VALUES
    (301, 'PERSONA', 'Persona', 'de Prueba 001', 'DEMO-OPE-001',
     'persona001@example.com', '2026-01-01', 'Operario sintético para pruebas'),
    (302, 'PERSONA', 'Persona', 'de Prueba 002', 'DEMO-OPE-002',
     NULL, '2026-01-01', 'Operario sintético sin correo');

INSERT INTO Clientes (id_cliente, id_entidad, codigo_cliente, descuento, id_forma_pago)
VALUES (101, 101, 'DEMO-CLI-001', 0, (SELECT id_forma_pago FROM Formas_Pago WHERE descripcion = '30 dias')),
       (102, 102, 'DEMO-CLI-002', 0, NULL);

INSERT INTO Proveedores (id_proveedor, id_entidad, codigo_proveedor, tipo_proveedor)
VALUES (201, 201, 'DEMO-PRO-001', 'EMPRESA');

INSERT INTO Direcciones
    (id_direccion, id_entidad, tipo_direccion, direccion, poblacion, provincia, pais, principal)
VALUES (101, 101, 'OBRA', 'Calle Ejemplo 001', 'Localidad de Prueba',
        'Provincia de Prueba', 'País de Prueba', TRUE);

INSERT INTO Contactos (id_contacto, id_entidad, nombre, cargo, email, principal)
VALUES (101, 101, 'Contacto de Prueba 001', 'Responsable de Prueba',
        'contacto001@example.net', TRUE);

INSERT INTO Operarios
    (id_operario, id_entidad, codigo_operario, fecha_alta, horas_semanales, coste_hora_real, id_categoria)
VALUES (301, 301, 'DEMO-OPE-001', '2026-01-01', 40, 25, (SELECT id_categoria FROM Categorias_Operarios WHERE descripcion = 'Oficial 1')),
       (302, 302, 'DEMO-OPE-002', '2026-01-01', NULL, NULL, NULL);

INSERT INTO Series_Documentos
    (id_serie, tipo_documento, nombre, prefijo, siguiente_numero, longitud, ejercicio)
VALUES (101, 'OBRA', 'Dato sintético 002', 'DEMO-OB-', 4, 3, 2026),
       (102, 'PRESUPUESTO', 'Dato sintético 003', 'DEMO-PR-', 3, 3, 2026),
       (103, 'FACTURA_CLIENTE', 'Dato sintético 004', 'DEMO-FC-', 4, 3, 2026),
       (104, 'FACTURA_PROVEEDOR', 'Dato sintético 005', 'DEMO-FP-', 4, 3, 2026);

INSERT INTO Obras
    (id_obra, id_serie, codigo_obra, titulo, presupuesto, fecha_inicio,
     fecha_fin_prevista, fecha_fin_real, id_estado, id_cliente, id_direccion, observaciones)
VALUES
    (1001, 101, 'DEMO-OB-001', 'Dato sintético 006', 3000,
     '2026-09-01', '2026-11-30', NULL, (SELECT id_estado FROM Estados_Obra WHERE descripcion = 'EN_CURSO'), 101, 101, 'Dato sintético 007'),
    (1002, 101, 'DEMO-OB-002', 'Dato sintético 008', 500,
     '2026-09-01', '2026-09-15', '2026-09-14', (SELECT id_estado FROM Estados_Obra WHERE descripcion = 'FINALIZADA'), 101, 101, 'Dato sintético 009'),
    (1003, 101, 'DEMO-OB-003', 'Dato sintético 010', 0,
     NULL, NULL, NULL, (SELECT id_estado FROM Estados_Obra WHERE descripcion = 'EN_CURSO'), 102, NULL, 'Dato sintético 011');

INSERT INTO Obra_Contacto (id_obra, id_contacto, rol_en_obra)
VALUES (1001, 101, 'Dato sintético 012');

INSERT INTO Almacenes (id_almacen, codigo_almacen, descripcion)
VALUES (101, 'DEMO-ALM-001', 'Dato sintético 013');

INSERT INTO Articulos
    (referencia, descripcion, precio_coste, precio_venta, stock_minimo,
     stock_maximo, id_iva, id_unidad, id_proveedor)
VALUES ('DEMO-ART-001', 'Dato sintético 014', 100, 150, 2, 100, (SELECT id_iva FROM Tipos_IVA WHERE porcentaje = 21), (SELECT id_unidad FROM Unidades_Medida WHERE abreviatura = 'ud'), 201),
       ('DEMO-ART-002', 'Dato sintético 015', 200, 300, 1, 50, (SELECT id_iva FROM Tipos_IVA WHERE porcentaje = 21), (SELECT id_unidad FROM Unidades_Medida WHERE abreviatura = 'ud'), 201);

INSERT INTO Movimientos_Stock
    (referencia_articulo, id_almacen, fecha, tipo_movimiento, cantidad, precio_unitario, observaciones)
VALUES ('DEMO-ART-001', 101, '2026-09-01 09:00:00', 'ENTRADA', 10, 100, 'Dato sintético 016'),
       ('DEMO-ART-001', 101, '2026-09-10 09:00:00', 'SALIDA', 1, 100, 'Dato sintético 017'),
       ('DEMO-ART-002', 101, '2026-09-01 09:00:00', 'ENTRADA', 5, 200, 'Dato sintético 018'),
       ('DEMO-ART-002', 101, '2026-09-10 09:00:00', 'SALIDA', 1, 200, 'Dato sintético 019');

INSERT INTO Materiales_Por_Obra
    (id_obra, referencia_articulo, id_almacen, fecha, cantidad, coste_unitario, importe)
VALUES (1001, 'DEMO-ART-001', 101, '2026-09-10', 1, 100, 100),
       (1001, 'DEMO-ART-002', 101, '2026-09-10', 1, 200, 200);

INSERT INTO Partes_Mano_Obra
    (id_obra, id_operario, fecha, hora_inicio, hora_fin, horas_trabajadas, coste_hora, coste_total, observaciones)
VALUES (1001, 301, '2026-09-10', '08:00:00', '16:00:00', 8, 25, 200, 'Dato sintético 020'),
       (1001, 301, '2026-09-11', '08:00:00', '16:00:00', 8, 25, 200, 'Dato sintético 021');

INSERT INTO Presupuestos
    (id_presupuesto, numero, id_serie, fecha, validez, subtotal, descuento,
     impuestos, total, id_estado, id_cliente, id_obra)
VALUES (4001, 'DEMO-PR-001', 102, '2026-08-01', '2026-08-31', 3000, 0, 630, 3630, (SELECT id_estado FROM Estados_Factura WHERE descripcion = 'ACEPTADO'), 101, 1001),
       (4002, 'DEMO-PR-002', 102, '2026-08-01', NULL, 500, 0, 105, 605, (SELECT id_estado FROM Estados_Factura WHERE descripcion = 'ACEPTADO'), 101, 1002);

INSERT INTO Lineas_Presupuesto
    (id_presupuesto, descripcion, cantidad, precio, descuento, iva, importe)
VALUES (4001, 'Dato sintético 022', 1, 1000, 0, 21, 1000),
       (4001, 'Dato sintético 023', 2, 1000, 0, 21, 2000),
       (4002, 'Dato sintético 024', 1, 500, 0, 21, 500);

INSERT INTO Facturas_Cliente
    (id_factura, numero, id_serie, fecha, fecha_vencimiento, subtotal, descuento,
     impuestos, total, id_estado, id_cliente, id_obra)
VALUES (5001, 'DEMO-FC-001', 103, '2026-09-01', '2026-09-30', 1000, 0, 210, 1210, (SELECT id_estado FROM Estados_Factura WHERE descripcion = 'PENDIENTE'), 101, 1001),
       (5002, 'DEMO-FC-002', 103, '2026-09-15', '2026-10-15', 2000, 0, 420, 2420, (SELECT id_estado FROM Estados_Factura WHERE descripcion = 'PARCIAL'), 101, 1001),
       (5003, 'DEMO-FC-003', 103, '2026-09-14', '2026-09-30', 500, 0, 105, 605, (SELECT id_estado FROM Estados_Factura WHERE descripcion = 'PAGADA'), 101, 1002);

INSERT INTO Lineas_Factura_Cliente
    (id_factura, descripcion, cantidad, precio, descuento, iva, importe)
VALUES (5001, 'Dato sintético 025', 1, 1000, 0, 21, 1000),
       (5002, 'Dato sintético 026', 2, 1000, 0, 21, 2000),
       (5003, 'Dato sintético 027', 1, 500, 0, 21, 500);

INSERT INTO Cobros (id_factura, fecha, importe, metodo_pago, numero_operacion)
VALUES (5002, '2026-09-20', 400, 'TRANSFERENCIA', 'DEMO-COB-001'),
       (5002, '2026-10-01', 600, 'TRANSFERENCIA', 'DEMO-COB-002'),
       (5003, '2026-09-20', 605, 'TRANSFERENCIA', 'DEMO-COB-003');

INSERT INTO Facturas_Proveedor
    (id_factura, numero, id_serie, fecha, fecha_vencimiento, subtotal, descuento,
     impuestos, total, id_estado, id_proveedor)
VALUES (6001, 'DEMO-FP-001', 104, '2026-09-01', '2026-09-30', 400, 0, 84, 484, (SELECT id_estado FROM Estados_Factura WHERE descripcion = 'PENDIENTE'), 201),
       (6002, 'DEMO-FP-002', 104, '2026-09-15', '2026-10-15', 800, 0, 168, 968, (SELECT id_estado FROM Estados_Factura WHERE descripcion = 'PARCIAL'), 201),
       (6003, 'DEMO-FP-003', 104, '2026-09-01', '2026-09-30', 200, 0, 42, 242, (SELECT id_estado FROM Estados_Factura WHERE descripcion = 'PAGADA'), 201);

INSERT INTO Lineas_Factura_Proveedor
    (id_factura, descripcion, cantidad, precio, descuento, iva, importe)
VALUES (6001, 'Dato sintético 028', 1, 400, 0, 21, 400),
       (6002, 'Dato sintético 029', 1, 800, 0, 21, 800),
       (6003, 'Dato sintético 030', 1, 200, 0, 21, 200);

INSERT INTO Pagos (id_factura, fecha, importe, metodo_pago, numero_operacion)
VALUES (6002, '2026-09-20', 300, 'TRANSFERENCIA', 'DEMO-PAG-001'),
       (6002, '2026-10-01', 200, 'TRANSFERENCIA', 'DEMO-PAG-002'),
       (6003, '2026-09-20', 242, 'TRANSFERENCIA', 'DEMO-PAG-003');

INSERT INTO API_Empresas (id_empresa, nombre) VALUES
    (1, 'Empresa Demostración 01'), (2, 'Empresa Demostración 02'),
    (3, 'Empresa Demostración 03'), (4, 'Empresa Demostración 04');

-- Cada empresa de demostración se vincula con su entidad sintética.
UPDATE API_Empresas SET id_entidad = id_empresa WHERE id_empresa IN (1,2,3,4);

INSERT INTO Roles (nombre, descripcion) VALUES
    ('CONSULTA_EMPRESA_DEMO', 'Dato sintético 031');


INSERT INTO Rol_Permiso (id_rol, id_permiso, nivel_acceso)
SELECT r.id_rol, p.id_permiso, 'LECTURA'
FROM Roles r CROSS JOIN Permisos p
WHERE r.nombre = 'CONSULTA_EMPRESA_DEMO' AND p.nombre = 'EMPRESA';

INSERT INTO API_Cliente_Empresa (id_cliente, id_empresa)
SELECT c.id_cliente, e.id_empresa
FROM Clientes c CROSS JOIN API_Empresas e
WHERE e.activo = TRUE;

INSERT INTO API_Obra_Empresa VALUES (1001, 1), (1002, 1), (1003, 2);

INSERT INTO Roles (nombre, descripcion) VALUES
('CONSULTA_RECURSOS_DEMO', 'Dato sintético 032');


INSERT INTO Rol_Permiso (id_rol, id_permiso, nivel_acceso)
SELECT r.id_rol, p.id_permiso, 'LECTURA' FROM Roles r CROSS JOIN Permisos p
WHERE r.nombre = 'CONSULTA_RECURSOS_DEMO' AND p.nombre IN ('EMPRESA', 'CLIENTES', 'OBRAS');

INSERT INTO API_Accesos_Empresa (id_usuario, id_empresa, id_rol)
SELECT u.id_usuario, 1, r.id_rol FROM Usuarios u CROSS JOIN Roles r
WHERE u.nombre_usuario = 'demo' AND r.nombre = 'CONSULTA_RECURSOS_DEMO';

INSERT INTO Presupuestos
(id_presupuesto, numero, id_serie, fecha, subtotal, descuento, impuestos, total, id_estado, id_cliente, id_obra)
SELECT 4003, 'DEMO-PR-003', 102, '2026-10-01', 100, 0, 21, 121, id_estado, 102, 1003
FROM Estados_Factura WHERE descripcion = 'PENDIENTE';

INSERT INTO Lineas_Presupuesto
(id_linea, id_presupuesto, descripcion, cantidad, precio, descuento, iva, importe)
VALUES (44003, 4003, 'Dato sintético 033', 1, 100, 0, 21, 100);

INSERT INTO API_Presupuesto_Empresa VALUES (4001, 1), (4002, 1), (4003, 2);

INSERT INTO Documentos (id_documento, id_obra, nombre, ruta, tipo, fecha_subida)
VALUES (7001, 1001, 'demo-public/archivo-001.txt', 'Dato sintético 034', 'text/plain', '2026-10-01 10:00:00'),
       (7002, 1003, 'demo-public/archivo-002.txt', 'Dato sintético 035', 'text/plain', '2026-10-01 11:00:00');

INSERT INTO API_Archivo_Documento VALUES
(7001, 1, 'c58f5263-bde1-41d9-a592-f909e0dfb871'),
(7002, 2, 'baed0adc-712f-44bc-9dd8-4497a3d01696');


INSERT INTO Rol_Permiso (id_rol, id_permiso, nivel_acceso)
SELECT r.id_rol, p.id_permiso, 'LECTURA' FROM Roles r CROSS JOIN Permisos p
WHERE r.nombre = 'CONSULTA_RECURSOS_DEMO' AND p.nombre IN ('PRESUPUESTOS', 'DOCUMENTOS');

INSERT INTO API_Operario_Empresa VALUES (301, 1), (302, 2);

INSERT INTO Partes_Mano_Obra
(id_parte, id_obra, id_operario, fecha, horas_trabajadas, coste_hora, coste_total, observaciones)
VALUES (81003, 1003, 302, '2026-09-12', 2.50, 20, 50, 'demo-public/archivo-003.txt');


INSERT INTO Rol_Permiso (id_rol, id_permiso, nivel_acceso)
SELECT r.id_rol, p.id_permiso, 'LECTURA' FROM Roles r CROSS JOIN Permisos p
WHERE r.nombre = 'CONSULTA_RECURSOS_DEMO' AND p.nombre IN ('OPERARIOS', 'PARTES_MANO_OBRA');

INSERT INTO Entidades (id_entidad, tipo_entidad, razon_social, nif_cif, fecha_alta)
VALUES (202, 'EMPRESA', 'Proveedor Ejemplo 002 SL', 'DEMO-PRO-002', '2026-01-01');

INSERT INTO Proveedores (id_proveedor, id_entidad, codigo_proveedor, tipo_proveedor)
VALUES (202, 202, 'DEMO-PRO-002', 'EMPRESA');

INSERT INTO API_Proveedor_Empresa (id_proveedor, id_empresa)
SELECT p.id_proveedor, e.id_empresa
FROM Proveedores p CROSS JOIN API_Empresas e
WHERE e.activo = TRUE;

INSERT INTO Facturas_Cliente
(id_factura, numero, id_serie, fecha, fecha_vencimiento, subtotal, descuento, impuestos, total, id_estado, id_cliente, id_obra)
SELECT 5004, 'DEMO-FC-004', 103, '2026-09-20', '2026-10-20', 100, 0, 21, 121, id_estado, 102, 1003
FROM Estados_Factura WHERE descripcion = 'PARCIAL';

INSERT INTO Facturas_Proveedor
(id_factura, numero, id_serie, fecha, subtotal, descuento, impuestos, total, id_estado, id_proveedor)
SELECT 6004, 'DEMO-FP-004', 104, '2026-09-20', 200, 0, 42, 242, id_estado, 202
FROM Estados_Factura WHERE descripcion = 'PARCIAL';

INSERT INTO API_Factura_Cliente_Empresa VALUES (5001, 1), (5002, 1), (5003, 1), (5004, 2);

INSERT INTO API_Factura_Proveedor_Empresa VALUES (6001, 1), (6002, 1), (6003, 1), (6004, 2);

INSERT INTO Lineas_Factura_Cliente (id_linea, id_factura, descripcion, cantidad, precio, descuento, iva, importe)
VALUES (54004, 5004, 'Dato sintético 036', 1, 100, 0, 21, 100);

INSERT INTO Lineas_Factura_Proveedor (id_linea, id_factura, descripcion, cantidad, precio, descuento, iva, importe)
VALUES (64004, 6004, 'Dato sintético 037', 1, 200, 0, 21, 200);

INSERT INTO Cobros (id_cobro, id_factura, fecha, importe, metodo_pago)
VALUES (95004, 5004, '2026-09-22', 40, 'TRANSFERENCIA');

INSERT INTO Pagos (id_pago, id_factura, fecha, importe, metodo_pago)
VALUES (96004, 6004, '2026-09-22', 42, 'TRANSFERENCIA');


INSERT INTO Rol_Permiso (id_rol, id_permiso, nivel_acceso)
SELECT r.id_rol, p.id_permiso, 'LECTURA' FROM Roles r CROSS JOIN Permisos p
WHERE r.nombre = 'CONSULTA_RECURSOS_DEMO' AND p.nombre IN ('FACTURAS_CLIENTE', 'FACTURAS_PROVEEDOR', 'COBROS', 'PAGOS');


INSERT INTO Rol_Permiso (id_rol, id_permiso, nivel_acceso)
SELECT r.id_rol, p.id_permiso, 'LECTURA' FROM Roles r CROSS JOIN Permisos p
WHERE r.nombre = 'CONSULTA_RECURSOS_DEMO' AND p.nombre = 'INFORMES';

INSERT INTO Almacenes (id_almacen, codigo_almacen, descripcion) VALUES
(102, 'DEMO-ALM-002', 'Dato sintético 038'),
(201, 'DEMO-ALM-003', 'Dato sintético 039');

INSERT INTO Articulos (referencia, descripcion) VALUES
('DEMO-ART-003', 'Dato sintético 040'),
('DEMO-ART-004', 'Dato sintético 041');

INSERT INTO API_Articulo_Empresa VALUES
('DEMO-ART-001', 1), ('DEMO-ART-002', 1), ('DEMO-ART-003', 2), ('DEMO-ART-004', 1);

INSERT INTO API_Almacen_Empresa VALUES (101, 1), (102, 1), (201, 2);

INSERT INTO Movimientos_Stock
(id_movimiento, referencia_articulo, id_almacen, fecha, tipo_movimiento, cantidad) VALUES
(97001, 'DEMO-ART-001', 101, '2026-09-12 16:00:00', 'TRASPASO', 2),
(97002, 'DEMO-ART-001', 101, '2026-09-13 23:59:59', 'AJUSTE', -0.50),
(97003, 'DEMO-ART-003', 201, '2026-09-01 09:00:00', 'ENTRADA', 7);

INSERT INTO API_Traspaso_Stock VALUES (97001, 102);


INSERT INTO Rol_Permiso (id_rol, id_permiso, nivel_acceso)
SELECT r.id_rol, p.id_permiso, 'LECTURA' FROM Roles r CROSS JOIN Permisos p
WHERE r.nombre = 'CONSULTA_RECURSOS_DEMO' AND p.nombre IN ('ARTICULOS', 'ALMACENES', 'STOCK');

INSERT INTO Series_Documentos
(id_serie, tipo_documento, nombre, prefijo, siguiente_numero, longitud, ejercicio)
VALUES (105, 'PEDIDO_PROVEEDOR', 'Dato sintético 042', 'DEMO-PP-', 5, 3, 2026);

INSERT INTO Pedidos_Proveedor
(id_pedido, numero, id_serie, fecha, subtotal, descuento, impuestos, total, id_estado, id_proveedor)
SELECT 98001, 'DEMO-PP-001', 105, '2026-09-01', 400, 0, 84, 484, id_estado, 201
FROM Estados_Factura WHERE descripcion = 'PENDIENTE';

INSERT INTO Pedidos_Proveedor
(id_pedido, numero, id_serie, fecha, subtotal, descuento, impuestos, total, id_estado, id_proveedor)
SELECT 98002, 'DEMO-PP-002', 105, '2026-09-15', 200, 20, 37.80, 217.80, id_estado, 201
FROM Estados_Factura WHERE descripcion = 'ACEPTADO';

INSERT INTO Pedidos_Proveedor
(id_pedido, numero, id_serie, fecha, subtotal, descuento, impuestos, total, id_estado, id_proveedor)
SELECT 98003, 'DEMO-PP-003', 105, '2026-09-15', 30, 0, 6.30, 36.30, id_estado, 202
FROM Estados_Factura WHERE descripcion = 'PENDIENTE';

INSERT INTO Pedidos_Proveedor
(id_pedido, numero, id_serie, fecha, id_estado, id_proveedor)
SELECT 98004, 'DEMO-PP-004', 105, '2026-10-01', id_estado, 201
FROM Estados_Factura WHERE descripcion = 'PENDIENTE';

INSERT INTO API_Pedido_Proveedor_Empresa VALUES (98001, 1), (98002, 1), (98003, 2), (98004, 1);

INSERT INTO Lineas_Pedido
(id_linea, id_pedido, referencia_articulo, descripcion, cantidad, precio_coste, descuento, iva, importe)
VALUES
(98101, 98001, 'DEMO-ART-001', 'Dato sintético 043', 2, 100, 0, 21, 200),
(98102, 98001, 'DEMO-ART-002', 'Dato sintético 044', 1, 200, 0, 21, 200),
(98103, 98002, 'DEMO-ART-002', NULL, 1, 200, 10, 21, 180),
(98104, 98003, 'DEMO-ART-003', 'Dato sintético 045', 3, 10, 0, 21, 30);


INSERT INTO Rol_Permiso (id_rol, id_permiso, nivel_acceso)
SELECT r.id_rol, p.id_permiso, 'LECTURA' FROM Roles r CROSS JOIN Permisos p
WHERE r.nombre = 'CONSULTA_RECURSOS_DEMO' AND p.nombre IN ('PROVEEDORES', 'PEDIDOS');

INSERT INTO Series_Documentos
(id_serie, tipo_documento, nombre, prefijo, siguiente_numero, longitud, ejercicio) VALUES
(106, 'ALBARAN_CLIENTE', 'Dato sintético 046', 'DEMO-AC-', 5, 3, 2026),
(107, 'ALBARAN_PROVEEDOR', 'Dato sintético 047', 'DEMO-AP-', 6, 3, 2026);

INSERT INTO Albaranes_Cliente
(id_albaran, numero, id_serie, fecha, subtotal, impuestos, total, id_estado, id_cliente, id_factura)
SELECT 99001, 'DEMO-AC-001', 106, '2026-09-10', 200, 42, 242, id_estado, 101, NULL
FROM Estados_Factura WHERE descripcion = 'PENDIENTE';

INSERT INTO Albaranes_Cliente
(id_albaran, numero, id_serie, fecha, subtotal, impuestos, total, id_estado, id_cliente)
SELECT 99002, 'DEMO-AC-002', 106, '2026-09-15', 75, 15.75, 90.75, id_estado, 101
FROM Estados_Factura WHERE descripcion = 'ACEPTADO';

INSERT INTO Albaranes_Cliente
(id_albaran, numero, id_serie, fecha, subtotal, impuestos, total, id_estado, id_cliente, id_factura)
SELECT 99003, 'DEMO-AC-003', 106, '2026-09-15', 10, 2.10, 12.10, id_estado, 102, NULL
FROM Estados_Factura WHERE descripcion = 'PENDIENTE';

INSERT INTO Albaranes_Cliente (id_albaran, numero, id_serie, fecha, id_estado, id_cliente)
SELECT 99004, 'DEMO-AC-004', 106, '2026-10-01', id_estado, 101
FROM Estados_Factura WHERE descripcion = 'PENDIENTE';

INSERT INTO API_Albaran_Cliente_Empresa VALUES (99001, 1), (99002, 1), (99003, 2), (99004, 1);

INSERT INTO Lineas_Albaran_Cliente
(id_linea, id_albaran, referencia_articulo, descripcion, cantidad, precio, descuento, iva, importe) VALUES
(99201, 99001, 'DEMO-ART-001', 'Dato sintético 048', 1, 150, 0, 21, 150),
(99202, 99001, NULL, 'Dato sintético 049', 1, 50, 0, 21, 50),
(99203, 99002, 'DEMO-ART-002', NULL, 1, 75, 0, 21, NULL),
(99204, 99003, 'DEMO-ART-003', 'Dato sintético 050', 1, 10, 0, 21, 10);

INSERT INTO Albaranes_Proveedor
(id_albaran, numero, id_serie, fecha, subtotal, impuestos, total, id_estado, id_proveedor, id_factura)
SELECT 99101, 'DEMO-AP-001', 107, '2026-09-10', 200, 42, 242, id_estado, 201, NULL
FROM Estados_Factura WHERE descripcion = 'PENDIENTE';

INSERT INTO Albaranes_Proveedor
(id_albaran, numero, id_serie, fecha, subtotal, impuestos, total, id_estado, id_proveedor, id_factura)
SELECT 99102, 'DEMO-AP-002', 107, '2026-09-15', 100, 21, 121, id_estado, 201, NULL
FROM Estados_Factura WHERE descripcion = 'ACEPTADO';

INSERT INTO Albaranes_Proveedor
(id_albaran, numero, id_serie, fecha, subtotal, impuestos, total, id_estado, id_proveedor, id_factura)
SELECT 99103, 'DEMO-AP-003', 107, '2026-09-15', 10, 2.10, 12.10, id_estado, 202, NULL
FROM Estados_Factura WHERE descripcion = 'PENDIENTE';

INSERT INTO Albaranes_Proveedor (id_albaran, numero, id_serie, fecha, id_estado, id_proveedor)
SELECT 99104, 'DEMO-AP-004', 107, '2026-10-01', id_estado, 201
FROM Estados_Factura WHERE descripcion = 'PENDIENTE';

INSERT INTO Albaranes_Proveedor (id_albaran, numero, id_serie, fecha, id_estado, id_proveedor)
SELECT 99105, 'DEMO-AP-005', 107, '2026-10-02', id_estado, 201
FROM Estados_Factura WHERE descripcion = 'PENDIENTE';

INSERT INTO API_Albaran_Proveedor_Empresa VALUES (99101, 1), (99102, 1), (99103, 2), (99104, 1), (99105, 1);

INSERT INTO Lineas_Albaran_Proveedor
(id_linea, id_albaran, referencia_articulo, descripcion, cantidad, precio, descuento, iva, importe) VALUES
(99301, 99101, 'DEMO-ART-001', 'Dato sintético 051', 1, 100, 0, 21, 100),
(99302, 99101, 'DEMO-ART-002', 'Dato sintético 052', 0.50, 200, 0, 21, 100),
(99303, 99102, 'DEMO-ART-001', 'Dato sintético 053', 1, 100, 0, 21, 100),
(99304, 99103, 'DEMO-ART-003', 'Dato sintético 054', 1, 10, 0, 21, 10),
(99305, 99105, NULL, NULL, 1, 0, 0, 21, NULL);


INSERT INTO Rol_Permiso (id_rol, id_permiso, nivel_acceso)
SELECT r.id_rol, p.id_permiso, 'LECTURA' FROM Roles r CROSS JOIN Permisos p
WHERE r.nombre = 'CONSULTA_RECURSOS_DEMO' AND p.nombre IN ('ALBARANES_CLIENTE', 'ALBARANES_PROVEEDOR');

INSERT INTO Incidencias (id_incidencia, id_obra, id_operario, fecha, descripcion, id_estado, id_prioridad)
SELECT 99501, 1001, 301, '2026-09-10', 'Dato sintético 055', s.id_estado, p.id_prioridad
FROM Estados_Incidencia s CROSS JOIN Prioridades p WHERE s.descripcion = 'ABIERTA' AND p.descripcion = 'ALTA';

INSERT INTO Incidencias (id_incidencia, id_obra, id_operario, fecha, descripcion, id_estado, id_prioridad)
SELECT 99502, 1001, NULL, '2026-09-11', 'Dato sintético 056', s.id_estado, p.id_prioridad
FROM Estados_Incidencia s CROSS JOIN Prioridades p WHERE s.descripcion = 'EN_PROCESO' AND p.descripcion = 'MEDIA';

INSERT INTO Incidencias (id_incidencia, id_obra, id_operario, fecha, descripcion, id_estado, id_prioridad)
SELECT 99503, 1003, 302, '2026-09-11', 'Dato sintético 057', s.id_estado, p.id_prioridad
FROM Estados_Incidencia s CROSS JOIN Prioridades p WHERE s.descripcion = 'CERRADA' AND p.descripcion = 'BAJA';

INSERT INTO Incidencias (id_incidencia, id_obra, id_operario, fecha, descripcion, id_estado, id_prioridad)
SELECT 99504, 1001, 301, '2026-09-11', 'Dato sintético 058', s.id_estado, p.id_prioridad
FROM Estados_Incidencia s CROSS JOIN Prioridades p WHERE s.descripcion = 'RESUELTA' AND p.descripcion = 'ALTA';

INSERT INTO API_Incidencia_Empresa VALUES (99501, 1), (99502, 1), (99503, 2), (99504, 1);


INSERT INTO Rol_Permiso (id_rol, id_permiso, nivel_acceso)
SELECT r.id_rol, p.id_permiso, 'LECTURA' FROM Roles r CROSS JOIN Permisos p
WHERE r.nombre = 'CONSULTA_RECURSOS_DEMO' AND p.nombre = 'INCIDENCIAS';

INSERT INTO Eventos (id_evento, titulo, descripcion, fecha_inicio, fecha_fin, tipo_evento, id_obra) VALUES
(99601, 'Dato sintético 059', 'Dato sintético 060', '2026-09-10 08:00:00', '2026-09-12 17:00:00', 'OBRA', 1001),
(99602, 'Dato sintético 061', NULL, '2026-09-11 09:00:00', NULL, 'AVISO', NULL),
(99603, 'Dato sintético 062', 'Dato sintético 063', '2026-09-11 10:00:00', '2026-09-11 11:00:00', 'REUNION', 1003),
(99604, 'Dato sintético 064', 'Dato sintético 065', '2026-09-11 09:00:00', '2026-09-11 10:00:00', 'REUNION', 1001);

INSERT INTO API_Evento_Empresa VALUES (99601, 1), (99602, 1), (99603, 2), (99604, 1);

INSERT INTO Tareas (id_tarea, titulo, descripcion, fecha_limite, fecha_finalizacion, id_obra, id_operario, id_estado, id_prioridad)
SELECT 99701, 'Dato sintético 066', 'Dato sintético 067', '2026-09-10', NULL, 1001, 301, s.id_estado, p.id_prioridad
FROM Estados_Tarea s CROSS JOIN Prioridades p WHERE s.descripcion = 'PENDIENTE' AND p.descripcion = 'ALTA';

INSERT INTO Tareas (id_tarea, titulo, id_estado, id_prioridad)
SELECT 99702, 'Dato sintético 068', s.id_estado, p.id_prioridad
FROM Estados_Tarea s CROSS JOIN Prioridades p WHERE s.descripcion = 'PENDIENTE' AND p.descripcion = 'MEDIA';

INSERT INTO Tareas (id_tarea, titulo, fecha_limite, id_obra, id_operario, id_estado, id_prioridad)
SELECT 99703, 'Dato sintético 069', '2026-09-11', 1003, 302, s.id_estado, p.id_prioridad
FROM Estados_Tarea s CROSS JOIN Prioridades p WHERE s.descripcion = 'EN_CURSO' AND p.descripcion = 'MEDIA';

INSERT INTO Tareas (id_tarea, titulo, descripcion, fecha_limite, fecha_finalizacion, id_obra, id_operario, id_estado, id_prioridad)
SELECT 99704, 'Dato sintético 070', 'Dato sintético 071', '2026-09-10', '2026-09-09', 1001, 301, s.id_estado, p.id_prioridad
FROM Estados_Tarea s CROSS JOIN Prioridades p WHERE s.descripcion = 'FINALIZADA' AND p.descripcion = 'BAJA';

INSERT INTO API_Tarea_Empresa VALUES (99701, 1), (99702, 1), (99703, 2), (99704, 1);


INSERT INTO Rol_Permiso (id_rol, id_permiso, nivel_acceso)
SELECT r.id_rol, p.id_permiso, 'LECTURA' FROM Roles r CROSS JOIN Permisos p
WHERE r.nombre = 'CONSULTA_RECURSOS_DEMO' AND p.nombre IN ('EVENTOS', 'TAREAS');

UPDATE Albaranes_Cliente SET id_factura=5001 WHERE id_albaran=99001 AND id_factura IS NULL;

UPDATE Albaranes_Cliente SET id_factura=5004 WHERE id_albaran=99003 AND id_factura IS NULL;

UPDATE Albaranes_Proveedor SET id_factura=6001 WHERE id_albaran IN (99101,99102) AND id_factura IS NULL;

UPDATE Albaranes_Proveedor SET id_factura=6004 WHERE id_albaran=99103 AND id_factura IS NULL;

-- IDs explicitos de fixtures no avanzan identidades PostgreSQL.
DO $$ DECLARE c record; maximo bigint; BEGIN
 FOR c IN SELECT table_name, column_name FROM information_schema.columns WHERE table_schema='public' AND is_identity='YES' LOOP
  EXECUTE format('SELECT max(%I) FROM %I',c.column_name,c.table_name) INTO maximo;
  PERFORM setval(pg_get_serial_sequence(format('%I',c.table_name),c.column_name),COALESCE(maximo,1),maximo IS NOT NULL);
 END LOOP;
END $$;

COMMIT;
