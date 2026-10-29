package com.empresa.api.common;

import java.util.List;

public class ApiException extends RuntimeException {
    private final int status;
    private final String codigo;
    private final List<String> campos;
    private ApiException(int status, String codigo, String detalle, List<String> campos) {
        super(detalle);
        this.status = status;
        this.codigo = codigo;
        this.campos = campos;
    }
    public int status() { return status; }
    public String codigo() { return codigo; }
    public List<String> campos() { return campos; }
    public static ApiException noEncontrado() {
        return new ApiException(404, "NO_ENCONTRADO", "Recurso no disponible", List.of());
    }
    public static ApiException cuerpoGrande() {
        return new ApiException(413, "CUERPO_DEMASIADO_GRANDE", "Cuerpo de solicitud demasiado grande", List.of());
    }
    public static ApiException almacenNoDisponible() {
        return new ApiException(503, "ALMACEN_NO_DISPONIBLE", "Archivo temporalmente no disponible", List.of());
    }
    public static ApiException invalido(String campo) {
        return new ApiException(400, "SOLICITUD_INVALIDA", "Revisar los parametros de la solicitud", List.of(campo));
    }
    public static ApiException conflicto(String detalle) {
        return new ApiException(409, "CONFLICTO", detalle, List.of());
    }
}
