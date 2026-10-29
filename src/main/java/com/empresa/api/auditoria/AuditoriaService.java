package com.empresa.api.auditoria;

import com.empresa.api.common.ApiException;
import com.empresa.api.common.Pagina;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import static com.empresa.api.auditoria.AuditoriaDtos.Evento;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class AuditoriaService {
    private final AuditoriaRepository repository;
    public AuditoriaService(AuditoriaRepository repository) { this.repository=repository; }
    private void exigir(int usuario) { if(!repository.permite(usuario)) throw ApiException.noEncontrado(); }
    public Pagina<Evento> listar(int usuario,MultiValueMap<String,String> query) {
        exigir(usuario); return repository.listar(AuditoriaParametros.leer(query));
    }
    public Evento detalle(int usuario,long id) { exigir(usuario); return repository.detalle(id); }
}
