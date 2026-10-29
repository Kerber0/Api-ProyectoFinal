package com.empresa.api.gestion;

import com.empresa.api.common.ApiException;
import com.empresa.api.model.*;
import com.empresa.api.security.AutorizacionService;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import static com.empresa.api.gestion.GestionDatosDtos.*;

@Service
@Transactional(readOnly = true)
public class GestionDatosService {
    private final GestionRepository repository;
    private final AutorizacionService acceso;
    private final EntityManager em;
    private final Clock clock;

    public GestionDatosService(GestionRepository repository, AutorizacionService acceso,
                               EntityManager em, Clock clock) {
        this.repository=repository; this.acceso=acceso; this.em=em; this.clock=clock;
    }

    private LocalDateTime ahora() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private Usuario autor(int id) { return repository.referencia(Usuario.class, id); }

    private Entidad titular(int usuario, int empresa, TipoTitular tipo, int id, boolean escritura) {
        if (tipo == null) { throw ApiException.invalido("tipo"); }
        String permiso = tipo == TipoTitular.CLIENTE ? "CLIENTES" : tipo == TipoTitular.PROVEEDOR ? "PROVEEDORES" : "OPERARIOS";
        acceso.exigirLectura(usuario, empresa, "EMPRESA");
        acceso.exigirLectura(usuario, empresa, permiso);
        if (escritura || tipo == TipoTitular.OPERARIO) { acceso.exigirEscritura(usuario, empresa, permiso); }
        if (tipo == TipoTitular.CLIENTE) {
            Cliente c = repository.cliente(empresa, id);
            if (escritura) {
                // Un maestro compartido requiere escritura sobre todas sus empresas visibles.
                for (Empresa e : c.getEmpresasVisibles()) { acceso.exigirEscritura(usuario, e.getId(), permiso); }
            }
            return c.getEntidad();
        }
        if (tipo == TipoTitular.OPERARIO) { return repository.operario(empresa,id).getEntidad(); }
        Proveedor p = repository.proveedor(empresa, id);
        if (escritura) {
            for (Empresa e : p.getEmpresasVisibles()) { acceso.exigirEscritura(usuario, e.getId(), permiso); }
        }
        return p.getEntidad();
    }

    private Contacto contacto(Entidad entidad, int id) {
        Contacto c=em.find(Contacto.class,id);
        if (c == null || !c.getEntidad().getId().equals(entidad.getId())) { throw ApiException.noEncontrado(); }
        return c;
    }
    private Direccion direccion(Entidad entidad, int id) {
        Direccion d=em.find(Direccion.class,id);
        if (d == null || !d.getEntidad().getId().equals(entidad.getId())) { throw ApiException.noEncontrado(); }
        return d;
    }

    private static ContactoFicha ficha(int titular, Contacto c) {
        return new ContactoFicha(c.getId(), titular, c.getNombre(), c.getApellidos(),
                c.getCargo(),c.getDepartamento(),c.getTelefono(),c.getMovil(),c.getEmail(),
                c.getObservaciones(),Boolean.TRUE.equals(c.getPrincipal()),Boolean.TRUE.equals(c.getActivo()));
    }
    private static DireccionFicha ficha(int titular, Direccion d) {
        return new DireccionFicha(d.getId(), titular, d.getTipoDireccion(),d.getDireccion(),
                d.getCodigoPostal(),d.getPoblacion(),d.getProvincia(),d.getPais(),
                d.getTelefono(),d.getObservaciones(),Boolean.TRUE.equals(d.getPrincipal()),Boolean.TRUE.equals(d.getActivo()));
    }

    public List<ContactoFicha> contactos(int user,int empresa,TipoTitular tipo,int id) {
        var e=titular(user,empresa,tipo,id,false);
        return em.createQuery("SELECT c FROM Contacto c WHERE c.entidad.id=:id ORDER BY c.id",Contacto.class)
                .setParameter("id",e.getId()).setMaxResults(250).getResultList().stream().map(c->ficha(id,c)).toList();
    }
    public ContactoFicha contacto(int user,int empresa,TipoTitular tipo,int id,int contacto) {
        return ficha(id,contacto(titular(user,empresa,tipo,id,false),contacto));
    }
    public List<DireccionFicha> direcciones(int user,int empresa,TipoTitular tipo,int id) {
        var e=titular(user,empresa,tipo,id,false);
        return em.createQuery("SELECT d FROM Direccion d WHERE d.entidad.id=:id ORDER BY d.id",Direccion.class)
                .setParameter("id",e.getId()).setMaxResults(250).getResultList().stream().map(d->ficha(id,d)).toList();
    }
    public DireccionFicha direccion(int user,int empresa,TipoTitular tipo,int id,int direccion) {
        return ficha(id,direccion(titular(user,empresa,tipo,id,false),direccion));
    }

    private void validarPrincipal(boolean principal, boolean activo) {
        if (principal && !activo) { throw ApiException.invalido("principal"); }
    }
    private void principalContacto(Entidad entidad,int actual,int user,int empresa) {
        for (var c:em.createQuery("SELECT c FROM Contacto c WHERE c.entidad.id=:id AND c.principal=true AND c.id<>:actual",Contacto.class)
                .setParameter("id",entidad.getId()).setParameter("actual",actual).getResultList()) {
            c.setPrincipal(false);c.setFechaModificacion(ahora());c.setUsuarioModificacion(autor(user));
            repository.auditar(user,empresa,"CONTACTO",c.getId(),"PRINCIPAL_DESMARCADO",null,
                    Map.of("activo",c.getActivo(),"principal",false));
        }
    }
    private void principalDireccion(Entidad entidad,int actual,int user,int empresa) {
        for (var d:em.createQuery("SELECT d FROM Direccion d WHERE d.entidad.id=:id AND d.principal=true AND d.id<>:actual",Direccion.class)
                .setParameter("id",entidad.getId()).setParameter("actual",actual).getResultList()) {
            d.setPrincipal(false);d.setFechaModificacion(ahora());d.setUsuarioModificacion(autor(user));
            repository.auditar(user,empresa,"DIRECCION",d.getId(),"PRINCIPAL_DESMARCADO",null,
                    Map.of("activo",d.getActivo(),"principal",false));
        }
    }
    private void aplicar(Contacto c,ContactoEntrada d,int user) {
        c.setNombre(d.nombre().strip());c.setApellidos(d.apellidos());c.setCargo(d.cargo());
        c.setDepartamento(d.departamento());c.setTelefono(d.telefono());c.setMovil(d.movil());
        c.setEmail(d.email());c.setObservaciones(d.observaciones());
        c.setPrincipal(d.principal());c.setActivo(d.activo());
        c.setFechaModificacion(ahora());c.setUsuarioModificacion(autor(user));
    }
    private void aplicar(Direccion d,DireccionEntrada x,int user) {
        d.setTipoDireccion(x.tipoDireccion().strip());d.setDireccion(x.direccion().strip());
        d.setCodigoPostal(x.codigoPostal());d.setPoblacion(x.poblacion());d.setProvincia(x.provincia());
        d.setPais(x.pais().strip());d.setTelefono(x.telefono());d.setObservaciones(x.observaciones());
        d.setPrincipal(x.principal());d.setActivo(x.activo());
        d.setFechaModificacion(ahora());d.setUsuarioModificacion(autor(user));
    }
    @Transactional
    public ContactoFicha guardarContacto(int user,int empresa,TipoTitular tipo,int titularId,Integer contactoId,@Valid ContactoEntrada data) {
        repository.serializar();
        Entidad entidad=titular(user,empresa,tipo,titularId,true);
        validarPrincipal(data.principal(),data.activo());
        Contacto c=contactoId==null?new Contacto():contacto(entidad,contactoId);
        if(contactoId==null){c.setEntidad(entidad);c.setFechaCreacion(ahora());c.setUsuarioCreacion(autor(user));}
        if(data.principal()) principalContacto(entidad,contactoId==null?-1:contactoId,user,empresa);
        aplicar(c,data,user);
        if(contactoId==null) em.persist(c);
        em.flush();
        repository.auditar(user,empresa,"CONTACTO",c.getId(),contactoId==null?"CREAR":"ACTUALIZAR",null,
                Map.of("activo",c.getActivo(),"principal",c.getPrincipal()));
        return ficha(titularId,c);
    }
    @Transactional
    public DireccionFicha guardarDireccion(int user,int empresa,TipoTitular tipo,int titularId,Integer direccionId,@Valid DireccionEntrada data) {
        repository.serializar();
        Entidad entidad=titular(user,empresa,tipo,titularId,true);
        validarPrincipal(data.principal(),data.activo());
        Direccion d=direccionId==null?new Direccion():direccion(entidad,direccionId);
        if(direccionId==null){d.setEntidad(entidad);d.setFechaCreacion(ahora());d.setUsuarioCreacion(autor(user));}
        if(data.principal()) principalDireccion(entidad,direccionId==null?-1:direccionId,user,empresa);
        aplicar(d,data,user);
        if(direccionId==null) em.persist(d);
        em.flush();
        repository.auditar(user,empresa,"DIRECCION",d.getId(),direccionId==null?"CREAR":"ACTUALIZAR",null,
                Map.of("activo",d.getActivo(),"principal",d.getPrincipal()));
        return ficha(titularId,d);
    }
}
