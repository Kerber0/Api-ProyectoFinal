package com.empresa.api.consulta;

// Identificadores SQL cerrados en codigo; nunca proceden de parametros HTTP.
enum AlbaranTipo {
    CLIENTE("Cliente", "Clientes", "cliente", "factura", "CLIENTES", "FACTURAS_CLIENTE"),
    PROVEEDOR("Proveedor", "Proveedores", "proveedor", "factura", "PROVEEDORES", "FACTURAS_PROVEEDOR");

    EconomiaTipo economia() { return this == CLIENTE ? EconomiaTipo.CLIENTE : EconomiaTipo.PROVEEDOR; }

    final String tabla, lineas, pertenencia, terceros, pertenenciaTercero, terceroId, terceroCodigo;
    final String filtroTercero, filtroRelacion, permiso, permisoTercero, permisoRelacion;
    AlbaranTipo(String sufijo, String terceros, String filtroTercero, String filtroRelacion,
                String permisoTercero, String permisoRelacion) {
        this.tabla = "Albaranes_" + sufijo;
        this.lineas = "Lineas_Albaran_" + sufijo;
        this.pertenencia = "API_Albaran_" + sufijo + "_Empresa";
        this.terceros = terceros;
        this.pertenenciaTercero = "API_" + sufijo + "_Empresa";
        this.terceroId = "id_" + filtroTercero;
        this.terceroCodigo = "codigo_" + filtroTercero;
        this.filtroTercero = filtroTercero;
        this.filtroRelacion = filtroRelacion;
        this.permiso = "ALBARANES_" + sufijo.toUpperCase(java.util.Locale.ROOT);
        this.permisoTercero = permisoTercero;
        this.permisoRelacion = permisoRelacion;
    }
}
