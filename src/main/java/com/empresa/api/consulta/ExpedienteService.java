package com.empresa.api.consulta;

import com.empresa.api.common.ApiException;
import com.empresa.api.security.AutorizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import static com.empresa.api.consulta.ExpedienteDtos.*;
import static com.empresa.api.consulta.ConsultaParametros.Tipo.*;
import com.empresa.api.common.Pagina;

@Service
@Transactional(readOnly = true)
public class ExpedienteService {
    private final AutorizacionService acceso;
    private final ExpedienteRepository repository;
    private final ConsultaService consultas;
    private final ArchivoDemoStore archivos;
    public ExpedienteService(AutorizacionService acceso, ExpedienteRepository repository, ConsultaService consultas, ArchivoDemoStore archivos) {
        this.acceso = acceso; this.repository = repository; this.consultas = consultas; this.archivos = archivos;
    }
    private void autorizar(int usuario, int empresa, String permiso) {
        acceso.exigirLectura(usuario, empresa, permiso);
    }
    public Pagina<PresupuestoResumen> presupuestos(int usuario, int empresa, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, "PRESUPUESTOS");
        return repository.presupuestos(empresa, ConsultaParametros.parse(query, PRESUPUESTOS));
    }
    public PresupuestoDetalle presupuesto(int usuario, int empresa, int id) {
        autorizar(usuario, empresa, "PRESUPUESTOS");
        return repository.presupuesto(empresa, id).orElseThrow(ApiException::noEncontrado);
    }
    public Pagina<Linea> lineas(int usuario, int empresa, int id, MultiValueMap<String, String> query) {
        presupuesto(usuario, empresa, id);
        return repository.lineas(empresa, id, ConsultaParametros.parse(query, LINEAS));
    }
    public Linea linea(int usuario, int empresa, int id, int linea) {
        presupuesto(usuario, empresa, id);
        return repository.linea(empresa, id, linea).orElseThrow(ApiException::noEncontrado);
    }
    private void autorizarObra(int usuario, int empresa, int obra) {
        autorizar(usuario, empresa, "DOCUMENTOS");
        consultas.obra(usuario, empresa, obra);
    }
    public Pagina<Documento> documentos(int usuario, int empresa, int obra, MultiValueMap<String, String> query) {
        autorizarObra(usuario, empresa, obra);
        return repository.documentos(empresa, obra, ConsultaParametros.parse(query, DOCUMENTOS));
    }
    public Documento documento(int usuario, int empresa, int obra, int id) {
        autorizarObra(usuario, empresa, obra);
        return repository.documento(empresa, obra, id).orElseThrow(ApiException::noEncontrado);
    }
    public byte[] descargar(int usuario, int empresa, int obra, int id) {
        documento(usuario, empresa, obra, id);
        String clave = repository.claveArchivo(empresa, obra, id).orElseThrow(ApiException::noEncontrado);
        return archivos.leer(empresa, id, clave);
    }
}
