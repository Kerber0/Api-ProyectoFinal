package com.empresa.api.documentos;

/** Claves opacas generadas en servidor. Ningun adaptador acepta rutas del cliente. */
public interface ArchivoStore {
    String proveedor();
    void guardar(String clave, byte[] contenido);
    byte[] leer(String clave);
    void borrar(String clave);
}
