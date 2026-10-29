package com.empresa.api.documentos;

import com.empresa.api.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.*;

@Component
public class ArchivoLocalStore implements ArchivoStore {
    private final Path raiz;
    public ArchivoLocalStore(@Value("${dorna.documentos.raiz}") String raiz) {
        if(raiz==null || raiz.isBlank()) throw new IllegalArgumentException("Configurar raiz documental privada");
        this.raiz=Path.of(raiz).toAbsolutePath().normalize();
    }
    public String proveedor() { return "local"; }
    private Path ruta(String clave) throws IOException {
        if(clave==null || !clave.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"))
            throw ApiException.noEncontrado();
        // No seguir enlaces ni junctions en el arbol del almacen privado.
        Path actual=raiz.getRoot();
        for(Path segmento:raiz) {
            actual=actual.resolve(segmento);
            if(Files.exists(actual,NOFOLLOW_LINKS) && (!Files.isDirectory(actual,NOFOLLOW_LINKS)
                    || !actual.toRealPath().equals(actual.toRealPath(NOFOLLOW_LINKS))))
                throw new IOException("Almacen no valido");
        }
        return raiz.resolve(clave);
    }
    public void guardar(String clave,byte[] contenido) {
        Path archivo=null; boolean creado=false;
        try {
            archivo=ruta(clave); Files.createDirectories(raiz); ruta(clave);
            try(var canal=FileChannel.open(archivo,CREATE_NEW,WRITE,NOFOLLOW_LINKS)) {
                creado=true; var buffer=ByteBuffer.wrap(contenido);
                while(buffer.hasRemaining()) canal.write(buffer);
                canal.force(true);
            }
        } catch(IOException ex) {
            if(creado) try { Files.deleteIfExists(archivo); } catch(IOException cleanup) { ex.addSuppressed(cleanup); }
            throw ApiException.almacenNoDisponible();
        }
    }
    public byte[] leer(String clave) {
        try {
            Path archivo=ruta(clave);
            if(!Files.isRegularFile(archivo,NOFOLLOW_LINKS)) throw ApiException.noEncontrado();
            try(var input=Files.newInputStream(archivo,READ,NOFOLLOW_LINKS)) {
                byte[] bytes=input.readNBytes(DocumentoValidacion.MAX_BYTES+1);
                if(bytes.length>DocumentoValidacion.MAX_BYTES) throw ApiException.almacenNoDisponible();
                return bytes;
            }
        } catch(NoSuchFileException ex) { throw ApiException.noEncontrado(); }
        catch(IOException ex) { throw ApiException.almacenNoDisponible(); }
    }
    public void borrar(String clave) {
        try { Files.deleteIfExists(ruta(clave)); }
        catch(IOException ex) { throw ApiException.almacenNoDisponible(); }
    }
}
