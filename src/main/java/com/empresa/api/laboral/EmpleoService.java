package com.empresa.api.laboral;

import com.empresa.api.common.ApiException;
import com.empresa.api.common.Pagina;
import com.empresa.api.model.AsignacionObra;
import com.empresa.api.model.Ausencia;
import com.empresa.api.security.AutorizacionService;
import com.empresa.api.security.GestionUsuariosRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import java.time.Clock;
import java.time.LocalDate;
import static com.empresa.api.laboral.EmpleoDtos.*;

@Service @Transactional(readOnly=true)
public class EmpleoService {
    private final EmpleoRepository repository;
    private final AutorizacionService autorizacion;
    private final GestionUsuariosRepository usuarios;
    private final Clock clock;
    public EmpleoService(EmpleoRepository repository,AutorizacionService autorizacion,GestionUsuariosRepository usuarios,Clock clock) {
        this.repository=repository; this.autorizacion=autorizacion; this.usuarios=usuarios; this.clock=clock;
    }
    private void gestionar(int autor,int empresa,int operario,boolean privado,boolean escritura) {
        if(privado && !usuarios.permiteGlobal(autor,true)) throw ApiException.noEncontrado();
        autorizacion.exigirLectura(autor,empresa,"EMPRESA");
        if(escritura) autorizacion.exigirEscritura(autor,empresa,"OPERARIOS");
        else autorizacion.exigirLectura(autor,empresa,"OPERARIOS");
        if(!privado) {
            if(escritura) autorizacion.exigirEscritura(autor,empresa,"OBRAS");
            else autorizacion.exigirLectura(autor,empresa,"OBRAS");
        }
        repository.exigirEmpleado(empresa,operario);
    }
    private void comenzar(int autor,int empresa,int operario,boolean privado) {
        gestionar(autor,empresa,operario,privado,true); repository.serializar();
        gestionar(autor,empresa,operario,privado,true);
    }
    private void periodo(LocalDate desde,LocalDate hasta,boolean finObligatorio) {
        if(desde==null || desde.getYear()<1 || desde.getYear()>9999 || (finObligatorio && hasta==null)
                || (hasta!=null && (hasta.isBefore(desde)||hasta.getYear()>9999))) throw ApiException.invalido("periodo");
    }
    public Vinculo vinculo(int autor,int empresa,int operario) {
        gestionar(autor,empresa,operario,true,false);
        var v=repository.vinculo(operario); if(v==null) throw ApiException.noEncontrado(); return v;
    }
    @Transactional public void vincular(int autor,int empresa,int operario,Vincular request) {
        comenzar(autor,empresa,operario,true);
        if(request.usuarioId()==null || request.usuarioId()<1) throw ApiException.invalido("usuarioId");
        if(repository.vincular(operario,request.usuarioId())) repository.auditar(autor,empresa,operario,"VINCULAR_USUARIO",request.usuarioId());
    }
    @Transactional public void desvincular(int autor,int empresa,int operario) {
        comenzar(autor,empresa,operario,true);
        var usuario=repository.desvincular(operario);
        if(usuario!=null) repository.auditar(autor,empresa,operario,"DESVINCULAR_USUARIO",usuario);
    }
    public Pagina<Asignacion> asignaciones(int autor,int empresa,int operario,MultiValueMap<String,String> query) {
        gestionar(autor,empresa,operario,false,false);
        return repository.asignaciones(empresa,operario,EmpleoParametros.leer(query,true));
    }
    @Transactional public Asignacion asignar(int autor,int empresa,int operario,NuevaAsignacion request) {
        comenzar(autor,empresa,operario,false); periodo(request.desde(),request.hasta(),false);
        if(request.obraId()==null || request.obraId()<1) throw ApiException.invalido("obraId");
        repository.exigirObra(empresa,request.obraId());
        repository.exigirPeriodoLaboral(empresa,operario,request.desde(),request.hasta());
        if(repository.solapaAsignacion(empresa,operario,request.obraId(),request.desde(),request.hasta()))
            throw ApiException.conflicto("El empleado ya tiene una asignacion a esa obra en el periodo");
        var a=new AsignacionObra(); a.setEmpresa(empresa); a.setOperario(operario); a.setObra(request.obraId());
        a.setDesde(request.desde()); a.setHasta(request.hasta()); repository.guardar(a);
        repository.auditar(autor,empresa,operario,"CREAR_ASIGNACION",a.getId());
        return new Asignacion(a.getId(),operario,a.getObra(),a.getDesde(),a.getHasta(),false);
    }
    @Transactional public void cancelarAsignacion(int autor,int empresa,int operario,int id) {
        comenzar(autor,empresa,operario,false);
        var a=repository.asignacion(empresa,operario,id);
        if(!a.isCancelada()) { a.setCancelada(true); repository.flush(); repository.auditar(autor,empresa,operario,"CANCELAR_ASIGNACION",id); }
    }
    public Pagina<AusenciaDetallePrivado> ausencias(int autor,int empresa,int operario,MultiValueMap<String,String> query) {
        gestionar(autor,empresa,operario,true,false);
        return repository.ausenciasPrivadas(empresa,operario,EmpleoParametros.leer(query,true));
    }
    @Transactional public AusenciaDetallePrivado ausencia(int autor,int empresa,int operario,NuevaAusencia request) {
        comenzar(autor,empresa,operario,true); periodo(request.desde(),request.hasta(),true);
        if(request.tipo()==null) throw ApiException.invalido("tipo");
        repository.exigirPeriodoLaboral(empresa,operario,request.desde(),request.hasta());
        if(repository.solapaAusencia(empresa,operario,request.desde(),request.hasta())) throw ApiException.conflicto("Ausencia solapada en el periodo");
        var a=new Ausencia(); a.setEmpresa(empresa); a.setOperario(operario); a.setDesde(request.desde()); a.setHasta(request.hasta());
        String observaciones = request.observaciones() == null ? null : request.observaciones().strip();
        if (observaciones != null && observaciones.length() > 1000) throw ApiException.invalido("observaciones");
        if (observaciones != null && observaciones.isEmpty()) observaciones = null;
        a.setTipo(Ausencia.Tipo.valueOf(request.tipo().name()));
        a.setJustificada(request.justificada());
        a.setObservaciones(observaciones);
        repository.guardar(a);
        repository.auditar(autor,empresa,operario,"CREAR_AUSENCIA",a.getId());
        return new AusenciaDetallePrivado(a.getId(),operario,a.getDesde(),a.getHasta(),
                request.tipo(),false,a.getJustificada(),a.getObservaciones());
    }
    @Transactional public void cancelarAusencia(int autor,int empresa,int operario,int id) {
        comenzar(autor,empresa,operario,true);
        var a=repository.ausencia(empresa,operario,id);
        if(!a.isCancelada()) { a.setCancelada(true); repository.flush(); repository.auditar(autor,empresa,operario,"CANCELAR_AUSENCIA",id); }
    }
    private Empleado propio(int autor,int empresa,LocalDate hoy) {
        autorizacion.exigirLectura(autor,empresa,"EMPRESA");
        autorizacion.exigirLectura(autor,empresa,"TRABAJO_PROPIO");
        return repository.propio(autor,empresa,hoy);
    }
    public Empleado empleado(int autor,int empresa) { return propio(autor,empresa,LocalDate.now(clock)); }
    public Pagina<ObraOperativa> obras(int autor,int empresa,MultiValueMap<String,String> query) {
        var hoy=LocalDate.now(clock); var empleado=propio(autor,empresa,hoy);
        return repository.obrasPropias(empresa,empleado.id(),hoy,EmpleoParametros.leer(query,false));
    }
    public ObraOperativa obra(int autor,int empresa,int obra) {
        var hoy=LocalDate.now(clock); var empleado=propio(autor,empresa,hoy);
        return repository.obraPropia(empresa,empleado.id(),obra,hoy);
    }
    public Pagina<ContactoOperativo> contactos(int autor,int empresa,int obra,MultiValueMap<String,String> query) {
        var hoy=LocalDate.now(clock); var empleado=propio(autor,empresa,hoy);
        repository.obraPropia(empresa,empleado.id(),obra,hoy);
        return repository.contactos(empresa,empleado.id(),obra,hoy,EmpleoParametros.leer(query,false));
    }
    public Pagina<AusenciaDetalle> ausenciasPropias(int autor,int empresa,MultiValueMap<String,String> query) {
        var empleado=propio(autor,empresa,LocalDate.now(clock));
        return repository.ausencias(empresa,empleado.id(),EmpleoParametros.leer(query,true));
    }
}