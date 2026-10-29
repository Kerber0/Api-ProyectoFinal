package com.empresa.api.documentos;

import com.empresa.api.common.ApiException;
import com.empresa.api.common.Pagina;
import com.empresa.api.consulta.ExpedienteService;
import com.empresa.api.laboral.EmpleoRepository;
import com.empresa.api.laboral.EmpleoService;
import com.empresa.api.model.ArchivoDocumento;
import com.empresa.api.security.AutorizacionService;
import com.empresa.api.security.GestionUsuariosRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.MultiValueMap;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import static com.empresa.api.documentos.DocumentoDtos.*;

@Service @Transactional(readOnly=true)
public class DocumentoService {
    private final DocumentoRepository repository;
    private final AutorizacionService acceso;
    private final GestionUsuariosRepository usuarios;
    private final EmpleoRepository ambito;
    private final EmpleoService empleo;
    private final ExpedienteService expedientes;
    private final Map<String,ArchivoStore> stores;
    private final String proveedor;
    private final Clock clock;
    public DocumentoService(DocumentoRepository repository,AutorizacionService acceso,GestionUsuariosRepository usuarios,
            EmpleoRepository ambito,EmpleoService empleo,ExpedienteService expedientes,List<ArchivoStore> stores,
            @Value("${dorna.documentos.proveedor:local}") String proveedor,Clock clock) {
        this.repository=repository; this.acceso=acceso; this.usuarios=usuarios; this.ambito=ambito;
        this.empleo=empleo; this.expedientes=expedientes; this.proveedor=proveedor; this.clock=clock;
        this.stores=stores.stream().collect(Collectors.toUnmodifiableMap(ArchivoStore::proveedor,s->s));
        if(!this.stores.containsKey(proveedor)) throw new IllegalStateException("Proveedor documental no configurado");
    }
    private void autorizar(int autor,int empresa,DocumentoTipo tipo,int referencia,boolean propia,boolean escritura) {
        acceso.exigirLectura(autor,empresa,"EMPRESA");
        if(propia) {
            if(tipo!=DocumentoTipo.AUSENCIA || escritura) throw ApiException.noEncontrado();
            int empleado=empleo.empleado(autor,empresa).id();
            if(repository.empleadoAusencia(empresa,referencia,false)!=empleado) throw ApiException.noEncontrado();
            return;
        }
        if(tipo==DocumentoTipo.AUSENCIA && !usuarios.permiteGlobal(autor,true)) throw ApiException.noEncontrado();
        for(String permiso:List.of("DOCUMENTOS",tipo.permiso)) {
            if(escritura) acceso.exigirEscritura(autor,empresa,permiso); else acceso.exigirLectura(autor,empresa,permiso);
        }
        switch(tipo) {
            case OBRA -> ambito.exigirObra(empresa,referencia);
            case PRESUPUESTO -> expedientes.presupuesto(autor,empresa,referencia);
            case AUSENCIA -> ambito.exigirEmpleado(empresa,repository.empleadoAusencia(empresa,referencia,escritura));
        }
    }
    private Detalle detalle(ArchivoDocumento d) { return new Detalle(d.getId(),d.getNombre(),d.getMime(),d.getTamano(),d.getSha256(),d.getAutor(),d.getFecha()); }
    public Pagina<Detalle> listar(int autor,int empresa,String tipo,int referencia,boolean propia,MultiValueMap<String,String> query) {
        var t=DocumentoTipo.leer(tipo); autorizar(autor,empresa,t,referencia,propia,false);
        return repository.listar(empresa,t,referencia,query);
    }
    public Detalle consultar(int autor,int empresa,String tipo,int referencia,int id,boolean propia) {
        var t=DocumentoTipo.leer(tipo); autorizar(autor,empresa,t,referencia,propia,false);
        return detalle(repository.buscar(empresa,t,referencia,id));
    }
    public Contenido descargar(int autor,int empresa,String tipo,int referencia,int id,boolean propia) {
        var t=DocumentoTipo.leer(tipo); autorizar(autor,empresa,t,referencia,propia,false);
        var d=repository.buscar(empresa,t,referencia,id); var store=stores.get(d.getProveedor());
        if(store==null) throw ApiException.almacenNoDisponible();
        byte[] bytes=store.leer(d.getClave());
        if(bytes.length!=d.getTamano() || !DocumentoValidacion.hash(bytes).equals(d.getSha256())) throw ApiException.almacenNoDisponible();
        return new Contenido(detalle(d),bytes);
    }
    @Transactional public Detalle crear(int autor,int empresa,String tipo,int referencia,String mime,MultiValueMap<String,String> query,byte[] bytes) {
        var t=DocumentoTipo.leer(tipo); autorizar(autor,empresa,t,referencia,false,true);
        mime=DocumentoValidacion.mime(mime);
        if(query.size()!=1 || !query.containsKey("nombre") || query.get("nombre").size()!=1) throw ApiException.invalido("query");
        String nombre=DocumentoValidacion.nombre(query.getFirst("nombre"),mime,bytes);
        ambito.serializar(); autorizar(autor,empresa,t,referencia,false,true);
        var d=new ArchivoDocumento(); d.setEmpresa(empresa); d.setNombre(nombre); d.setMime(mime); d.setTamano(bytes.length);
        d.setSha256(DocumentoValidacion.hash(bytes)); d.setClave(UUID.randomUUID().toString()); d.setProveedor(proveedor);
        d.setAutor(autor); d.setFecha(clock.instant().truncatedTo(ChronoUnit.MICROS));
        switch(t) { case OBRA -> d.setObra(referencia); case PRESUPUESTO -> d.setPresupuesto(referencia); case AUSENCIA -> d.setAusencia(referencia); }
        var store=stores.get(proveedor);
        // Publicar bytes antes del commit. Un rollback elimina solo la clave creada por esta operacion.
        store.guardar(d.getClave(),bytes);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if(status==STATUS_ROLLED_BACK) store.borrar(d.getClave());
            }
        });
        repository.guardar(d);
        return detalle(d);
    }
}
