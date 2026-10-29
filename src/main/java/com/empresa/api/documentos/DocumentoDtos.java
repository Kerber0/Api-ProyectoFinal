package com.empresa.api.documentos;

import java.time.Instant;

public final class DocumentoDtos {
    private DocumentoDtos() {}
    public record Detalle(int id,String nombre,String mime,int tamano,String sha256,int autorId,Instant fecha) {}
    public record Contenido(Detalle documento,byte[] bytes) {}
}
