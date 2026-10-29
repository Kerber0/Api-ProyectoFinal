package com.empresa.api.consulta;

import com.empresa.api.common.ApiException;
import com.empresa.api.security.AutorizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import static com.empresa.api.consulta.CalendarioDtos.*;
import com.empresa.api.common.Pagina;

@Service
@Transactional(readOnly = true)
public class CalendarioService {
    private final AutorizacionService acceso;
    private final CalendarioRepository repository;
    private final ConsultaService consultas;
    private final LaboralService laboral;
    public CalendarioService(AutorizacionService acceso, CalendarioRepository repository, ConsultaService consultas, LaboralService laboral) {
        this.acceso = acceso; this.repository = repository; this.consultas = consultas; this.laboral = laboral;
    }
    private void autorizar(int usuario, int empresa, boolean tarea) {
        for (String permiso : tarea ? new String[]{"TAREAS", "OBRAS", "CLIENTES", "OPERARIOS"}
                : new String[]{"EVENTOS", "OBRAS", "CLIENTES"}) {
            acceso.exigirLectura(usuario, empresa, permiso);
        }
    }
    public Pagina<Evento> eventos(int usuario, int empresa, Integer obra, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, false);
        if (obra != null) { consultas.obra(usuario, empresa, obra); }
        var p = CalendarioParametros.parse(query, false, obra == null ? null : "obra");
        return repository.eventos(empresa, obra == null ? p.pagina().obra() : obra, p);
    }
    public EventoDetalle evento(int usuario, int empresa, int id) {
        autorizar(usuario, empresa, false);
        return repository.evento(empresa, id).orElseThrow(ApiException::noEncontrado);
    }
    public Pagina<Tarea> tareas(int usuario, int empresa, Integer obra, Integer operario, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, true);
        if (obra != null) { consultas.obra(usuario, empresa, obra); }
        if (operario != null) { laboral.operario(usuario, empresa, operario); }
        var p = CalendarioParametros.parse(query, true, obra != null ? "obra" : operario != null ? "operario" : null);
        return repository.tareas(empresa, obra == null ? p.pagina().obra() : obra, operario == null ? p.pagina().operario() : operario, p);
    }
    public TareaDetalle tarea(int usuario, int empresa, int id) {
        autorizar(usuario, empresa, true);
        return repository.tarea(empresa, id).orElseThrow(ApiException::noEncontrado);
    }
}
