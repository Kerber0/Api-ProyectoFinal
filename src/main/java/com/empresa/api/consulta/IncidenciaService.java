package com.empresa.api.consulta;

import com.empresa.api.common.ApiException;
import com.empresa.api.security.AutorizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import static com.empresa.api.consulta.IncidenciaDtos.*;
import com.empresa.api.common.Pagina;

@Service
@Transactional(readOnly = true)
public class IncidenciaService {
    private final AutorizacionService acceso;
    private final IncidenciaRepository repository;
    private final ConsultaService consultas;
    private final LaboralService laboral;
    public IncidenciaService(AutorizacionService acceso, IncidenciaRepository repository, ConsultaService consultas, LaboralService laboral) {
        this.acceso = acceso; this.repository = repository; this.consultas = consultas; this.laboral = laboral;
    }
    private void autorizar(int usuario, int empresa) {
        for (String permiso : new String[]{"INCIDENCIAS", "OBRAS", "CLIENTES", "OPERARIOS"}) {
            acceso.exigirLectura(usuario, empresa, permiso);
        }
    }
    public Pagina<Resumen> incidencias(int usuario, int empresa, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa);
        var p = IncidenciaParametros.parse(query, null);
        return repository.incidencias(empresa, p.pagina().obra(), p.pagina().operario(), p);
    }
    public Detalle incidencia(int usuario, int empresa, int id) {
        autorizar(usuario, empresa);
        return repository.incidencia(empresa, id).orElseThrow(ApiException::noEncontrado);
    }
    public Pagina<Resumen> obra(int usuario, int empresa, int id, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa);
        consultas.obra(usuario, empresa, id);
        var p = IncidenciaParametros.parse(query, "obra");
        return repository.incidencias(empresa, id, p.pagina().operario(), p);
    }
    public Pagina<Resumen> operario(int usuario, int empresa, int id, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa);
        laboral.operario(usuario, empresa, id);
        var p = IncidenciaParametros.parse(query, "operario");
        return repository.incidencias(empresa, p.pagina().obra(), id, p);
    }
}
