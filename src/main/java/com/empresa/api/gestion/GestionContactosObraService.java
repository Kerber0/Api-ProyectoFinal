package com.empresa.api.gestion;

import com.empresa.api.common.ApiException;
import com.empresa.api.model.*;
import com.empresa.api.security.AutorizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.Map;
import java.util.Objects;
import static com.empresa.api.gestion.GestionContactosObraDtos.*;

@Service
@Transactional(readOnly=true)
public class GestionContactosObraService {
    private final GestionRepository repository;
    private final AutorizacionService acceso;
    private final Clock clock;
    public GestionContactosObraService(GestionRepository repository,AutorizacionService acceso,Clock clock) {
        this.repository=repository; this.acceso=acceso; this.clock=clock;
    }
    private void permiso(int usuario,int empresa) {
        acceso.exigirLectura(usuario,empresa,"EMPRESA");
        acceso.exigirEscritura(usuario,empresa,"OBRAS");
        acceso.exigirLectura(usuario,empresa,"CLIENTES");
    }
    private Obra comenzar(int usuario,int empresa,int obra) {
        permiso(usuario,empresa);
        repository.serializar();
        permiso(usuario,empresa);
        return repository.obra(empresa,obra);
    }
    private Contacto contacto(Obra obra,int id) {
        var c=repository.referencia(Contacto.class,id);
        if(!c.getEntidad().getId().equals(obra.getCliente().getEntidad().getId())) throw ApiException.noEncontrado();
        if(!Boolean.TRUE.equals(c.getActivo()) || !Boolean.TRUE.equals(c.getEntidad().getActivo()))
            throw ApiException.conflicto("Contacto o cliente inactivo");
        return c;
    }
    private String rol(String valor) {
        if(valor==null || valor.isBlank() || valor.length()>80) throw ApiException.invalido("rol");
        return valor.strip();
    }
    private String observaciones(String valor) {
        if(valor!=null && valor.length()>1000) throw ApiException.invalido("observaciones");
        return valor==null || valor.isBlank() ? null : valor.strip();
    }
    private void comprobarDuplicado(ObraContacto r,String rol) {
        if(repository.contactoObraDuplicado(r.getObra().getId(),r.getContacto().getId(),rol,r.getId()==null?0:r.getId()))
            throw ApiException.conflicto("El contacto ya tiene ese rol en la obra");
    }
    private Relacion ficha(ObraContacto r) {
        return new Relacion(r.getId(),r.getObra().getId(),r.getContacto().getId(),r.getRolEnObra(),r.getObservaciones());
    }
    private Map<String,?> referencias(ObraContacto r) {
        return Map.of("relacionId",r.getId(),"contactoId",r.getContacto().getId());
    }
    private void autor(ObraContacto r,int usuario,boolean nuevo) {
        var ahora=LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);
        var autor=repository.referencia(Usuario.class,usuario);
        r.setFechaModificacion(ahora); r.setUsuarioModificacion(autor);
        if(nuevo) { r.setFechaCreacion(ahora); r.setUsuarioCreacion(autor); }
    }
    public Relacion consultar(int usuario,int empresa,int obra,int relacion) {
        permiso(usuario,empresa); repository.obra(empresa,obra);
        return ficha(repository.contactoObra(obra,relacion));
    }
    @Transactional
    public Relacion asignar(int usuario,int empresa,int obra,Asignar datos) {
        var o=comenzar(usuario,empresa,obra);
        if(datos.contactoId()==null || datos.contactoId()<1) throw ApiException.invalido("contactoId");
        var r=new ObraContacto(); r.setObra(o); r.setContacto(contacto(o,datos.contactoId()));
        r.setRolEnObra(rol(datos.rol())); r.setObservaciones(observaciones(datos.observaciones()));
        comprobarDuplicado(r,r.getRolEnObra()); autor(r,usuario,true); repository.guardar(r);
        repository.auditar(usuario,empresa,"OBRA",obra,"CONTACTO_ASIGNAR",null,referencias(r));
        return ficha(r);
    }
    @Transactional
    public Relacion editar(int usuario,int empresa,int obra,int relacion,Editar datos) {
        var o=comenzar(usuario,empresa,obra);
        var r=repository.contactoObra(obra,relacion); contacto(o,r.getContacto().getId());
        String rol=rol(datos.rol()), notas=observaciones(datos.observaciones());
        comprobarDuplicado(r,rol);
        boolean cambiaRol=!Objects.equals(rol,r.getRolEnObra()), cambianNotas=!Objects.equals(notas,r.getObservaciones());
        if(cambiaRol || cambianNotas) {
            var antes=referencias(r);
            r.setRolEnObra(rol); r.setObservaciones(notas); autor(r,usuario,false);
            repository.auditar(usuario,empresa,"OBRA",obra,"CONTACTO_MODIFICAR",antes,
                    Map.of("relacionId",r.getId(),"contactoId",r.getContacto().getId(),
                            "rolModificado",cambiaRol,"observacionesModificadas",cambianNotas));
        }
        return ficha(r);
    }
    @Transactional
    public void desvincular(int usuario,int empresa,int obra,int relacion) {
        comenzar(usuario,empresa,obra);
        var r=repository.contactoObra(obra,relacion); var antes=referencias(r);
        // Tambien permite retirar una relacion historica con un contacto ya inactivo.
        repository.desvincularContactoObra(r);
        repository.auditar(usuario,empresa,"OBRA",obra,"CONTACTO_DESVINCULAR",antes,null);
    }
}
