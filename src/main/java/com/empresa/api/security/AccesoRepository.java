package com.empresa.api.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class AccesoRepository {
    private final JdbcTemplate jdbc;

    public AccesoRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Cuenta(int id, String nombre, String hash, boolean activa) {
        @Override public String toString() { return "Cuenta[id=" + id + "]"; }
    }
    public record Sesion(String id, int usuario, String hash, int version, Instant expira, boolean revocada) {
        @Override public String toString() { return "Sesion[omitida]"; }
    }

    public Optional<Cuenta> cuenta(String nombre) {
        return jdbc.query("SELECT id_usuario, nombre_usuario, password_hash, activo FROM Usuarios WHERE nombre_usuario = ?",
                (rs, row) -> new Cuenta(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)), nombre)
                .stream().findFirst();
    }

    public Optional<Cuenta> bloquearCuenta(String nombre) {
        return jdbc.query("SELECT id_usuario,nombre_usuario,password_hash,activo FROM Usuarios WHERE nombre_usuario=? FOR UPDATE",
                (rs,row)->new Cuenta(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getBoolean(4)),nombre).stream().findFirst();
    }

    public void crearSesion(String id, int usuario, String hash, Instant expira) {
        jdbc.update("INSERT INTO API_Sesiones (id_sesion, id_usuario, refresh_hash, expira) VALUES (?, ?, ?, ?)",
                id, usuario, hash, Timestamp.from(expira));
        jdbc.update("UPDATE Usuarios SET ultimo_login = timezone('UTC', statement_timestamp()) WHERE id_usuario = ?", usuario);
    }

    public Optional<Sesion> bloquearSesion(String id) {
        // Mismo orden que gestion de cuentas: usuario antes de sesion.
        jdbc.query("SELECT id_usuario FROM Usuarios WHERE id_usuario=(SELECT id_usuario FROM API_Sesiones WHERE id_sesion=?) FOR UPDATE",
                (rs,row)->rs.getInt(1),id);
        return jdbc.query("""
                SELECT s.id_sesion, s.id_usuario, s.refresh_hash, s.version, s.expira,
                       (s.revocada OR NOT u.activo) AS revocada
                FROM API_Sesiones s JOIN Usuarios u ON u.id_usuario = s.id_usuario
                WHERE s.id_sesion = ? FOR UPDATE OF s
                """, (rs, row) -> new Sesion(rs.getString(1), rs.getInt(2), rs.getString(3),
                rs.getInt(4), rs.getTimestamp(5).toInstant(), rs.getBoolean(6)), id).stream().findFirst();
    }

    public boolean sesionVigente(String id, int usuario, int version, Instant ahora) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM API_Sesiones s JOIN Usuarios u ON u.id_usuario = s.id_usuario
                WHERE s.id_sesion = ? AND s.id_usuario = ? AND s.version = ?
                  AND NOT s.revocada AND s.expira > ? AND u.activo
                """, Integer.class, id, usuario, version, Timestamp.from(ahora)) == 1;
    }

    public void rotar(String id, String hash) {
        jdbc.update("UPDATE API_Sesiones SET refresh_hash = ?, version = version + 1 WHERE id_sesion = ?", hash, id);
    }

    public void revocar(String id, int usuario) {
        jdbc.update("UPDATE API_Sesiones SET revocada = TRUE WHERE id_sesion = ? AND id_usuario = ?", id, usuario);
    }

    public AuthDtos.Perfil perfil(int usuario) {
        return jdbc.queryForObject("SELECT id_usuario, nombre_usuario FROM Usuarios WHERE id_usuario = ?",
                (rs, row) -> new AuthDtos.Perfil(rs.getInt(1), rs.getString(2)), usuario);
    }

    // Solo los roles inscritos explicitamente tienen ambito global.
    private static final String ASIGNACIONES = """
            (SELECT id_usuario, id_empresa, id_rol FROM API_Accesos_Empresa
             UNION
             SELECT ur.id_usuario, e.id_empresa, ur.id_rol FROM Usuario_Rol ur
             JOIN API_Roles_Globales g ON g.id_rol = ur.id_rol
             CROSS JOIN API_Empresas e)
            """;
    private static final String EMPRESAS_AUTORIZADAS = """
            SELECT DISTINCT e.id_empresa, e.nombre FROM API_Empresas e
            JOIN %s a ON a.id_empresa = e.id_empresa
            JOIN Usuarios u ON u.id_usuario = a.id_usuario
            JOIN Roles r ON r.id_rol = a.id_rol
            JOIN Rol_Permiso rp ON rp.id_rol = r.id_rol
            JOIN Permisos p ON p.id_permiso = rp.id_permiso
            WHERE a.id_usuario = ? AND u.activo AND e.activo AND r.activo AND p.activo
              AND p.nombre = 'EMPRESA' AND rp.nivel_acceso IN ('LECTURA', 'ESCRITURA', 'ADMIN')
            """.formatted(ASIGNACIONES);

    public List<AuthDtos.Empresa> empresas(int usuario) {
        return jdbc.query(EMPRESAS_AUTORIZADAS + " ORDER BY e.id_empresa",
                (rs, row) -> new AuthDtos.Empresa(rs.getInt(1), rs.getString(2)), usuario);
    }

    public Optional<AuthDtos.Empresa> empresa(int usuario, int empresa) {
        return jdbc.query(EMPRESAS_AUTORIZADAS + " AND e.id_empresa = ?",
                (rs, row) -> new AuthDtos.Empresa(rs.getInt(1), rs.getString(2)), usuario, empresa)
                .stream().findFirst();
    }

    public boolean permiteLectura(int usuario, int empresa, String permiso) {
        return permite(usuario, empresa, permiso, false);
    }

    public boolean permiteEscritura(int usuario, int empresa, String permiso) {
        return permite(usuario, empresa, permiso, true);
    }

    public List<GestionUsuariosDtos.Nivel> permisosEfectivos(int usuario,int empresa) {
        return jdbc.query("""
                SELECT p.nombre, CASE max(CASE rp.nivel_acceso WHEN 'ADMIN' THEN 3 WHEN 'ESCRITURA' THEN 2 ELSE 1 END)
                  WHEN 3 THEN 'ADMIN' WHEN 2 THEN 'ESCRITURA' ELSE 'LECTURA' END
                FROM %s a JOIN API_Empresas e ON e.id_empresa=a.id_empresa
                JOIN Usuarios u ON u.id_usuario=a.id_usuario JOIN Roles r ON r.id_rol=a.id_rol
                JOIN Rol_Permiso rp ON rp.id_rol=r.id_rol JOIN Permisos p ON p.id_permiso=rp.id_permiso
                WHERE a.id_usuario=? AND a.id_empresa=? AND u.activo AND e.activo AND r.activo AND p.activo
                  AND rp.nivel_acceso IN ('LECTURA','ESCRITURA','ADMIN') GROUP BY p.nombre ORDER BY p.nombre
                """.formatted(ASIGNACIONES), (rs,row)->new GestionUsuariosDtos.Nivel(rs.getString(1),rs.getString(2)),usuario,empresa);
    }

    private boolean permite(int usuario, int empresa, String permiso, boolean escritura) {
        return jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM %s a
                JOIN API_Empresas e ON e.id_empresa = a.id_empresa
                JOIN Usuarios u ON u.id_usuario = a.id_usuario
                JOIN Roles r ON r.id_rol = a.id_rol
                JOIN Rol_Permiso rp ON rp.id_rol = r.id_rol
                JOIN Permisos p ON p.id_permiso = rp.id_permiso
                WHERE a.id_usuario = ? AND a.id_empresa = ? AND p.nombre = ?
                  AND u.activo AND e.activo AND r.activo AND p.activo
                  AND rp.nivel_acceso IN ('LECTURA', 'ESCRITURA', 'ADMIN')
                  AND (? = FALSE OR rp.nivel_acceso IN ('ESCRITURA', 'ADMIN')))
                """.formatted(ASIGNACIONES), Boolean.class, usuario, empresa, permiso, escritura);
    }
}
