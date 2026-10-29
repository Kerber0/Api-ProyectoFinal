package com.empresa.api.auditoria;

import com.empresa.api.common.ApiException;
import com.empresa.api.common.Pagina;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.ArrayList;
import static com.empresa.api.auditoria.AuditoriaDtos.*;

@Repository
public class AuditoriaRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<Evento> mapper;
    public AuditoriaRepository(JdbcTemplate jdbc,ObjectMapper json) {
        this.jdbc=jdbc;
        mapper=(rs,n)->new Evento(rs.getLong("id"),Modulo.valueOf(rs.getString("modulo")),rs.getLong("origen_id"),
                rs.getInt("autor"),rs.getObject("empresa",Integer.class),rs.getString("entidad"),rs.getInt("entidad_id"),
                rs.getString("accion"),rs.getTimestamp("fecha").toInstant(),rs.getBoolean("historico"),json.readTree(rs.getString("cambios")));
    }
    public boolean permite(int usuario) {
        return jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM Usuarios u
            JOIN Usuario_Rol ur ON ur.id_usuario=u.id_usuario
            JOIN API_Roles_Globales g ON g.id_rol=ur.id_rol
            JOIN Roles r ON r.id_rol=g.id_rol
            JOIN Rol_Permiso rp ON rp.id_rol=r.id_rol
            JOIN Permisos p ON p.id_permiso=rp.id_permiso
            WHERE u.id_usuario=? AND u.activo AND r.activo AND p.activo
              AND p.nombre='AUDITORIA' AND rp.nivel_acceso='ADMIN')
            """,Boolean.class,usuario);
    }
    public Pagina<Evento> listar(AuditoriaParametros p) {
        String from="FROM API_Auditoria WHERE 1=1";
        var args=new ArrayList<Object>();
        if(p.empresa()!=null) { from+=" AND empresa=?"; args.add(p.empresa()); }
        if(p.autor()!=null) { from+=" AND autor=?"; args.add(p.autor()); }
        if(p.modulo()!=null) { from+=" AND modulo=?"; args.add(p.modulo().name()); }
        if(p.desde()!=null) { from+=" AND fecha>=?"; args.add(Timestamp.from(p.desde())); }
        if(p.hasta()!=null) { from+=" AND fecha<?"; args.add(Timestamp.from(p.hasta())); }
        long total=jdbc.queryForObject("SELECT count(*) "+from,Long.class,args.toArray());
        args.add(p.size()); args.add((long)p.page()*p.size());
        var items=jdbc.query("SELECT * "+from+" ORDER BY fecha DESC,id DESC LIMIT ? OFFSET ?",mapper,args.toArray());
        return new Pagina<>(items,p.page(),p.size(),total,(total+p.size()-1)/p.size(),"fecha,desc;id,desc");
    }
    public Evento detalle(long id) {
        return jdbc.query("SELECT * FROM API_Auditoria WHERE id=?",mapper,id).stream().findFirst().orElseThrow(ApiException::noEncontrado);
    }
}
