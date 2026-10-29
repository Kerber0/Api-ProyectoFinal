package com.empresa.api.consulta;

import com.empresa.api.common.ApiException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;

/** Almacen de fixtures privados. No abre Documento.ruta ni rutas recibidas del cliente. */
@Component
public class ArchivoDemoStore {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    public byte[] leer(int empresa, int documento, String clave) {
        if (empresa <= 0 || documento <= 0 || clave == null ||
                !clave.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) {
            throw ApiException.noEncontrado();
        }
        // Empresa e ID forman parte de la ruta: una clave ajena tampoco permite cruzar documentos.
        var resource = new ClassPathResource("demo/documentos/" + empresa + "/" + documento + "/" + clave);
        try (var stream = resource.getInputStream()) {
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) { throw ApiException.noEncontrado(); }
            return bytes;
        } catch (IOException ex) { throw ApiException.noEncontrado(); }
    }
}
