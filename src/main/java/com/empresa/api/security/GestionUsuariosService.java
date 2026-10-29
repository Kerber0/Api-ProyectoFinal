package com.empresa.api.security;

import com.empresa.api.common.ApiException;
import com.empresa.api.common.Pagina;
import com.empresa.api.model.Usuario;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly=true)
public class GestionUsuariosService {
    private final GestionUsuariosRepository repository;
    private final AccesoRepository accesos;
    private final AutorizacionService autorizacion;
    private final PasswordEncoder passwords;
    private final Clock clock;
    public GestionUsuariosService(GestionUsuariosRepository repository, AccesoRepository accesos,
                                  AutorizacionService autorizacion,PasswordEncoder passwords,Clock clock) {
        this.repository=repository; this.accesos=accesos; this.autorizacion=autorizacion;
        this.passwords=passwords; this.clock=clock;
    }
    private void exigir(int autor,boolean escritura) {
        if(!repository.permiteGlobal(autor,escritura)) throw ApiException.noEncontrado();
    }
    private void comenzar(int autor) {
        exigir(autor,true);
        repository.serializarGestion();
        // Revalidar despues de esperar: otro administrador pudo retirar el acceso.
        exigir(autor,true);
    }
    private void conservarAdministrador() {
        if(!repository.hayAdministrador()) throw ApiException.conflicto("Debe conservarse un administrador global activo");
    }
    private void validarNombre(String nombre,int excepto) {
        if(nombre==null || nombre.isBlank() || nombre.length()>50 || !nombre.equals(nombre.strip())
                || nombre.codePoints().anyMatch(Character::isISOControl)) throw ApiException.invalido("usuario");
        if(repository.nombreOcupado(nombre,excepto)) throw ApiException.conflicto("Nombre de usuario no disponible");
    }
    private void validarPassword(String password) {
        if(password==null || password.isBlank() || password.codePointCount(0,password.length())<12
                || password.getBytes(StandardCharsets.UTF_8).length>72) throw ApiException.invalido("password");
    }
    private LocalDateTime ahora() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC); }
    private GestionUsuariosDtos.Cuenta dto(Usuario u) {
        return new GestionUsuariosDtos.Cuenta(u.getId(),u.getNombreUsuario(),u.getActivo());
    }
    public Pagina<GestionUsuariosDtos.Cuenta> listar(int autor,MultiValueMap<String,String> raw) {
        exigir(autor,false);
        raw.forEach((key,values)-> { if(!Set.of("page","size").contains(key)||values.size()!=1) throw ApiException.invalido("query"); });
        int page=entero(raw.getFirst("page"),0,0,100000,"page");
        int size=entero(raw.getFirst("size"),20,1,100,"size");
        long total=repository.total();
        return new Pagina<>(repository.cuentas(page,size),page,size,total,(total+size-1)/size,"id,asc");
    }
    private int entero(String value,int fallback,int min,int max,String campo) {
        if(value==null) return fallback;
        try { int result=Integer.parseInt(value); if(result<min||result>max) throw ApiException.invalido(campo); return result; }
        catch(NumberFormatException ex) { throw ApiException.invalido(campo); }
    }
    public GestionUsuariosDtos.Cuenta detalle(int autor,int id) { exigir(autor,false); return repository.cuenta(id); }
    public List<GestionUsuariosDtos.Asignacion> asignaciones(int autor,int id) {
        exigir(autor,false); repository.cuenta(id); return repository.accesos(id);
    }
    public List<GestionUsuariosDtos.Rol> roles(int autor) { exigir(autor,false); return repository.roles(); }
    public List<GestionUsuariosDtos.Permiso> permisos(int autor) { exigir(autor,false); return repository.permisos(); }
    public List<GestionUsuariosDtos.Nivel> efectivos(int autor,int empresa) {
        autorizacion.exigirLectura(autor,empresa,"EMPRESA"); return accesos.permisosEfectivos(autor,empresa);
    }
    @Transactional
    public GestionUsuariosDtos.Cuenta crear(int autor,GestionUsuariosDtos.Crear request) {
        comenzar(autor); validarNombre(request.usuario(),0); validarPassword(request.password());
        var u=new Usuario(); u.setNombreUsuario(request.usuario()); u.setPasswordHash(passwords.encode(request.password()));
        u.setActivo(true); u.setFechaCreacion(ahora()); u.setFechaModificacion(ahora());
        u.setUsuarioCreacion(repository.referencia(autor)); u.setUsuarioModificacion(repository.referencia(autor));
        repository.crear(u); repository.auditar(autor,u.getId(),"CREAR",null,null);
        return dto(u);
    }
    @Transactional
    public GestionUsuariosDtos.Cuenta actualizar(int autor,int id,GestionUsuariosDtos.Actualizar request) {
        comenzar(autor); var u=repository.bloquear(id); validarNombre(request.usuario(),id);
        if(request.activo()==null) throw ApiException.invalido("activo");
        if(!u.getNombreUsuario().equals(request.usuario()) || !u.getActivo().equals(request.activo())) {
            u.setNombreUsuario(request.usuario()); u.setActivo(request.activo());
            u.setFechaModificacion(ahora()); u.setUsuarioModificacion(repository.referencia(autor));
            repository.guardar(); conservarAdministrador(); repository.revocarSesiones(id);
            repository.auditar(autor,id,"ACTUALIZAR",null,null);
        }
        return dto(u);
    }
    @Transactional
    public void password(int autor,int id,GestionUsuariosDtos.Password request) {
        comenzar(autor); var u=repository.bloquear(id); validarPassword(request.password());
        u.setPasswordHash(passwords.encode(request.password())); u.setFechaModificacion(ahora());
        u.setUsuarioModificacion(repository.referencia(autor)); repository.guardar(); repository.revocarSesiones(id);
        repository.auditar(autor,id,"CAMBIAR_PASSWORD",null,null);
    }
    @Transactional
    public void asignar(int autor,int id,Integer empresa,int rol,boolean conceder) {
        comenzar(autor); var u=repository.bloquear(id); repository.validarAsignacion(empresa,rol,conceder);
        if(conceder && !u.getActivo()) throw ApiException.conflicto("No se conceden accesos a una cuenta inactiva");
        if(repository.asignar(id,empresa,rol,conceder)) {
            conservarAdministrador(); repository.revocarSesiones(id);
            repository.auditar(autor,id,conceder?"CONCEDER_ROL":"REVOCAR_ROL",empresa,rol);
        }
    }
}
