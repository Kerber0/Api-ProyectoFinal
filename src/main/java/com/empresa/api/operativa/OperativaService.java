package com.empresa.api.operativa;

import com.empresa.api.common.ApiException;
import com.empresa.api.gestion.GestionRepository;
import com.empresa.api.model.*;
import com.empresa.api.security.AutorizacionService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import static com.empresa.api.operativa.OperativaDtos.*;

@Service
@Transactional(readOnly = true)
public class OperativaService {
    private final GestionRepository repository;
    private final AutorizacionService acceso;
    private final EntityManager em;
    private final Clock clock;
    public OperativaService(GestionRepository repository,AutorizacionService acceso,EntityManager em,Clock clock) {
        this.repository=repository;this.acceso=acceso;this.em=em;this.clock=clock;
    }
    private LocalDateTime ahora() {return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);}
    private Usuario autor(int id) {return repository.referencia(Usuario.class,id);}
    private void preparar(int usuario,int empresa,String permiso) {
        acceso.exigirLectura(usuario,empresa,"EMPRESA");
        acceso.exigirEscritura(usuario,empresa,permiso);
        repository.serializar();
        acceso.exigirEscritura(usuario,empresa,permiso);
    }
    private Obra obra(int empresa,Integer id) {
        if(id==null)return null;
        return repository.obra(empresa,id);
    }
    private Operario operario(int empresa,Integer id) {
        if(id==null)return null;
        return repository.operario(empresa,id);
    }
    private <T> T catalogo(Class<T> clase,int id) {
        T obj=em.find(clase,id);
        if(obj==null) throw ApiException.noEncontrado();
        boolean activo;
        if(obj instanceof Prioridad p) activo=Boolean.TRUE.equals(p.getActivo());
        else if(obj instanceof EstadoIncidencia p) activo=Boolean.TRUE.equals(p.getActivo());
        else if(obj instanceof EstadoTarea p) activo=Boolean.TRUE.equals(p.getActivo());
        else throw new IllegalArgumentException("Clase de catalogo no admitida");
        if(!activo) throw ApiException.noEncontrado();
        return obj;
    }
    private static IncidenciaFicha ficha(int empresa,Incidencia i) {
        return new IncidenciaFicha(i.getId(),empresa,i.getObra().getId(),i.getOperario()==null?null:i.getOperario().getId(),
                i.getFecha(),i.getDescripcion(),i.getEstado().getId(),i.getPrioridad().getId());
    }
    private static TareaFicha ficha(int empresa,Tarea t) {
        return new TareaFicha(t.getId(),empresa,t.getTitulo(),t.getDescripcion(),t.getEstado().getId(),
                t.getPrioridad().getId(),t.getFechaLimite(),t.getFechaFinalizacion(),
                t.getObra()==null?null:t.getObra().getId(),t.getOperario()==null?null:t.getOperario().getId());
    }
    private static EventoFicha ficha(int empresa,Evento e) {
        return new EventoFicha(e.getId(),empresa,e.getTitulo(),e.getDescripcion(),e.getFechaInicio(),
                e.getFechaFin(),e.getColor(),e.getTipoEvento(),e.getObra()==null?null:e.getObra().getId());
    }
    private <T> T cargar(Class<T> clase,int id,int empresa) {
        var registro=em.find(clase,id);
        if(registro==null) throw ApiException.noEncontrado();
        Empresa propietario;
        if(registro instanceof Incidencia x) propietario=x.getEmpresa();
        else if(registro instanceof Tarea x) propietario=x.getEmpresa();
        else if(registro instanceof Evento x) propietario=x.getEmpresa();
        else throw new IllegalArgumentException("Modelo no admitido");
        if(!propietario.getId().equals(empresa)) throw ApiException.noEncontrado();
        return registro;
    }
    @Transactional
    public IncidenciaFicha incidencia(int usuario,int empresa,Integer id,IncidenciaEntrada data) {
        preparar(usuario,empresa,"INCIDENCIAS");
        var i=id==null?new Incidencia():cargar(Incidencia.class,id,empresa);
        if(id==null) { i.setEmpresa(repository.referencia(Empresa.class,empresa));i.setUsuarioCreacion(autor(usuario));i.setFechaCreacion(ahora()); }
        i.setObra(obra(empresa,data.obraId()));i.setOperario(operario(empresa,data.operarioId()));
        i.setFecha(data.fecha());i.setDescripcion(data.descripcion().strip());
        i.setPrioridad(catalogo(Prioridad.class,data.prioridadId()));i.setEstado(catalogo(EstadoIncidencia.class,data.estadoId()));
        i.setUsuarioModificacion(autor(usuario));i.setFechaModificacion(ahora());
        if(id==null) em.persist(i);
        em.flush();repository.auditar(usuario,empresa,"INCIDENCIA",i.getId(),id==null?"CREAR":"ACTUALIZAR",null,
                Map.of("obra",i.getObra().getId(),"estado",i.getEstado().getId(),"prioridad",i.getPrioridad().getId()));
        return ficha(empresa,i);
    }
    @Transactional
    public TareaFicha tarea(int usuario,int empresa,Integer id,TareaEntrada data) {
        preparar(usuario,empresa,"TAREAS");
        var t=id==null?new Tarea():cargar(Tarea.class,id,empresa);
        if(id==null){t.setEmpresa(repository.referencia(Empresa.class,empresa));t.setFechaCreacion(ahora());t.setUsuarioCreacion(autor(usuario));}
        t.setTitulo(data.titulo().strip());t.setDescripcion(data.descripcion());
        t.setEstado(catalogo(EstadoTarea.class,data.estadoId()));t.setPrioridad(catalogo(Prioridad.class,data.prioridadId()));
        t.setFechaLimite(data.fechaLimite());t.setFechaFinalizacion(data.fechaFinalizacion());
        t.setObra(obra(empresa,data.obraId()));t.setOperario(operario(empresa,data.operarioId()));
        t.setFechaModificacion(ahora());t.setUsuarioModificacion(autor(usuario));
        if(id==null)em.persist(t);
        em.flush();repository.auditar(usuario,empresa,"TAREA",t.getId(),id==null?"CREAR":"ACTUALIZAR",null,
                Map.of("estado",t.getEstado().getId(),"prioridad",t.getPrioridad().getId()));
        return ficha(empresa,t);
    }
    @Transactional
    public EventoFicha evento(int usuario,int empresa,Integer id,EventoEntrada data) {
        preparar(usuario,empresa,"EVENTOS");
        if(data.fechaFin()!=null&&data.fechaFin().isBefore(data.fechaInicio()))throw ApiException.invalido("fechaFin");
        var e=id==null?new Evento():cargar(Evento.class,id,empresa);
        if(id==null){e.setEmpresa(repository.referencia(Empresa.class,empresa));e.setFechaCreacion(ahora());e.setUsuarioCreacion(autor(usuario));}
        e.setTitulo(data.titulo().strip());e.setDescripcion(data.descripcion());
        e.setFechaInicio(data.fechaInicio());e.setFechaFin(data.fechaFin());
        e.setColor(data.color());e.setTipoEvento(data.tipoEvento().strip());e.setObra(obra(empresa,data.obraId()));
        e.setFechaModificacion(ahora());e.setUsuarioModificacion(autor(usuario));
        if(id==null)em.persist(e);
        em.flush();repository.auditar(usuario,empresa,"EVENTO",e.getId(),id==null?"CREAR":"ACTUALIZAR",null,
                Map.of("fechaInicio",e.getFechaInicio().toString(),"tipo",e.getTipoEvento()));
        return ficha(empresa,e);
    }
}
