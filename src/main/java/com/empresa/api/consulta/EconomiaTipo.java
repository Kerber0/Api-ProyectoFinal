package com.empresa.api.consulta;

// Identificadores SQL cerrados; ningun nombre de tabla procede de parametros HTTP.
enum EconomiaTipo {
    CLIENTE("Facturas_Cliente", "Lineas_Factura_Cliente", "API_Factura_Cliente_Empresa",
            "Clientes", "API_Cliente_Empresa", "id_cliente", "codigo_cliente", "cliente", "Cobros", "id_cobro", "FACTURAS_CLIENTE", "COBROS"),
    PROVEEDOR("Facturas_Proveedor", "Lineas_Factura_Proveedor", "API_Factura_Proveedor_Empresa",
            "Proveedores", "API_Proveedor_Empresa", "id_proveedor", "codigo_proveedor", "proveedor", "Pagos", "id_pago", "FACTURAS_PROVEEDOR", "PAGOS");

    final String facturas, lineas, pertenencia, terceros, pertenenciaTercero, terceroId, terceroCodigo, filtroTercero;
    final String movimientos, movimientoId, permisoFactura, permisoMovimiento;
    EconomiaTipo(String facturas, String lineas, String pertenencia, String terceros, String pertenenciaTercero,
                 String terceroId, String terceroCodigo, String filtroTercero, String movimientos, String movimientoId,
                 String permisoFactura, String permisoMovimiento) {
        this.facturas = facturas; this.lineas = lineas; this.pertenencia = pertenencia;
        this.terceros = terceros; this.pertenenciaTercero = pertenenciaTercero;
        this.terceroId = terceroId; this.terceroCodigo = terceroCodigo; this.filtroTercero = filtroTercero;
        this.movimientos = movimientos; this.movimientoId = movimientoId;
        this.permisoFactura = permisoFactura; this.permisoMovimiento = permisoMovimiento;
    }
}
