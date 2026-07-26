/**************************************************************************
* PROYECTO ERP GESTION DE OBRAS
* Esquema normalizado para MySQL / MariaDB compatible con Hibernate.
*
* Este script define el esquema objetivo. No ejecuta DROP DATABASE para no
* destruir datos existentes por accidente.
**************************************************************************/

CREATE DATABASE IF NOT EXISTS EmpresaObras
CHARACTER SET utf8mb4
COLLATE utf8mb4_spanish_ci;

USE EmpresaObras;


/**************************************************************************
* ENTIDADES, USUARIOS Y SEGURIDAD
**************************************************************************/

CREATE TABLE Entidades(
    id_entidad INT AUTO_INCREMENT,
    tipo_entidad VARCHAR(10) NOT NULL,
    nombre VARCHAR(100),
    apellidos VARCHAR(150),
    razon_social VARCHAR(150),
    nombre_comercial VARCHAR(150),
    nif_cif VARCHAR(20) NOT NULL,
    telefono_fijo VARCHAR(20),
    movil VARCHAR(20),
    email VARCHAR(150),
    web VARCHAR(150),
    fecha_nacimiento DATE,
    observaciones TEXT,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_alta DATE NOT NULL DEFAULT (CURRENT_DATE),
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_entidad),
    UNIQUE uk_entidad_nif_cif(nif_cif),
    INDEX idx_entidad_tipo(tipo_entidad),
    INDEX idx_entidad_nombre(nombre),
    INDEX idx_entidad_razon_social(razon_social),
    INDEX idx_entidad_nombre_comercial(nombre_comercial),
    INDEX idx_entidad_activo(activo),
    INDEX idx_entidad_usuario_creacion(usuario_creacion),
    INDEX idx_entidad_usuario_modificacion(usuario_modificacion),

    CONSTRAINT chk_entidad_tipo
        CHECK(tipo_entidad IN ('PERSONA','EMPRESA')),

    CONSTRAINT chk_entidad_persona_empresa
        CHECK(
            (tipo_entidad='PERSONA' AND nombre IS NOT NULL AND razon_social IS NULL)
            OR
            (tipo_entidad='EMPRESA' AND razon_social IS NOT NULL AND nombre IS NULL AND apellidos IS NULL)
        )
)ENGINE=InnoDB;

CREATE TABLE Usuarios(
    id_usuario INT AUTO_INCREMENT,
    id_entidad INT,
    nombre_usuario VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email_login VARCHAR(150),
    ultimo_login DATETIME,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_usuario),
    UNIQUE uk_usuario_nombre(nombre_usuario),
    UNIQUE uk_usuario_email_login(email_login),
    INDEX idx_usuario_entidad(id_entidad),
    INDEX idx_usuario_activo(activo),
    INDEX idx_usuario_usuario_creacion(usuario_creacion),
    INDEX idx_usuario_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_usuario_entidad
        FOREIGN KEY(id_entidad)
        REFERENCES Entidades(id_entidad)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_usuario_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_usuario_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

ALTER TABLE Entidades
    ADD CONSTRAINT fk_entidad_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    ADD CONSTRAINT fk_entidad_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL;

CREATE TABLE Roles(
    id_rol INT AUTO_INCREMENT,
    nombre VARCHAR(50) NOT NULL,
    descripcion VARCHAR(300),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_rol),
    UNIQUE uk_rol_nombre(nombre),
    INDEX idx_rol_activo(activo),
    INDEX idx_rol_usuario_creacion(usuario_creacion),
    INDEX idx_rol_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_rol_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_rol_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Permisos(
    id_permiso INT AUTO_INCREMENT,
    nombre VARCHAR(80) NOT NULL,
    descripcion VARCHAR(300),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_permiso),
    UNIQUE uk_permiso_nombre(nombre),
    INDEX idx_permiso_activo(activo),
    INDEX idx_permiso_usuario_creacion(usuario_creacion),
    INDEX idx_permiso_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_permiso_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_permiso_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Usuario_Rol(
    id_usuario_rol INT AUTO_INCREMENT,
    id_usuario INT NOT NULL,
    id_rol INT NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_usuario_rol),
    UNIQUE uk_usuario_rol(id_usuario,id_rol),
    INDEX idx_usuario_rol_rol(id_rol),
    INDEX idx_usuario_rol_usuario_creacion(usuario_creacion),
    INDEX idx_usuario_rol_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_usuario_rol_usuario
        FOREIGN KEY(id_usuario)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_usuario_rol_rol
        FOREIGN KEY(id_rol)
        REFERENCES Roles(id_rol)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_usuario_rol_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_usuario_rol_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Rol_Permiso(
    id_rol_permiso INT AUTO_INCREMENT,
    id_rol INT NOT NULL,
    id_permiso INT NOT NULL,
    nivel_acceso VARCHAR(10) NOT NULL DEFAULT 'LECTURA',
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_rol_permiso),
    UNIQUE uk_rol_permiso(id_rol,id_permiso),
    INDEX idx_rol_permiso_permiso(id_permiso),
    INDEX idx_rol_permiso_usuario_creacion(usuario_creacion),
    INDEX idx_rol_permiso_usuario_modificacion(usuario_modificacion),

    CONSTRAINT chk_rol_permiso_nivel
        CHECK(nivel_acceso IN ('LECTURA','ESCRITURA','ADMIN')),

    CONSTRAINT fk_rol_permiso_rol
        FOREIGN KEY(id_rol)
        REFERENCES Roles(id_rol)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_rol_permiso_permiso
        FOREIGN KEY(id_permiso)
        REFERENCES Permisos(id_permiso)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_rol_permiso_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_rol_permiso_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;


/**************************************************************************
* TABLAS DE CONFIGURACION
**************************************************************************/

CREATE TABLE Zonas(
    id_zona INT AUTO_INCREMENT,
    descripcion VARCHAR(300) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_zona),
    UNIQUE uk_zona_descripcion(descripcion),
    INDEX idx_zona_activo(activo),
    INDEX idx_zona_usuario_creacion(usuario_creacion),
    INDEX idx_zona_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_zona_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_zona_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Formas_Pago(
    id_forma_pago INT AUTO_INCREMENT,
    descripcion VARCHAR(300) NOT NULL,
    dias_vencimiento INT NOT NULL DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_forma_pago),
    UNIQUE uk_forma_pago_descripcion(descripcion),
    INDEX idx_forma_pago_activo(activo),
    INDEX idx_forma_pago_usuario_creacion(usuario_creacion),
    INDEX idx_forma_pago_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_forma_pago_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_forma_pago_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Tipos_IVA(
    id_iva INT AUTO_INCREMENT,
    descripcion VARCHAR(300) NOT NULL,
    porcentaje DECIMAL(5,2) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_iva),
    UNIQUE uk_iva_porcentaje(porcentaje),
    INDEX idx_iva_activo(activo),
    INDEX idx_iva_usuario_creacion(usuario_creacion),
    INDEX idx_iva_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_iva_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_iva_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Familias(
    id_familia INT AUTO_INCREMENT,
    descripcion VARCHAR(300) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_familia),
    UNIQUE uk_familia_descripcion(descripcion),
    INDEX idx_familia_activo(activo),
    INDEX idx_familia_usuario_creacion(usuario_creacion),
    INDEX idx_familia_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_familia_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_familia_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Unidades_Medida(
    id_unidad INT AUTO_INCREMENT,
    descripcion VARCHAR(300) NOT NULL,
    abreviatura VARCHAR(10) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_unidad),
    UNIQUE uk_unidad_abreviatura(abreviatura),
    INDEX idx_unidad_activo(activo),
    INDEX idx_unidad_usuario_creacion(usuario_creacion),
    INDEX idx_unidad_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_unidad_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_unidad_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Almacenes(
    id_almacen INT AUTO_INCREMENT,
    codigo_almacen VARCHAR(20),
    descripcion VARCHAR(300) NOT NULL,
    direccion VARCHAR(200),
    ciudad VARCHAR(100),
    provincia VARCHAR(100),
    codigo_postal VARCHAR(10),
    telefono VARCHAR(20),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_almacen),
    UNIQUE uk_almacen_codigo(codigo_almacen),
    INDEX idx_almacen_descripcion(descripcion),
    INDEX idx_almacen_usuario_creacion(usuario_creacion),
    INDEX idx_almacen_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_almacen_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_almacen_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Categorias_Operarios(
    id_categoria INT AUTO_INCREMENT,
    descripcion VARCHAR(300) NOT NULL,
    coste_hora_base DECIMAL(10,2) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_categoria),
    UNIQUE uk_categoria_operario_descripcion(descripcion),
    INDEX idx_categoria_operario_activo(activo),
    INDEX idx_categoria_operario_usuario_creacion(usuario_creacion),
    INDEX idx_categoria_operario_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_categoria_operario_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_categoria_operario_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Estados_Obra(
    id_estado INT AUTO_INCREMENT,
    descripcion VARCHAR(300) NOT NULL,
    color VARCHAR(20),
    orden_visualizacion INT NOT NULL DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_estado),
    UNIQUE uk_estado_obra_descripcion(descripcion),
    INDEX idx_estado_obra_orden(orden_visualizacion),
    INDEX idx_estado_obra_activo(activo),
    INDEX idx_estado_obra_usuario_creacion(usuario_creacion),
    INDEX idx_estado_obra_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_estado_obra_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_estado_obra_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Estados_Factura(
    id_estado INT AUTO_INCREMENT,
    descripcion VARCHAR(300) NOT NULL,
    color VARCHAR(20),
    orden_visualizacion INT NOT NULL DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_estado),
    UNIQUE uk_estado_factura_descripcion(descripcion),
    INDEX idx_estado_factura_orden(orden_visualizacion),
    INDEX idx_estado_factura_activo(activo),
    INDEX idx_estado_factura_usuario_creacion(usuario_creacion),
    INDEX idx_estado_factura_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_estado_factura_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_estado_factura_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Estados_Tarea(
    id_estado INT AUTO_INCREMENT,
    descripcion VARCHAR(300) NOT NULL,
    color VARCHAR(20),
    orden_visualizacion INT NOT NULL DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_estado),
    UNIQUE uk_estado_tarea_descripcion(descripcion),
    INDEX idx_estado_tarea_orden(orden_visualizacion),
    INDEX idx_estado_tarea_activo(activo),
    INDEX idx_estado_tarea_usuario_creacion(usuario_creacion),
    INDEX idx_estado_tarea_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_estado_tarea_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_estado_tarea_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Estados_Incidencia(
    id_estado INT AUTO_INCREMENT,
    descripcion VARCHAR(300) NOT NULL,
    color VARCHAR(20),
    orden_visualizacion INT NOT NULL DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_estado),
    UNIQUE uk_estado_incidencia_descripcion(descripcion),
    INDEX idx_estado_incidencia_orden(orden_visualizacion),
    INDEX idx_estado_incidencia_activo(activo),
    INDEX idx_estado_incidencia_usuario_creacion(usuario_creacion),
    INDEX idx_estado_incidencia_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_estado_incidencia_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_estado_incidencia_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Prioridades(
    id_prioridad INT AUTO_INCREMENT,
    descripcion VARCHAR(300) NOT NULL,
    color VARCHAR(20),
    orden_visualizacion INT NOT NULL DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_prioridad),
    UNIQUE uk_prioridad_descripcion(descripcion),
    INDEX idx_prioridad_orden(orden_visualizacion),
    INDEX idx_prioridad_activo(activo),
    INDEX idx_prioridad_usuario_creacion(usuario_creacion),
    INDEX idx_prioridad_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_prioridad_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_prioridad_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Series_Documentos(
    id_serie INT AUTO_INCREMENT,
    tipo_documento VARCHAR(40) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    prefijo VARCHAR(20) NOT NULL,
    siguiente_numero INT NOT NULL DEFAULT 1,
    longitud INT NOT NULL DEFAULT 6,
    ejercicio INT NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_serie),
    UNIQUE uk_serie_documento(tipo_documento,ejercicio,nombre),
    INDEX idx_serie_activo(activo),
    INDEX idx_serie_usuario_creacion(usuario_creacion),
    INDEX idx_serie_usuario_modificacion(usuario_modificacion),

    CONSTRAINT chk_serie_tipo_documento
        CHECK(tipo_documento IN (
            'OBRA',
            'PRESUPUESTO',
            'PEDIDO_PROVEEDOR',
            'ALBARAN_CLIENTE',
            'ALBARAN_PROVEEDOR',
            'FACTURA_CLIENTE',
            'FACTURA_PROVEEDOR'
        )),

    CONSTRAINT fk_serie_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_serie_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;


/**************************************************************************
* DIRECCIONES Y CONTACTOS
**************************************************************************/

CREATE TABLE Direcciones(
    id_direccion INT AUTO_INCREMENT,
    id_entidad INT NOT NULL,
    tipo_direccion VARCHAR(50) NOT NULL,
    direccion VARCHAR(200) NOT NULL,
    codigo_postal VARCHAR(10),
    poblacion VARCHAR(100),
    provincia VARCHAR(100),
    pais VARCHAR(100) NOT NULL DEFAULT 'Espana',
    telefono VARCHAR(20),
    observaciones TEXT,
    principal BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_direccion),
    INDEX idx_direccion_entidad(id_entidad),
    INDEX idx_direccion_tipo(tipo_direccion),
    INDEX idx_direccion_principal(principal),
    INDEX idx_direccion_codigo_postal(codigo_postal),
    INDEX idx_direccion_poblacion(poblacion),
    INDEX idx_direccion_usuario_creacion(usuario_creacion),
    INDEX idx_direccion_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_direccion_entidad
        FOREIGN KEY(id_entidad)
        REFERENCES Entidades(id_entidad)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_direccion_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_direccion_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Contactos(
    id_contacto INT AUTO_INCREMENT,
    id_entidad INT NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    apellidos VARCHAR(150),
    cargo VARCHAR(100),
    departamento VARCHAR(100),
    telefono VARCHAR(20),
    movil VARCHAR(20),
    email VARCHAR(150),
    observaciones TEXT,
    principal BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_contacto),
    INDEX idx_contacto_entidad(id_entidad),
    INDEX idx_contacto_nombre(nombre,apellidos),
    INDEX idx_contacto_email(email),
    INDEX idx_contacto_principal(principal),
    INDEX idx_contacto_usuario_creacion(usuario_creacion),
    INDEX idx_contacto_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_contacto_entidad
        FOREIGN KEY(id_entidad)
        REFERENCES Entidades(id_entidad)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_contacto_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_contacto_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;


/**************************************************************************
* CLIENTES, PROVEEDORES Y OPERARIOS
**************************************************************************/

CREATE TABLE Clientes(
    id_cliente INT AUTO_INCREMENT,
    id_entidad INT NOT NULL,
    codigo_cliente VARCHAR(20) NOT NULL,
    descuento DECIMAL(5,2) NOT NULL DEFAULT 0,
    limite_credito DECIMAL(12,2),
    id_zona INT,
    id_forma_pago INT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_cliente),
    UNIQUE uk_cliente_entidad(id_entidad),
    UNIQUE uk_cliente_codigo(codigo_cliente),
    INDEX idx_cliente_zona(id_zona),
    INDEX idx_cliente_forma_pago(id_forma_pago),
    INDEX idx_cliente_usuario_creacion(usuario_creacion),
    INDEX idx_cliente_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_cliente_entidad
        FOREIGN KEY(id_entidad)
        REFERENCES Entidades(id_entidad)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_cliente_zona
        FOREIGN KEY(id_zona)
        REFERENCES Zonas(id_zona)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_cliente_forma_pago
        FOREIGN KEY(id_forma_pago)
        REFERENCES Formas_Pago(id_forma_pago)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_cliente_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_cliente_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Proveedores(
    id_proveedor INT AUTO_INCREMENT,
    id_entidad INT NOT NULL,
    codigo_proveedor VARCHAR(20) NOT NULL,
    tipo_proveedor VARCHAR(20) NOT NULL DEFAULT 'EMPRESA',
    iban VARCHAR(34),
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_proveedor),
    UNIQUE uk_proveedor_entidad(id_entidad),
    UNIQUE uk_proveedor_codigo(codigo_proveedor),
    INDEX idx_proveedor_tipo(tipo_proveedor),
    INDEX idx_proveedor_usuario_creacion(usuario_creacion),
    INDEX idx_proveedor_usuario_modificacion(usuario_modificacion),

    CONSTRAINT chk_proveedor_tipo
        CHECK(tipo_proveedor IN ('EMPRESA','AUTONOMO')),

    CONSTRAINT fk_proveedor_entidad
        FOREIGN KEY(id_entidad)
        REFERENCES Entidades(id_entidad)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_proveedor_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_proveedor_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Operarios(
    id_operario INT AUTO_INCREMENT,
    id_entidad INT NOT NULL,
    codigo_operario VARCHAR(20) NOT NULL,
    fecha_alta DATE NOT NULL DEFAULT (CURRENT_DATE),
    fecha_baja DATE,
    nss VARCHAR(30),
    horas_semanales DECIMAL(5,2),
    coste_hora_real DECIMAL(10,2),
    id_categoria INT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_operario),
    UNIQUE uk_operario_entidad(id_entidad),
    UNIQUE uk_operario_codigo(codigo_operario),
    UNIQUE uk_operario_nss(nss),
    INDEX idx_operario_categoria(id_categoria),
    INDEX idx_operario_fecha_alta(fecha_alta),
    INDEX idx_operario_usuario_creacion(usuario_creacion),
    INDEX idx_operario_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_operario_entidad
        FOREIGN KEY(id_entidad)
        REFERENCES Entidades(id_entidad)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_operario_categoria
        FOREIGN KEY(id_categoria)
        REFERENCES Categorias_Operarios(id_categoria)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_operario_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_operario_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;


/**************************************************************************
* VEHICULOS, ARTICULOS Y STOCK
**************************************************************************/

CREATE TABLE Vehiculos(
    id_vehiculo INT AUTO_INCREMENT,
    matricula VARCHAR(15) NOT NULL,
    marca VARCHAR(100) NOT NULL,
    modelo VARCHAR(100) NOT NULL,
    version VARCHAR(100),
    combustible VARCHAR(30),
    color VARCHAR(30),
    fecha_matriculacion DATE,
    kilometros INT NOT NULL DEFAULT 0,
    fecha_itv DATE,
    fecha_seguro DATE,
    numero_poliza VARCHAR(100),
    compania_seguro VARCHAR(100),
    observaciones TEXT,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_vehiculo),
    UNIQUE uk_vehiculo_matricula(matricula),
    INDEX idx_vehiculo_marca(marca),
    INDEX idx_vehiculo_modelo(modelo),
    INDEX idx_vehiculo_fecha_itv(fecha_itv),
    INDEX idx_vehiculo_usuario_creacion(usuario_creacion),
    INDEX idx_vehiculo_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_vehiculo_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_vehiculo_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Articulos(
    referencia VARCHAR(50) NOT NULL,
    codigo_barras VARCHAR(50),
    descripcion VARCHAR(300) NOT NULL,
    marca VARCHAR(100),
    modelo VARCHAR(100),
    precio_coste DECIMAL(10,2) NOT NULL DEFAULT 0,
    precio_venta DECIMAL(10,2) NOT NULL DEFAULT 0,
    stock_minimo DECIMAL(10,2) NOT NULL DEFAULT 0,
    stock_maximo DECIMAL(10,2) NOT NULL DEFAULT 0,
    peso DECIMAL(10,2),
    ubicacion VARCHAR(100),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    id_familia INT,
    id_iva INT,
    id_unidad INT,
    id_proveedor INT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(referencia),
    UNIQUE uk_articulo_codigo_barras(codigo_barras),
    INDEX idx_articulo_descripcion(descripcion),
    INDEX idx_articulo_familia(id_familia),
    INDEX idx_articulo_iva(id_iva),
    INDEX idx_articulo_unidad(id_unidad),
    INDEX idx_articulo_proveedor(id_proveedor),
    INDEX idx_articulo_usuario_creacion(usuario_creacion),
    INDEX idx_articulo_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_articulo_familia
        FOREIGN KEY(id_familia)
        REFERENCES Familias(id_familia)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_articulo_iva
        FOREIGN KEY(id_iva)
        REFERENCES Tipos_IVA(id_iva)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_articulo_unidad
        FOREIGN KEY(id_unidad)
        REFERENCES Unidades_Medida(id_unidad)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_articulo_proveedor
        FOREIGN KEY(id_proveedor)
        REFERENCES Proveedores(id_proveedor)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_articulo_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_articulo_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Movimientos_Stock(
    id_movimiento INT AUTO_INCREMENT,
    referencia_articulo VARCHAR(50) NOT NULL,
    id_almacen INT NOT NULL,
    fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tipo_movimiento VARCHAR(20) NOT NULL,
    origen VARCHAR(100),
    destino VARCHAR(100),
    documento VARCHAR(50),
    cantidad DECIMAL(10,2) NOT NULL,
    precio_unitario DECIMAL(10,2),
    observaciones TEXT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_movimiento),
    INDEX idx_stock_fecha(fecha),
    INDEX idx_stock_articulo(referencia_articulo),
    INDEX idx_stock_almacen(id_almacen),
    INDEX idx_stock_documento(documento),
    INDEX idx_stock_usuario_creacion(usuario_creacion),
    INDEX idx_stock_usuario_modificacion(usuario_modificacion),

    CONSTRAINT chk_stock_tipo_movimiento
        CHECK(tipo_movimiento IN ('ENTRADA','SALIDA','AJUSTE','TRASPASO')),

    CONSTRAINT fk_stock_articulo
        FOREIGN KEY(referencia_articulo)
        REFERENCES Articulos(referencia)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_stock_almacen
        FOREIGN KEY(id_almacen)
        REFERENCES Almacenes(id_almacen)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_stock_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_stock_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;


/**************************************************************************
* OBRAS
**************************************************************************/

CREATE TABLE Obras(
    id_obra INT AUTO_INCREMENT,
    id_serie INT NOT NULL,
    codigo_obra VARCHAR(20) NOT NULL,
    titulo VARCHAR(200) NOT NULL,
    descripcion TEXT,
    presupuesto DECIMAL(12,2) NOT NULL DEFAULT 0,
    fecha_inicio DATE,
    fecha_fin_prevista DATE,
    fecha_fin_real DATE,
    id_estado INT NOT NULL DEFAULT 1,
    observaciones TEXT,
    id_cliente INT NOT NULL,
    id_direccion INT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_obra),
    UNIQUE uk_obra_codigo(codigo_obra),
    INDEX idx_obra_serie(id_serie),
    INDEX idx_obra_cliente(id_cliente),
    INDEX idx_obra_direccion(id_direccion),
    INDEX idx_obra_estado(id_estado),
    INDEX idx_obra_fecha_inicio(fecha_inicio),
    INDEX idx_obra_usuario_creacion(usuario_creacion),
    INDEX idx_obra_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_obra_serie
        FOREIGN KEY(id_serie)
        REFERENCES Series_Documentos(id_serie)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_obra_estado
        FOREIGN KEY(id_estado)
        REFERENCES Estados_Obra(id_estado)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_obra_cliente
        FOREIGN KEY(id_cliente)
        REFERENCES Clientes(id_cliente)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_obra_direccion
        FOREIGN KEY(id_direccion)
        REFERENCES Direcciones(id_direccion)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_obra_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_obra_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Obra_Contacto(
    id_obra_contacto INT AUTO_INCREMENT,
    id_obra INT NOT NULL,
    id_contacto INT NOT NULL,
    rol_en_obra VARCHAR(80) NOT NULL,
    observaciones TEXT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_obra_contacto),
    UNIQUE uk_obra_contacto_rol(id_obra,id_contacto,rol_en_obra),
    INDEX idx_obra_contacto_contacto(id_contacto),
    INDEX idx_obra_contacto_usuario_creacion(usuario_creacion),
    INDEX idx_obra_contacto_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_obra_contacto_obra
        FOREIGN KEY(id_obra)
        REFERENCES Obras(id_obra)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_obra_contacto_contacto
        FOREIGN KEY(id_contacto)
        REFERENCES Contactos(id_contacto)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_obra_contacto_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_obra_contacto_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Partes_Mano_Obra(
    id_parte INT AUTO_INCREMENT,
    id_obra INT NOT NULL,
    id_operario INT NOT NULL,
    fecha DATE NOT NULL,
    hora_inicio TIME,
    hora_fin TIME,
    horas_trabajadas DECIMAL(5,2) NOT NULL,
    coste_hora DECIMAL(10,2) NOT NULL,
    coste_total DECIMAL(10,2) NOT NULL,
    observaciones TEXT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_parte),
    INDEX idx_parte_obra(id_obra),
    INDEX idx_parte_operario(id_operario),
    INDEX idx_parte_fecha(fecha),
    INDEX idx_parte_usuario_creacion(usuario_creacion),
    INDEX idx_parte_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_parte_obra
        FOREIGN KEY(id_obra)
        REFERENCES Obras(id_obra)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_parte_operario
        FOREIGN KEY(id_operario)
        REFERENCES Operarios(id_operario)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_parte_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_parte_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Materiales_Por_Obra(
    id_material INT AUTO_INCREMENT,
    id_obra INT NOT NULL,
    referencia_articulo VARCHAR(50) NOT NULL,
    id_almacen INT,
    fecha DATE NOT NULL,
    cantidad DECIMAL(10,2) NOT NULL,
    coste_unitario DECIMAL(10,2) NOT NULL,
    importe DECIMAL(12,2) NOT NULL,
    observaciones TEXT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_material),
    INDEX idx_material_obra(id_obra),
    INDEX idx_material_articulo(referencia_articulo),
    INDEX idx_material_almacen(id_almacen),
    INDEX idx_material_fecha(fecha),
    INDEX idx_material_usuario_creacion(usuario_creacion),
    INDEX idx_material_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_material_obra
        FOREIGN KEY(id_obra)
        REFERENCES Obras(id_obra)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_material_articulo
        FOREIGN KEY(referencia_articulo)
        REFERENCES Articulos(referencia)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_material_almacen
        FOREIGN KEY(id_almacen)
        REFERENCES Almacenes(id_almacen)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_material_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_material_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Documentos(
    id_documento INT AUTO_INCREMENT,
    id_obra INT NOT NULL,
    nombre VARCHAR(200) NOT NULL,
    ruta VARCHAR(500) NOT NULL,
    tipo VARCHAR(50),
    fecha_subida DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observaciones TEXT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_documento),
    INDEX idx_documento_obra(id_obra),
    INDEX idx_documento_fecha(fecha_subida),
    INDEX idx_documento_tipo(tipo),
    INDEX idx_documento_usuario_creacion(usuario_creacion),
    INDEX idx_documento_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_documento_obra
        FOREIGN KEY(id_obra)
        REFERENCES Obras(id_obra)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_documento_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_documento_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Incidencias(
    id_incidencia INT AUTO_INCREMENT,
    id_obra INT NOT NULL,
    id_operario INT,
    fecha DATE NOT NULL,
    descripcion TEXT NOT NULL,
    id_estado INT NOT NULL DEFAULT 1,
    id_prioridad INT NOT NULL DEFAULT 2,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_incidencia),
    INDEX idx_incidencia_obra(id_obra),
    INDEX idx_incidencia_operario(id_operario),
    INDEX idx_incidencia_estado(id_estado),
    INDEX idx_incidencia_prioridad(id_prioridad),
    INDEX idx_incidencia_fecha(fecha),
    INDEX idx_incidencia_usuario_creacion(usuario_creacion),
    INDEX idx_incidencia_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_incidencia_obra
        FOREIGN KEY(id_obra)
        REFERENCES Obras(id_obra)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_incidencia_operario
        FOREIGN KEY(id_operario)
        REFERENCES Operarios(id_operario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_incidencia_estado
        FOREIGN KEY(id_estado)
        REFERENCES Estados_Incidencia(id_estado)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_incidencia_prioridad
        FOREIGN KEY(id_prioridad)
        REFERENCES Prioridades(id_prioridad)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_incidencia_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_incidencia_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;


/**************************************************************************
* DOCUMENTOS COMERCIALES
**************************************************************************/

CREATE TABLE Presupuestos(
    id_presupuesto INT AUTO_INCREMENT,
    numero VARCHAR(30) NOT NULL,
    id_serie INT NOT NULL,
    fecha DATE NOT NULL,
    validez DATE,
    observaciones TEXT,
    subtotal DECIMAL(12,2) NOT NULL DEFAULT 0,
    descuento DECIMAL(12,2) NOT NULL DEFAULT 0,
    impuestos DECIMAL(12,2) NOT NULL DEFAULT 0,
    total DECIMAL(12,2) NOT NULL DEFAULT 0,
    id_estado INT NOT NULL DEFAULT 1,
    id_cliente INT NOT NULL,
    id_obra INT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_presupuesto),
    UNIQUE uk_presupuesto_serie_numero(id_serie,numero),
    INDEX idx_presupuesto_numero(numero),
    INDEX idx_presupuesto_fecha(fecha),
    INDEX idx_presupuesto_cliente(id_cliente),
    INDEX idx_presupuesto_obra(id_obra),
    INDEX idx_presupuesto_estado(id_estado),
    INDEX idx_presupuesto_usuario_creacion(usuario_creacion),
    INDEX idx_presupuesto_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_presupuesto_serie
        FOREIGN KEY(id_serie)
        REFERENCES Series_Documentos(id_serie)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_presupuesto_estado
        FOREIGN KEY(id_estado)
        REFERENCES Estados_Factura(id_estado)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_presupuesto_cliente
        FOREIGN KEY(id_cliente)
        REFERENCES Clientes(id_cliente)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_presupuesto_obra
        FOREIGN KEY(id_obra)
        REFERENCES Obras(id_obra)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_presupuesto_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_presupuesto_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Lineas_Presupuesto(
    id_linea INT AUTO_INCREMENT,
    id_presupuesto INT NOT NULL,
    referencia_articulo VARCHAR(50),
    descripcion VARCHAR(300) NOT NULL,
    cantidad DECIMAL(10,2) NOT NULL,
    precio DECIMAL(10,2) NOT NULL,
    descuento DECIMAL(5,2) NOT NULL DEFAULT 0,
    iva DECIMAL(5,2) NOT NULL DEFAULT 21,
    importe DECIMAL(12,2) NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_linea),
    INDEX idx_lp_presupuesto(id_presupuesto),
    INDEX idx_lp_articulo(referencia_articulo),
    INDEX idx_lp_usuario_creacion(usuario_creacion),
    INDEX idx_lp_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_lp_presupuesto
        FOREIGN KEY(id_presupuesto)
        REFERENCES Presupuestos(id_presupuesto)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_lp_articulo
        FOREIGN KEY(referencia_articulo)
        REFERENCES Articulos(referencia)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_lp_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_lp_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Pedidos_Proveedor(
    id_pedido INT AUTO_INCREMENT,
    numero VARCHAR(30) NOT NULL,
    id_serie INT NOT NULL,
    fecha DATE NOT NULL,
    observaciones TEXT,
    subtotal DECIMAL(12,2) NOT NULL DEFAULT 0,
    descuento DECIMAL(12,2) NOT NULL DEFAULT 0,
    impuestos DECIMAL(12,2) NOT NULL DEFAULT 0,
    total DECIMAL(12,2) NOT NULL DEFAULT 0,
    id_estado INT NOT NULL DEFAULT 1,
    id_proveedor INT NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_pedido),
    UNIQUE uk_pedido_serie_numero(id_serie,numero),
    INDEX idx_pedido_numero(numero),
    INDEX idx_pedido_fecha(fecha),
    INDEX idx_pedido_proveedor(id_proveedor),
    INDEX idx_pedido_estado(id_estado),
    INDEX idx_pedido_usuario_creacion(usuario_creacion),
    INDEX idx_pedido_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_pedido_serie
        FOREIGN KEY(id_serie)
        REFERENCES Series_Documentos(id_serie)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_pedido_estado
        FOREIGN KEY(id_estado)
        REFERENCES Estados_Factura(id_estado)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_pedido_proveedor
        FOREIGN KEY(id_proveedor)
        REFERENCES Proveedores(id_proveedor)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_pedido_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_pedido_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Lineas_Pedido(
    id_linea INT AUTO_INCREMENT,
    id_pedido INT NOT NULL,
    referencia_articulo VARCHAR(50) NOT NULL,
    descripcion VARCHAR(300),
    cantidad DECIMAL(10,2) NOT NULL,
    precio_coste DECIMAL(10,2) NOT NULL,
    descuento DECIMAL(5,2) NOT NULL DEFAULT 0,
    iva DECIMAL(5,2) NOT NULL DEFAULT 21,
    importe DECIMAL(12,2) NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_linea),
    INDEX idx_linea_pedido(id_pedido),
    INDEX idx_linea_pedido_articulo(referencia_articulo),
    INDEX idx_linea_pedido_usuario_creacion(usuario_creacion),
    INDEX idx_linea_pedido_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_linea_pedido
        FOREIGN KEY(id_pedido)
        REFERENCES Pedidos_Proveedor(id_pedido)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_linea_pedido_articulo
        FOREIGN KEY(referencia_articulo)
        REFERENCES Articulos(referencia)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_linea_pedido_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_linea_pedido_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Albaranes_Cliente(
    id_albaran INT AUTO_INCREMENT,
    numero VARCHAR(30) NOT NULL,
    id_serie INT NOT NULL,
    fecha DATE NOT NULL,
    observaciones TEXT,
    subtotal DECIMAL(12,2) NOT NULL DEFAULT 0,
    descuento DECIMAL(12,2) NOT NULL DEFAULT 0,
    impuestos DECIMAL(12,2) NOT NULL DEFAULT 0,
    total DECIMAL(12,2) NOT NULL DEFAULT 0,
    id_estado INT NOT NULL DEFAULT 1,
    id_cliente INT NOT NULL,
    id_obra INT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_albaran),
    UNIQUE uk_albaran_cliente_serie_numero(id_serie,numero),
    INDEX idx_albaran_cliente_numero(numero),
    INDEX idx_albaran_cliente_fecha(fecha),
    INDEX idx_albaran_cliente_cliente(id_cliente),
    INDEX idx_albaran_cliente_obra(id_obra),
    INDEX idx_albaran_cliente_estado(id_estado),
    INDEX idx_albaran_cliente_usuario_creacion(usuario_creacion),
    INDEX idx_albaran_cliente_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_albaran_cliente_serie
        FOREIGN KEY(id_serie)
        REFERENCES Series_Documentos(id_serie)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_albaran_cliente_estado
        FOREIGN KEY(id_estado)
        REFERENCES Estados_Factura(id_estado)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_albaran_cliente_cliente
        FOREIGN KEY(id_cliente)
        REFERENCES Clientes(id_cliente)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_albaran_cliente_obra
        FOREIGN KEY(id_obra)
        REFERENCES Obras(id_obra)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_albaran_cliente_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_albaran_cliente_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Lineas_Albaran_Cliente(
    id_linea INT AUTO_INCREMENT,
    id_albaran INT NOT NULL,
    referencia_articulo VARCHAR(50),
    descripcion VARCHAR(300),
    cantidad DECIMAL(10,2) NOT NULL,
    precio DECIMAL(10,2) NOT NULL,
    descuento DECIMAL(5,2) NOT NULL DEFAULT 0,
    iva DECIMAL(5,2) NOT NULL DEFAULT 21,
    importe DECIMAL(12,2),
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_linea),
    INDEX idx_lac_albaran(id_albaran),
    INDEX idx_lac_articulo(referencia_articulo),
    INDEX idx_lac_usuario_creacion(usuario_creacion),
    INDEX idx_lac_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_lac_albaran
        FOREIGN KEY(id_albaran)
        REFERENCES Albaranes_Cliente(id_albaran)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_lac_articulo
        FOREIGN KEY(referencia_articulo)
        REFERENCES Articulos(referencia)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_lac_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_lac_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Albaranes_Proveedor(
    id_albaran INT AUTO_INCREMENT,
    numero VARCHAR(30) NOT NULL,
    id_serie INT NOT NULL,
    fecha DATE NOT NULL,
    observaciones TEXT,
    subtotal DECIMAL(12,2) NOT NULL DEFAULT 0,
    descuento DECIMAL(12,2) NOT NULL DEFAULT 0,
    impuestos DECIMAL(12,2) NOT NULL DEFAULT 0,
    total DECIMAL(12,2) NOT NULL DEFAULT 0,
    id_estado INT NOT NULL DEFAULT 1,
    id_proveedor INT NOT NULL,
    id_pedido INT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_albaran),
    UNIQUE uk_albaran_proveedor_serie_numero(id_serie,numero),
    INDEX idx_albaran_proveedor_numero(numero),
    INDEX idx_albaran_proveedor_fecha(fecha),
    INDEX idx_albaran_proveedor_proveedor(id_proveedor),
    INDEX idx_albaran_proveedor_pedido(id_pedido),
    INDEX idx_albaran_proveedor_estado(id_estado),
    INDEX idx_albaran_proveedor_usuario_creacion(usuario_creacion),
    INDEX idx_albaran_proveedor_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_albaran_proveedor_serie
        FOREIGN KEY(id_serie)
        REFERENCES Series_Documentos(id_serie)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_albaran_proveedor_estado
        FOREIGN KEY(id_estado)
        REFERENCES Estados_Factura(id_estado)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_albaran_proveedor_proveedor
        FOREIGN KEY(id_proveedor)
        REFERENCES Proveedores(id_proveedor)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_albaran_proveedor_pedido
        FOREIGN KEY(id_pedido)
        REFERENCES Pedidos_Proveedor(id_pedido)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_albaran_proveedor_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_albaran_proveedor_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Lineas_Albaran_Proveedor(
    id_linea INT AUTO_INCREMENT,
    id_albaran INT NOT NULL,
    referencia_articulo VARCHAR(50),
    descripcion VARCHAR(300),
    cantidad DECIMAL(10,2) NOT NULL,
    precio DECIMAL(10,2) NOT NULL,
    descuento DECIMAL(5,2) NOT NULL DEFAULT 0,
    iva DECIMAL(5,2) NOT NULL DEFAULT 21,
    importe DECIMAL(12,2),
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_linea),
    INDEX idx_lap_albaran(id_albaran),
    INDEX idx_lap_articulo(referencia_articulo),
    INDEX idx_lap_usuario_creacion(usuario_creacion),
    INDEX idx_lap_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_lap_albaran
        FOREIGN KEY(id_albaran)
        REFERENCES Albaranes_Proveedor(id_albaran)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_lap_articulo
        FOREIGN KEY(referencia_articulo)
        REFERENCES Articulos(referencia)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_lap_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_lap_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Facturas_Cliente(
    id_factura INT AUTO_INCREMENT,
    numero VARCHAR(30) NOT NULL,
    id_serie INT NOT NULL,
    fecha DATE NOT NULL,
    fecha_vencimiento DATE,
    observaciones TEXT,
    subtotal DECIMAL(12,2) NOT NULL DEFAULT 0,
    descuento DECIMAL(12,2) NOT NULL DEFAULT 0,
    impuestos DECIMAL(12,2) NOT NULL DEFAULT 0,
    total DECIMAL(12,2) NOT NULL DEFAULT 0,
    id_estado INT NOT NULL DEFAULT 1,
    id_cliente INT NOT NULL,
    id_obra INT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_factura),
    UNIQUE uk_fc_serie_numero(id_serie,numero),
    INDEX idx_fc_numero(numero),
    INDEX idx_fc_fecha(fecha),
    INDEX idx_fc_cliente(id_cliente),
    INDEX idx_fc_obra(id_obra),
    INDEX idx_fc_estado(id_estado),
    INDEX idx_fc_usuario_creacion(usuario_creacion),
    INDEX idx_fc_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_fc_serie
        FOREIGN KEY(id_serie)
        REFERENCES Series_Documentos(id_serie)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_fc_estado
        FOREIGN KEY(id_estado)
        REFERENCES Estados_Factura(id_estado)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_fc_cliente
        FOREIGN KEY(id_cliente)
        REFERENCES Clientes(id_cliente)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_fc_obra
        FOREIGN KEY(id_obra)
        REFERENCES Obras(id_obra)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_fc_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_fc_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Lineas_Factura_Cliente(
    id_linea INT AUTO_INCREMENT,
    id_factura INT NOT NULL,
    referencia_articulo VARCHAR(50),
    descripcion VARCHAR(300),
    cantidad DECIMAL(10,2),
    precio DECIMAL(10,2),
    descuento DECIMAL(5,2),
    iva DECIMAL(5,2),
    importe DECIMAL(12,2),
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_linea),
    INDEX idx_lfc_factura(id_factura),
    INDEX idx_lfc_articulo(referencia_articulo),
    INDEX idx_lfc_usuario_creacion(usuario_creacion),
    INDEX idx_lfc_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_lfc_factura
        FOREIGN KEY(id_factura)
        REFERENCES Facturas_Cliente(id_factura)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_lfc_articulo
        FOREIGN KEY(referencia_articulo)
        REFERENCES Articulos(referencia)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_lfc_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_lfc_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Facturas_Proveedor(
    id_factura INT AUTO_INCREMENT,
    numero VARCHAR(30) NOT NULL,
    id_serie INT NOT NULL,
    fecha DATE NOT NULL,
    fecha_vencimiento DATE,
    observaciones TEXT,
    subtotal DECIMAL(12,2) NOT NULL DEFAULT 0,
    descuento DECIMAL(12,2) NOT NULL DEFAULT 0,
    impuestos DECIMAL(12,2) NOT NULL DEFAULT 0,
    total DECIMAL(12,2) NOT NULL DEFAULT 0,
    id_estado INT NOT NULL DEFAULT 1,
    id_proveedor INT NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_factura),
    UNIQUE uk_fp_serie_numero(id_serie,numero),
    INDEX idx_fp_numero(numero),
    INDEX idx_fp_fecha(fecha),
    INDEX idx_fp_proveedor(id_proveedor),
    INDEX idx_fp_estado(id_estado),
    INDEX idx_fp_usuario_creacion(usuario_creacion),
    INDEX idx_fp_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_fp_serie
        FOREIGN KEY(id_serie)
        REFERENCES Series_Documentos(id_serie)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_fp_estado
        FOREIGN KEY(id_estado)
        REFERENCES Estados_Factura(id_estado)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_fp_proveedor
        FOREIGN KEY(id_proveedor)
        REFERENCES Proveedores(id_proveedor)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_fp_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_fp_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Lineas_Factura_Proveedor(
    id_linea INT AUTO_INCREMENT,
    id_factura INT NOT NULL,
    referencia_articulo VARCHAR(50),
    descripcion VARCHAR(300),
    cantidad DECIMAL(10,2),
    precio DECIMAL(10,2),
    descuento DECIMAL(5,2),
    iva DECIMAL(5,2),
    importe DECIMAL(12,2),
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_linea),
    INDEX idx_lfp_factura(id_factura),
    INDEX idx_lfp_articulo(referencia_articulo),
    INDEX idx_lfp_usuario_creacion(usuario_creacion),
    INDEX idx_lfp_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_lfp_factura
        FOREIGN KEY(id_factura)
        REFERENCES Facturas_Proveedor(id_factura)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_lfp_articulo
        FOREIGN KEY(referencia_articulo)
        REFERENCES Articulos(referencia)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_lfp_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_lfp_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;


/**************************************************************************
* COBROS, PAGOS Y CALENDARIO
**************************************************************************/

CREATE TABLE Cobros(
    id_cobro INT AUTO_INCREMENT,
    id_factura INT NOT NULL,
    fecha DATE NOT NULL,
    importe DECIMAL(12,2) NOT NULL,
    metodo_pago VARCHAR(20),
    numero_operacion VARCHAR(100),
    observaciones TEXT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_cobro),
    INDEX idx_cobro_factura(id_factura),
    INDEX idx_cobro_fecha(fecha),
    INDEX idx_cobro_numero_operacion(numero_operacion),
    INDEX idx_cobro_usuario_creacion(usuario_creacion),
    INDEX idx_cobro_usuario_modificacion(usuario_modificacion),

    CONSTRAINT chk_cobro_metodo_pago
        CHECK(metodo_pago IS NULL OR metodo_pago IN ('EFECTIVO','TRANSFERENCIA','TARJETA','BIZUM','CHEQUE')),

    CONSTRAINT fk_cobro_factura
        FOREIGN KEY(id_factura)
        REFERENCES Facturas_Cliente(id_factura)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_cobro_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_cobro_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Pagos(
    id_pago INT AUTO_INCREMENT,
    id_factura INT NOT NULL,
    fecha DATE NOT NULL,
    importe DECIMAL(12,2) NOT NULL,
    metodo_pago VARCHAR(20),
    numero_operacion VARCHAR(100),
    observaciones TEXT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_pago),
    INDEX idx_pago_factura(id_factura),
    INDEX idx_pago_fecha(fecha),
    INDEX idx_pago_numero_operacion(numero_operacion),
    INDEX idx_pago_usuario_creacion(usuario_creacion),
    INDEX idx_pago_usuario_modificacion(usuario_modificacion),

    CONSTRAINT chk_pago_metodo_pago
        CHECK(metodo_pago IS NULL OR metodo_pago IN ('EFECTIVO','TRANSFERENCIA','TARJETA','BIZUM','CHEQUE')),

    CONSTRAINT fk_pago_factura
        FOREIGN KEY(id_factura)
        REFERENCES Facturas_Proveedor(id_factura)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_pago_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_pago_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Eventos(
    id_evento INT AUTO_INCREMENT,
    titulo VARCHAR(150) NOT NULL,
    descripcion TEXT,
    fecha_inicio DATETIME NOT NULL,
    fecha_fin DATETIME,
    color VARCHAR(20),
    tipo_evento VARCHAR(30) NOT NULL DEFAULT 'OBRA',
    id_obra INT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_evento),
    INDEX idx_evento_fecha_inicio(fecha_inicio),
    INDEX idx_evento_obra(id_obra),
    INDEX idx_evento_tipo(tipo_evento),
    INDEX idx_evento_usuario_creacion(usuario_creacion),
    INDEX idx_evento_usuario_modificacion(usuario_modificacion),

    CONSTRAINT chk_evento_tipo
        CHECK(tipo_evento IN ('OBRA','REUNION','VACACIONES','AVISO','PERSONAL')),

    CONSTRAINT fk_evento_obra
        FOREIGN KEY(id_obra)
        REFERENCES Obras(id_obra)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_evento_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_evento_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Tareas(
    id_tarea INT AUTO_INCREMENT,
    titulo VARCHAR(150) NOT NULL,
    descripcion TEXT,
    id_prioridad INT NOT NULL DEFAULT 2,
    id_estado INT NOT NULL DEFAULT 1,
    fecha_limite DATE,
    fecha_finalizacion DATE,
    id_obra INT,
    id_operario INT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_tarea),
    INDEX idx_tarea_prioridad(id_prioridad),
    INDEX idx_tarea_estado(id_estado),
    INDEX idx_tarea_obra(id_obra),
    INDEX idx_tarea_operario(id_operario),
    INDEX idx_tarea_fecha_limite(fecha_limite),
    INDEX idx_tarea_usuario_creacion(usuario_creacion),
    INDEX idx_tarea_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_tarea_prioridad
        FOREIGN KEY(id_prioridad)
        REFERENCES Prioridades(id_prioridad)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_tarea_estado
        FOREIGN KEY(id_estado)
        REFERENCES Estados_Tarea(id_estado)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_tarea_obra
        FOREIGN KEY(id_obra)
        REFERENCES Obras(id_obra)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_tarea_operario
        FOREIGN KEY(id_operario)
        REFERENCES Operarios(id_operario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_tarea_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_tarea_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;


/**************************************************************************
* HISTORICOS, DOCUMENTOS GENERALES Y LOG
**************************************************************************/

CREATE TABLE Historial_Estados_Obra(
    id_historial INT AUTO_INCREMENT,
    id_obra INT NOT NULL,
    id_estado_anterior INT,
    id_estado_nuevo INT NOT NULL,
    fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_usuario INT,
    observaciones TEXT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_historial),
    INDEX idx_historial_obra(id_obra),
    INDEX idx_historial_estado_anterior(id_estado_anterior),
    INDEX idx_historial_estado_nuevo(id_estado_nuevo),
    INDEX idx_historial_fecha(fecha),
    INDEX idx_historial_usuario(id_usuario),
    INDEX idx_historial_usuario_creacion(usuario_creacion),
    INDEX idx_historial_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_historial_obra
        FOREIGN KEY(id_obra)
        REFERENCES Obras(id_obra)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_historial_estado_anterior
        FOREIGN KEY(id_estado_anterior)
        REFERENCES Estados_Obra(id_estado)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_historial_estado_nuevo
        FOREIGN KEY(id_estado_nuevo)
        REFERENCES Estados_Obra(id_estado)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_historial_usuario
        FOREIGN KEY(id_usuario)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_historial_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_historial_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Documentos_Generales(
    id_documento INT AUTO_INCREMENT,
    nombre VARCHAR(200) NOT NULL,
    descripcion TEXT,
    ruta VARCHAR(500) NOT NULL,
    tipo VARCHAR(50),
    fecha_subida DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_usuario INT,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    usuario_creacion INT,
    usuario_modificacion INT,

    PRIMARY KEY(id_documento),
    INDEX idx_doc_general_usuario(id_usuario),
    INDEX idx_doc_general_fecha(fecha_subida),
    INDEX idx_doc_general_tipo(tipo),
    INDEX idx_doc_general_usuario_creacion(usuario_creacion),
    INDEX idx_doc_general_usuario_modificacion(usuario_modificacion),

    CONSTRAINT fk_doc_general_usuario
        FOREIGN KEY(id_usuario)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_doc_general_usuario_creacion
        FOREIGN KEY(usuario_creacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_doc_general_usuario_modificacion
        FOREIGN KEY(usuario_modificacion)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;

CREATE TABLE Log(
    id_log INT AUTO_INCREMENT,
    fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_usuario INT,
    accion VARCHAR(150) NOT NULL,
    tabla_afectada VARCHAR(100),
    id_registro INT,
    descripcion TEXT,
    ip VARCHAR(45),

    PRIMARY KEY(id_log),
    INDEX idx_log_fecha(fecha),
    INDEX idx_log_usuario(id_usuario),
    INDEX idx_log_tabla(tabla_afectada),

    CONSTRAINT fk_log_usuario
        FOREIGN KEY(id_usuario)
        REFERENCES Usuarios(id_usuario)
        ON UPDATE CASCADE
        ON DELETE SET NULL
)ENGINE=InnoDB;


/**************************************************************************
* VISTAS
**************************************************************************/

CREATE VIEW vw_stock_actual AS
SELECT
    a.referencia,
    a.descripcion,
    COALESCE(SUM(
        CASE ms.tipo_movimiento
            WHEN 'ENTRADA' THEN ms.cantidad
            WHEN 'SALIDA' THEN -ms.cantidad
            WHEN 'AJUSTE' THEN ms.cantidad
            ELSE 0
        END
    ),0) AS stock_actual,
    a.stock_minimo,
    a.stock_maximo,
    f.descripcion AS familia
FROM Articulos a
LEFT JOIN Movimientos_Stock ms
    ON a.referencia=ms.referencia_articulo
LEFT JOIN Familias f
    ON a.id_familia=f.id_familia
GROUP BY
    a.referencia,
    a.descripcion,
    a.stock_minimo,
    a.stock_maximo,
    f.descripcion;

CREATE VIEW vw_facturacion_mensual AS
SELECT
    YEAR(fecha) AS anio,
    MONTH(fecha) AS mes,
    COUNT(*) AS total_facturas,
    SUM(total) AS facturacion
FROM Facturas_Cliente
GROUP BY
    YEAR(fecha),
    MONTH(fecha);

CREATE VIEW vw_facturas_pendientes AS
SELECT
    fc.numero,
    fc.fecha,
    fc.fecha_vencimiento,
    fc.total,
    ef.descripcion AS estado,
    fc.id_cliente
FROM Facturas_Cliente fc
JOIN Estados_Factura ef
    ON fc.id_estado=ef.id_estado
WHERE ef.descripcion<>'PAGADA';

CREATE VIEW vw_cobros_mes AS
SELECT
    YEAR(fecha) AS anio,
    MONTH(fecha) AS mes,
    SUM(importe) AS total
FROM Cobros
GROUP BY
    YEAR(fecha),
    MONTH(fecha);

CREATE VIEW vw_pagos_mes AS
SELECT
    YEAR(fecha) AS anio,
    MONTH(fecha) AS mes,
    SUM(importe) AS total
FROM Pagos
GROUP BY
    YEAR(fecha),
    MONTH(fecha);

CREATE VIEW vw_operarios_horas AS
SELECT
    o.id_operario,
    TRIM(CONCAT_WS(' ', e.nombre, e.apellidos)) AS nombre_completo,
    SUM(p.horas_trabajadas) AS horas,
    SUM(p.coste_total) AS coste
FROM Operarios o
JOIN Entidades e
    ON o.id_entidad=e.id_entidad
LEFT JOIN Partes_Mano_Obra p
    ON o.id_operario=p.id_operario
GROUP BY
    o.id_operario,
    e.nombre,
    e.apellidos;

CREATE VIEW vw_beneficio_obra AS
SELECT
    ob.id_obra,
    ob.titulo,
    IFNULL(SUM(fc.total),0) AS ingresos,
    IFNULL(SUM(pm.coste_total),0)+IFNULL(SUM(mp.importe),0) AS gastos,
    IFNULL(SUM(fc.total),0)-
    (IFNULL(SUM(pm.coste_total),0)+IFNULL(SUM(mp.importe),0)) AS beneficio
FROM Obras ob
LEFT JOIN Facturas_Cliente fc
    ON ob.id_obra=fc.id_obra
LEFT JOIN Partes_Mano_Obra pm
    ON ob.id_obra=pm.id_obra
LEFT JOIN Materiales_Por_Obra mp
    ON ob.id_obra=mp.id_obra
GROUP BY
    ob.id_obra,
    ob.titulo;

CREATE VIEW vw_top_clientes AS
SELECT
    c.id_cliente,
    COALESCE(e.nombre_comercial, e.razon_social, TRIM(CONCAT_WS(' ', e.nombre, e.apellidos))) AS nombre_comercial,
    SUM(f.total) AS total_facturado
FROM Clientes c
JOIN Entidades e
    ON c.id_entidad=e.id_entidad
JOIN Facturas_Cliente f
    ON c.id_cliente=f.id_cliente
GROUP BY
    c.id_cliente,
    e.nombre_comercial,
    e.razon_social,
    e.nombre,
    e.apellidos
ORDER BY
    total_facturado DESC;

CREATE VIEW vw_obras_activas AS
SELECT
    ob.*,
    eo.descripcion AS estado
FROM Obras ob
JOIN Estados_Obra eo
    ON ob.id_estado=eo.id_estado
WHERE eo.descripcion='EN_CURSO';


/**************************************************************************
* DATOS INICIALES
**************************************************************************/

INSERT INTO Roles(nombre,descripcion)
VALUES
('Administrador','Acceso completo al sistema'),
('Gerencia','Acceso a toda la gestion'),
('Administracion','Clientes, facturacion y cobros'),
('Compras','Pedidos y proveedores'),
('Almacen','Control de inventario'),
('Operario','Gestion de obras y partes de trabajo');

INSERT INTO Permisos(nombre,descripcion)
VALUES
('CLIENTES','Gestion de clientes'),
('PROVEEDORES','Gestion de proveedores'),
('OPERARIOS','Gestion de operarios'),
('ARTICULOS','Gestion de articulos'),
('ALMACEN','Gestion de almacenes'),
('OBRAS','Gestion de obras'),
('PRESUPUESTOS','Gestion de presupuestos'),
('PEDIDOS','Gestion de pedidos'),
('ALBARANES','Gestion de albaranes'),
('FACTURAS','Gestion de facturacion'),
('COBROS','Gestion de cobros'),
('PAGOS','Gestion de pagos'),
('VEHICULOS','Gestion de vehiculos'),
('USUARIOS','Administracion de usuarios'),
('CONFIGURACION','Configuracion del sistema'),
('ESTADISTICAS','Informes y estadisticas');

INSERT INTO Rol_Permiso(id_rol,id_permiso,nivel_acceso)
SELECT r.id_rol, p.id_permiso, 'ADMIN'
FROM Roles r
CROSS JOIN Permisos p
WHERE r.nombre='Administrador';

INSERT INTO Estados_Obra(descripcion,color,orden_visualizacion,activo)
VALUES
    ('PRESUPUESTO','#6b7280',1,TRUE),
    ('EN_CURSO','#2563eb',2,TRUE),
    ('FINALIZADA','#16a34a',3,TRUE),
    ('CANCELADA','#dc2626',4,TRUE);

INSERT INTO Estados_Factura(descripcion,color,orden_visualizacion,activo)
VALUES
    ('PENDIENTE','#f59e0b',1,TRUE),
    ('ACEPTADO','#16a34a',2,TRUE),
    ('RECHAZADO','#dc2626',3,TRUE),
    ('RECIBIDO','#2563eb',4,TRUE),
    ('FACTURADO','#7c3aed',5,TRUE),
    ('PAGADA','#16a34a',6,TRUE),
    ('PARCIAL','#0891b2',7,TRUE),
    ('ANULADA','#6b7280',8,TRUE),
    ('CANCELADA','#dc2626',9,TRUE);

INSERT INTO Estados_Tarea(descripcion,color,orden_visualizacion,activo)
VALUES
    ('PENDIENTE','#f59e0b',1,TRUE),
    ('EN_CURSO','#2563eb',2,TRUE),
    ('FINALIZADA','#16a34a',3,TRUE),
    ('CANCELADA','#dc2626',4,TRUE);

INSERT INTO Estados_Incidencia(descripcion,color,orden_visualizacion,activo)
VALUES
    ('ABIERTA','#dc2626',1,TRUE),
    ('EN_PROCESO','#f59e0b',2,TRUE),
    ('RESUELTA','#16a34a',3,TRUE),
    ('CERRADA','#6b7280',4,TRUE);

INSERT INTO Prioridades(descripcion,color,orden_visualizacion,activo)
VALUES
    ('BAJA','#22c55e',1,TRUE),
    ('MEDIA','#f59e0b',2,TRUE),
    ('ALTA','#f97316',3,TRUE),
    ('URGENTE','#dc2626',4,TRUE);

INSERT INTO Formas_Pago(descripcion,dias_vencimiento)
VALUES
    ('Contado',0),
    ('30 dias',30),
    ('60 dias',60),
    ('Transferencia',0),
    ('Tarjeta',0);

INSERT INTO Tipos_IVA(descripcion,porcentaje)
VALUES
    ('General',21),
    ('Reducido',10),
    ('Superreducido',4),
    ('Exento',0);

INSERT INTO Categorias_Operarios(descripcion,coste_hora_base)
VALUES
    ('Oficial 1',24.50),
    ('Oficial 2',20.00),
    ('Peon',16.00),
    ('Encargado',30.00);

INSERT INTO Unidades_Medida(descripcion,abreviatura)
VALUES
    ('Unidad','ud'),
    ('Metro','m'),
    ('Metro cuadrado','m2'),
    ('Metro cubico','m3'),
    ('Kilogramo','kg'),
    ('Litro','l');

INSERT INTO Series_Documentos(tipo_documento,nombre,prefijo,siguiente_numero,longitud,ejercicio,activo)
VALUES
    ('OBRA',CONCAT('Obras ',YEAR(CURDATE())),'OB-',1,5,YEAR(CURDATE()),TRUE),
    ('PRESUPUESTO',CONCAT('Presupuestos ',YEAR(CURDATE())),'PR-',1,5,YEAR(CURDATE()),TRUE),
    ('PEDIDO_PROVEEDOR',CONCAT('Pedidos proveedor ',YEAR(CURDATE())),'PP-',1,5,YEAR(CURDATE()),TRUE),
    ('ALBARAN_CLIENTE',CONCAT('Albaranes cliente ',YEAR(CURDATE())),'AC-',1,5,YEAR(CURDATE()),TRUE),
    ('ALBARAN_PROVEEDOR',CONCAT('Albaranes proveedor ',YEAR(CURDATE())),'AP-',1,5,YEAR(CURDATE()),TRUE),
    ('FACTURA_CLIENTE',CONCAT('Facturas cliente ',YEAR(CURDATE())),'FC-',1,5,YEAR(CURDATE()),TRUE),
    ('FACTURA_PROVEEDOR',CONCAT('Facturas proveedor ',YEAR(CURDATE())),'FP-',1,5,YEAR(CURDATE()),TRUE);


/**************************************************************************
* FIN DEL SCRIPT
**************************************************************************/
