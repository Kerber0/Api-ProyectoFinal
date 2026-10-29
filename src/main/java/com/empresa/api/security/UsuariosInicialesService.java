package com.empresa.api.security;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Profile({"provisionar", "test"})
public class UsuariosInicialesService {
    public static final Map<String, Integer> EMPRESAS = Map.of(
            "ADMIN-DORNA", 1, "ADMIN-SOLUTION", 2, "ADMIN-ANROD", 3, "ADMIN-KELTOR", 4);
    public static final List<String> USUARIOS = List.of("ADMIN", "SOCIO", "ADMIN-DORNA", "ADMIN-SOLUTION", "ADMIN-ANROD", "ADMIN-KELTOR");
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;

    public UsuariosInicialesService(JdbcTemplate jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    @Transactional
    public void provisionar(Map<String, String> claves) {
        if (!List.of("dorna_dev", "dorna_test").contains(jdbc.queryForObject("SELECT current_database()", String.class))
                || jdbc.queryForObject("SELECT COUNT(*) FROM dorna_demo_metadata WHERE id=1", Integer.class) != 1) {
            throw new IllegalStateException("La provision local requiere una base DEMO identificada");
        }
        if (!claves.keySet().equals(java.util.Set.copyOf(USUARIOS))
                || claves.values().stream().anyMatch(p -> p == null || !p.matches("[a-fA-F0-9]{64}"))) {
            throw new IllegalArgumentException("Se requieren los seis usuarios y claves aleatorias de 32 bytes");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM API_Empresas WHERE id_empresa IN (1,2,3,4) AND activo", Integer.class) != 4) {
            throw new IllegalStateException("Faltan empresas activas para los usuarios iniciales");
        }
        for (String nombre : USUARIOS) {
            var existentes = jdbc.query("SELECT id_usuario, password_hash, activo FROM Usuarios WHERE nombre_usuario=?",
                    (rs, row) -> new AccesoRepository.Cuenta(rs.getInt(1), nombre, rs.getString(2), rs.getBoolean(3)), nombre);
            if (!existentes.isEmpty() && (!existentes.getFirst().activa()
                    || !passwords.matches(claves.get(nombre), existentes.getFirst().hash()))) {
                throw new IllegalStateException("Usuario existente sin credencial local coincidente o inactivo: " + nombre);
            }
            if (existentes.isEmpty()) {
                jdbc.update("INSERT INTO Usuarios (nombre_usuario,password_hash) VALUES (?,?)", nombre, passwords.encode(claves.get(nombre)));
            }
            int usuario = jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario=?", Integer.class, nombre);
            String rol = EMPRESAS.containsKey(nombre) ? "LECTURA_EMPRESA" : nombre;
            int rolId = jdbc.queryForObject("SELECT id_rol FROM Roles WHERE nombre=? AND activo", Integer.class, rol);
            var globales = jdbc.queryForList("SELECT id_rol FROM Usuario_Rol WHERE id_usuario=?", Integer.class, usuario);
            var locales = jdbc.queryForList("SELECT CONCAT(id_empresa, ':', id_rol) FROM API_Accesos_Empresa WHERE id_usuario=?", String.class, usuario);
            boolean global = !EMPRESAS.containsKey(nombre);
            if (global ? globales.equals(List.of(rolId)) && locales.isEmpty()
                    : globales.isEmpty() && locales.equals(List.of(EMPRESAS.get(nombre) + ":" + rolId))) { continue; }
            // Converger solo las seis cuentas solicitadas; retirar posibles permisos anteriores.
            jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?", usuario);
            jdbc.update("DELETE FROM API_Accesos_Empresa WHERE id_usuario=?", usuario);
            if (global) {
                jdbc.update("INSERT INTO Usuario_Rol (id_usuario,id_rol) VALUES (?,?)", usuario, rolId);
            } else {
                jdbc.update("INSERT INTO API_Accesos_Empresa (id_usuario,id_empresa,id_rol) VALUES (?,?,?)", usuario, EMPRESAS.get(nombre), rolId);
            }
            jdbc.update("UPDATE API_Sesiones SET revocada=TRUE WHERE id_usuario=?", usuario);
        }
    }
}
