package com.empresa.api.security;

import com.empresa.api.common.ApiException;
import com.empresa.api.model.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class GestionUsuariosRepository {
    private final JdbcTemplate jdbc;
    private final EntityManager entities;
    public GestionUsuariosRepository(JdbcTemplate jdbc, EntityManager entities) {
        this.jdbc=jdbc; this.entities=entities;
    }

    public boolean permiteGlobal(int usuario, boolean escritura) {
        return jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM Usuarios u
            JOIN Usuario_Rol ur ON ur.id_usuario=u.id_usuario
            JOIN API_Roles_Globales g ON g.id_rol=ur.id_rol
            JOIN Roles r ON r.id_rol=g.id_rol
            JOIN Rol_Permiso rp ON rp.id_rol=r.id_rol
            JOIN Permisos p ON p.id_permiso=rp.id_permiso
            WHERE u.id_usuario=? AND u.activo AND r.activo AND p.activo
              AND p.nombre='USUARIOS' AND rp.nivel_acceso IN ('LECTURA','ESCRITURA','ADMIN')
              AND (NOT ? OR rp.nivel_acceso='ADMIN'))
            """, Boolean.class, usuario, escritura);
    }

    public void serializarGestion() {
        // Espacio propio de la aplicacion; serializa cambios de privilegios y ultimo administrador.
        jdbc.query("SELECT pg_advisory_xact_lock(1146049102,4)", rs -> {});
    }

    public Usuario bloquear(int id) {
        var usuario=entities.find(Usuario.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(usuario==null) throw ApiException.noEncontrado();
        return usuario;
    }
    public Usuario referencia(int id) { return entities.getReference(Usuario.class,id); }
    public void crear(Usuario usuario) { entities.persist(usuario); entities.flush(); }
    public void guardar() { entities.flush(); }

    public boolean nombreOcupado(String nombre, int excepto) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM Usuarios WHERE nombre_usuario=? AND id_usuario<>?)",
                Boolean.class,nombre,excepto);
    }
    public long total() { return jdbc.queryForObject("SELECT count(*) FROM Usuarios",Long.class); }
    public List<GestionUsuariosDtos.Cuenta> cuentas(int page,int size) {
        return jdbc.query("SELECT id_usuario,nombre_usuario,activo FROM Usuarios ORDER BY id_usuario LIMIT ? OFFSET ?",
                (rs,n)->new GestionUsuariosDtos.Cuenta(rs.getInt(1),rs.getString(2),rs.getBoolean(3)),size,(long)page*size);
    }
    public GestionUsuariosDtos.Cuenta cuenta(int id) {
        return jdbc.query("SELECT id_usuario,nombre_usuario,activo FROM Usuarios WHERE id_usuario=?",
                (rs,n)->new GestionUsuariosDtos.Cuenta(rs.getInt(1),rs.getString(2),rs.getBoolean(3)),id)
                .stream().findFirst().orElseThrow(ApiException::noEncontrado);
    }
    public List<GestionUsuariosDtos.Permiso> permisos() {
        return jdbc.query("SELECT id_permiso,nombre,activo FROM Permisos ORDER BY id_permiso",
                (rs,n)->new GestionUsuariosDtos.Permiso(rs.getInt(1),rs.getString(2),rs.getBoolean(3)));
    }
    public List<GestionUsuariosDtos.Rol> roles() {
        var niveles=jdbc.query("""
                SELECT rp.id_rol,p.nombre,rp.nivel_acceso FROM Rol_Permiso rp
                JOIN Permisos p ON p.id_permiso=rp.id_permiso WHERE p.activo ORDER BY p.nombre
                """, (rs,n)->new NivelRol(rs.getInt(1),new GestionUsuariosDtos.Nivel(rs.getString(2),rs.getString(3))));
        return jdbc.query("""
                SELECT r.id_rol,r.nombre,r.activo,EXISTS(SELECT 1 FROM API_Roles_Globales g WHERE g.id_rol=r.id_rol)
                FROM Roles r ORDER BY r.id_rol
                """, (rs,n)-> {
                    int id=rs.getInt(1);
                    return new GestionUsuariosDtos.Rol(id,rs.getString(2),rs.getBoolean(3),rs.getBoolean(4),
                            niveles.stream().filter(p->p.rol()==id).map(NivelRol::nivel).toList());
                });
    }
    private record NivelRol(int rol,GestionUsuariosDtos.Nivel nivel) {}
    public List<GestionUsuariosDtos.Asignacion> accesos(int id) {
        return accesos(id, false);
    }
    public List<GestionUsuariosDtos.Asignacion> accesosVigentes(int id) {
        return accesos(id, true);
    }
    private List<GestionUsuariosDtos.Asignacion> accesos(int id, boolean soloVigentes) {
        return jdbc.query("""
                SELECT a.id_empresa,a.id_rol,r.nombre,false AS global FROM API_Accesos_Empresa a
                JOIN Usuarios u ON u.id_usuario=a.id_usuario
                JOIN API_Empresas e ON e.id_empresa=a.id_empresa
                JOIN Roles r ON r.id_rol=a.id_rol
                WHERE a.id_usuario=? AND (NOT ? OR (u.activo AND e.activo AND r.activo))
                UNION ALL
                SELECT NULL::integer,ur.id_rol,r.nombre,true FROM Usuario_Rol ur
                JOIN Usuarios u ON u.id_usuario=ur.id_usuario
                JOIN API_Roles_Globales g ON g.id_rol=ur.id_rol
                JOIN Roles r ON r.id_rol=ur.id_rol
                WHERE ur.id_usuario=? AND (NOT ? OR (u.activo AND r.activo))
                ORDER BY global DESC,id_empresa,id_rol
                """, (rs,n)->new GestionUsuariosDtos.Asignacion(rs.getObject(1,Integer.class),rs.getInt(2),rs.getString(3),rs.getBoolean(4)),
                id,soloVigentes,id,soloVigentes);
    }
    public void validarAsignacion(Integer empresa,int rol,boolean conceder) {
        if(empresa!=null && !jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM API_Empresas WHERE id_empresa=? AND (NOT ? OR activo))",
                Boolean.class,empresa,conceder)) throw ApiException.noEncontrado();
        if(!jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM Roles r WHERE r.id_rol=? AND (NOT ? OR r.activo)
                AND (? OR EXISTS(SELECT 1 FROM API_Roles_Globales g WHERE g.id_rol=r.id_rol)))
                """,Boolean.class,rol,conceder,empresa!=null)) throw ApiException.noEncontrado();
    }
    public boolean asignar(int usuario,Integer empresa,int rol,boolean conceder) {
        int changes;
        if(empresa==null) {
            changes=conceder ? jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) VALUES (?,?) ON CONFLICT DO NOTHING",usuario,rol)
                    : jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=? AND id_rol=?",usuario,rol);
        } else {
            changes=conceder ? jdbc.update("INSERT INTO API_Accesos_Empresa(id_usuario,id_empresa,id_rol) VALUES (?,?,?) ON CONFLICT DO NOTHING",usuario,empresa,rol)
                    : jdbc.update("DELETE FROM API_Accesos_Empresa WHERE id_usuario=? AND id_empresa=? AND id_rol=?",usuario,empresa,rol);
        }
        return changes!=0;
    }
    public boolean hayAdministrador() {
        return jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM Usuarios u JOIN Usuario_Rol ur ON ur.id_usuario=u.id_usuario
            JOIN API_Roles_Globales g ON g.id_rol=ur.id_rol JOIN Roles r ON r.id_rol=g.id_rol
            JOIN Rol_Permiso rp ON rp.id_rol=r.id_rol JOIN Permisos p ON p.id_permiso=rp.id_permiso
            WHERE u.activo AND r.activo AND p.activo AND p.nombre='USUARIOS' AND rp.nivel_acceso='ADMIN')
            """,Boolean.class);
    }
    public void revocarSesiones(int usuario) {
        jdbc.update("UPDATE API_Sesiones SET revocada=TRUE WHERE id_usuario=? AND NOT revocada",usuario);
    }
    public void auditar(int autor,int usuario,String accion,Integer empresa,Integer rol) {
        jdbc.update("INSERT INTO API_Usuarios_Auditoria(autor,usuario,accion,empresa,rol) VALUES (?,?,?,?,?)",
                autor,usuario,accion,empresa,rol);
    }
}
