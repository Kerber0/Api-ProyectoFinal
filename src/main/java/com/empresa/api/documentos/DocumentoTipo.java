package com.empresa.api.documentos;

import com.empresa.api.common.ApiException;

enum DocumentoTipo {
    OBRA("obras","obra","OBRAS"), PRESUPUESTO("presupuestos","presupuesto","PRESUPUESTOS"),
    AUSENCIA("ausencias","ausencia","OPERARIOS");
    final String ruta,columna,permiso;
    DocumentoTipo(String ruta,String columna,String permiso) { this.ruta=ruta; this.columna=columna; this.permiso=permiso; }
    static DocumentoTipo leer(String ruta) {
        for(var tipo:values()) if(tipo.ruta.equals(ruta)) return tipo;
        throw ApiException.noEncontrado();
    }
}
