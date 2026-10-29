package com.empresa.api.observaciones;

import com.empresa.api.common.ApiException;
import com.empresa.api.common.Pagina;
import com.empresa.api.laboral.EmpleoRepository;
import com.empresa.api.laboral.EmpleoService;
import com.empresa.api.model.ObservacionObra;
import com.empresa.api.security.AutorizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import static com.empresa.api.observaciones.ObservacionDtos.*;

@Service @Transactional(readOnly=true)
public class ObservacionService {
    private final ObservacionRepository repository;
    private final AutorizacionService autorizacion;
    private final EmpleoService empleo;
    private final EmpleoRepository ambito;
    private final Clock clock;
    public ObservacionService(ObservacionRepository repository,AutorizacionService autorizacion,EmpleoService empleo,EmpleoRepository ambito,Clock clock) {
        this.repository=repository; this.autorizacion=autorizacion; this.empleo=empleo; this.ambito=ambito; this.clock=clock;
    }
    private void autorizar(int autor,int empresa,int obra,boolean propia,boolean escritura) {
        autorizacion.exigirLectura(autor,empresa,"EMPRESA");
        if(escritura) autorizacion.exigirEscritura(autor,empresa,"OBSERVACIONES");
        else autorizacion.exigirLectura(autor,empresa,"OBSERVACIONES");
        if(propia) empleo.obra(autor,empresa,obra);
        else {
            if(escritura) autorizacion.exigirEscritura(autor,empresa,"OBRAS");
            else autorizacion.exigirLectura(autor,empresa,"OBRAS");
            ambito.exigirObra(empresa,obra);
        }
    }
    private void comenzar(int autor,int empresa,int obra,boolean propia) {
        autorizar(autor,empresa,obra,propia,true);
        // Mismo bloqueo que cambios de roles, vinculos y asignaciones; revalidar al obtenerlo.
        ambito.serializar();
        autorizar(autor,empresa,obra,propia,true);
    }
    private String texto(String value,String campo) {
        if(value==null || value.isBlank() || value.codePointCount(0,value.length())>2000
                || value.codePoints().anyMatch(c->Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t'))
            throw ApiException.invalido(campo);
        return value.strip();
    }
    private Detalle detalle(ObservacionObra o) {
        return new Detalle(o.getId(),o.getObra(),o.getAutor(),o.getFechaCreacion(),Estado.valueOf(o.getEstado().name()),
                o.getTexto(),o.getResueltoPor(),o.getFechaResolucion(),o.getResolucion());
    }
    public Pagina<Resumen> listar(int autor,int empresa,int obra,boolean propia,MultiValueMap<String,String> query) {
        autorizar(autor,empresa,obra,propia,false);
        return repository.listar(empresa,obra,ObservacionParametros.leer(query));
    }
    public Detalle consultar(int autor,int empresa,int obra,int id,boolean propia) {
        autorizar(autor,empresa,obra,propia,false);
        return detalle(repository.buscar(empresa,obra,id));
    }
    @Transactional public Detalle crear(int autor,int empresa,int obra,boolean propia,Nueva request) {
        comenzar(autor,empresa,obra,propia);
        var o=new ObservacionObra(); o.setEmpresa(empresa); o.setObra(obra); o.setAutor(autor);
        o.setTexto(texto(request.texto(),"texto")); o.setFechaCreacion(clock.instant().truncatedTo(ChronoUnit.MICROS));
        repository.guardar(o); repository.auditar(o.getId(),autor,"CREAR",o.getFechaCreacion());
        return detalle(o);
    }
    @Transactional public Detalle resolver(int autor,int empresa,int obra,int id,Resolver request) {
        comenzar(autor,empresa,obra,false);
        var o=repository.buscar(empresa,obra,id);
        String resolucion=texto(request.resolucion(),"resolucion");
        if(o.getEstado()==ObservacionObra.Estado.RESUELTA) {
            if(!resolucion.equals(o.getResolucion())) throw ApiException.conflicto("La observacion ya esta resuelta; no se puede sustituir su resolucion");
            return detalle(o);
        }
        o.setEstado(ObservacionObra.Estado.RESUELTA); o.setResueltoPor(autor);
        o.setFechaResolucion(clock.instant().truncatedTo(ChronoUnit.MICROS)); o.setResolucion(resolucion);
        repository.flush(); repository.auditar(id,autor,"RESOLVER",o.getFechaResolucion());
        return detalle(o);
    }
}
