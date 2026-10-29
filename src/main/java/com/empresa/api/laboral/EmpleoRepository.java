package com.empresa.api.laboral;

import com.empresa.api.common.ApiException;
import com.empresa.api.common.Pagina;
import com.empresa.api.model.AsignacionObra;
import com.empresa.api.model.Ausencia;
import com.empresa.api.model.VinculoEmpleado;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static com.empresa.api.laboral.EmpleoDtos.*;

@Repository
public class EmpleoRepository {
    private final JdbcTemplate jdbc;
    private final EntityManager entities;
    public EmpleoRepository(JdbcTemplate jdbc,EntityManager entities) { this.jdbc=jdbc; this.entities=entities; }
    // Compartido con la gestion de usuarios: evita cambios de autoridad durante una escritura.
    public void serializar() { jdbc.query("SELECT pg_advisory_xact_lock(1146049102,4)",rs -> {}); }
    public void guardar(Object entity) { entities.persist(entity); entities.flush(); }
    public void flush() { entities.flush(); }
    public void auditar(int autor,int empresa,int operario,String accion,int referencia) {
        jdbc.update("INSERT INTO API_Laboral_Auditoria(autor,empresa,operario,accion,referencia) VALUES (?,?,?,?,?)",
                autor,empresa,operario,accion,referencia);
    }
    private static final String EMPLEADO = """
            FROM Operarios op JOIN API_Operario_Empresa pe ON pe.id_operario=op.id_operario
            JOIN Entidades e ON e.id_entidad=op.id_entidad AND e.tipo_entidad='PERSONA'
            WHERE pe.id_empresa=? AND op.id_operario=?
            """;
    public void exigirEmpleado(int empresa,int operario) {
        if(jdbc.queryForObject("SELECT count(*) " + EMPLEADO, Integer.class,empresa,operario)!=1) throw ApiException.noEncontrado();
    }
    public void exigirPeriodoLaboral(int empresa,int operario,LocalDate desde,LocalDate hasta) {
        if(jdbc.queryForObject("SELECT count(*) " + EMPLEADO + " AND e.activo AND op.fecha_alta<=? AND (op.fecha_baja IS NULL OR (?::date IS NOT NULL AND op.fecha_baja>=?))",
                Integer.class,empresa,operario,desde,hasta,hasta)!=1)
            throw ApiException.conflicto("El periodo debe quedar dentro del alta y baja del empleado activo");
    }
    public Empleado propio(int usuario,int empresa,LocalDate hoy) {
        return jdbc.query("""
                SELECT op.id_operario,op.codigo_operario,NULLIF(TRIM(CONCAT_WS(' ',e.nombre,e.apellidos)),'')
                FROM API_Usuario_Operario v JOIN Operarios op ON op.id_operario=v.id_operario
                JOIN API_Operario_Empresa pe ON pe.id_operario=op.id_operario
                JOIN Entidades e ON e.id_entidad=op.id_entidad AND e.tipo_entidad='PERSONA'
                WHERE v.id_usuario=? AND pe.id_empresa=? AND e.activo
                  AND op.fecha_alta<=? AND (op.fecha_baja IS NULL OR op.fecha_baja>=?)
                """,(rs,n)->new Empleado(rs.getInt(1),rs.getString(2),rs.getString(3)),usuario,empresa,hoy,hoy)
                .stream().findFirst().orElseThrow(ApiException::noEncontrado);
    }
    public Vinculo vinculo(int operario) {
        return jdbc.query("SELECT id_usuario,id_operario FROM API_Usuario_Operario WHERE id_operario=?",
                (rs,n)->new Vinculo(rs.getInt(1),rs.getInt(2)),operario).stream().findFirst().orElse(null);
    }
    public boolean vincular(int operario,int usuario) {
        // Mismo orden de bloqueo que login/refresh: usuario antes de sesiones.
        var cuentas=jdbc.queryForList("SELECT activo FROM Usuarios WHERE id_usuario=? FOR UPDATE",Boolean.class,usuario);
        if(cuentas.isEmpty() || !cuentas.getFirst()) throw ApiException.noEncontrado();
        var actual=vinculo(operario);
        if(actual!=null && actual.usuarioId()==usuario) return false;
        if(actual!=null || entities.find(VinculoEmpleado.class,usuario)!=null)
            throw ApiException.conflicto("Cuenta o empleado ya vinculado; desvincular antes de reasignar");
        var v=new VinculoEmpleado(); v.setUsuario(usuario); v.setOperario(operario); guardar(v);
        revocar(usuario); return true;
    }
    public Integer desvincular(int operario) {
        var v=vinculo(operario); if(v==null) return null;
        jdbc.queryForList("SELECT id_usuario FROM Usuarios WHERE id_usuario=? FOR UPDATE",Integer.class,v.usuarioId());
        entities.remove(entities.find(VinculoEmpleado.class,v.usuarioId())); flush(); revocar(v.usuarioId()); return v.usuarioId();
    }
    private void revocar(int usuario) { jdbc.update("UPDATE API_Sesiones SET revocada=TRUE WHERE id_usuario=? AND NOT revocada",usuario); }
    private static final String OBRAS = """
            FROM Obras o JOIN API_Obra_Empresa oe ON oe.id_obra=o.id_obra
            JOIN Clientes c ON c.id_cliente=o.id_cliente
            JOIN API_Cliente_Empresa ce ON ce.id_cliente=c.id_cliente AND ce.id_empresa=oe.id_empresa
            JOIN Estados_Obra s ON s.id_estado=o.id_estado
            LEFT JOIN Direcciones d ON d.id_direccion=o.id_direccion AND d.id_entidad=c.id_entidad
            WHERE oe.id_empresa=?
            """;
    public void exigirObra(int empresa,int obra) {
        if(jdbc.queryForObject("SELECT count(*) " + OBRAS + " AND o.id_obra=?",Integer.class,empresa,obra)!=1) throw ApiException.noEncontrado();
    }
    public boolean solapaAsignacion(int empresa,int operario,int obra,LocalDate desde,LocalDate hasta) {
        return jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM API_Asignaciones_Obra WHERE id_empresa=? AND id_operario=? AND id_obra=?
                AND NOT cancelada AND desde<=COALESCE(?::date,'infinity'::date) AND (hasta IS NULL OR hasta>=?))
                """,Boolean.class,empresa,operario,obra,hasta,desde);
    }
    public boolean solapaAusencia(int empresa,int operario,LocalDate desde,LocalDate hasta) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM API_Ausencias WHERE id_empresa=? AND id_operario=? AND NOT cancelada AND desde<=? AND hasta>=?)",
                Boolean.class,empresa,operario,hasta,desde);
    }
    public AsignacionObra asignacion(int empresa,int operario,int id) {
        var a=entities.find(AsignacionObra.class,id);
        if(a==null || a.getEmpresa()!=empresa || a.getOperario()!=operario) throw ApiException.noEncontrado();
        return a;
    }
    public Ausencia ausencia(int empresa,int operario,int id) {
        var a=entities.find(Ausencia.class,id);
        if(a==null || a.getEmpresa()!=empresa || a.getOperario()!=operario) throw ApiException.noEncontrado();
        return a;
    }
    public Pagina<Asignacion> asignaciones(int empresa,int operario,EmpleoParametros p) {
        return pagina("id,id_operario,id_obra,desde,hasta,cancelada",
                "FROM API_Asignaciones_Obra WHERE id_empresa=? AND id_operario=? AND desde<=? AND (hasta IS NULL OR hasta>=?)",
                List.of(empresa,operario,p.hasta(),p.desde()),(rs,n)->new Asignacion(rs.getInt(1),rs.getInt(2),rs.getInt(3),
                        rs.getObject(4,LocalDate.class),rs.getObject(5,LocalDate.class),rs.getBoolean(6)),p,"id");
    }
    public Pagina<AusenciaDetalle> ausencias(int empresa,int operario,EmpleoParametros p) {
        return pagina("id,id_operario,desde,hasta,tipo,cancelada",
                "FROM API_Ausencias WHERE id_empresa=? AND id_operario=? AND desde<=? AND hasta>=?",
                List.of(empresa,operario,p.hasta(),p.desde()),(rs,n)->new AusenciaDetalle(rs.getInt(1),rs.getInt(2),
                        rs.getObject(3,LocalDate.class),rs.getObject(4,LocalDate.class),TipoAusencia.valueOf(rs.getString(5)),rs.getBoolean(6)),p,"id");
    }
    public Pagina<AusenciaDetallePrivado> ausenciasPrivadas(int empresa,int operario,EmpleoParametros p) {
        return pagina("id,id_operario,desde,hasta,tipo,cancelada,justificada,observaciones",
                "FROM API_Ausencias WHERE id_empresa=? AND id_operario=? AND desde<=? AND hasta>=?",
                List.of(empresa,operario,p.hasta(),p.desde()),(rs,n)->new AusenciaDetallePrivado(rs.getInt(1),rs.getInt(2),
                        rs.getObject(3,LocalDate.class),rs.getObject(4,LocalDate.class),
                        TipoAusencia.valueOf(rs.getString(5)),rs.getBoolean(6),
                        rs.getObject(7,Boolean.class),rs.getString(8)),p,"id");
    }
    private static final String ASIGNADA = """
             AND EXISTS(SELECT 1 FROM API_Asignaciones_Obra a WHERE a.id_empresa=oe.id_empresa
               AND a.id_obra=o.id_obra AND a.id_operario=? AND NOT a.cancelada
               AND a.desde<=? AND (a.hasta IS NULL OR a.hasta>=?))
            """;
    private static final String OBRA_COLUMNAS = "o.id_obra,o.codigo_obra,o.titulo,s.descripcion,o.fecha_inicio,o.fecha_fin_prevista,d.id_direccion,d.direccion,d.codigo_postal,d.poblacion,d.provincia,d.pais";
    private final RowMapper<ObraOperativa> obraMapper=(rs,n)->new ObraOperativa(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4),
            rs.getObject(5,LocalDate.class),rs.getObject(6,LocalDate.class),rs.getObject(7)==null?null:
            new Ubicacion(rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12)));
    public Pagina<ObraOperativa> obrasPropias(int empresa,int operario,LocalDate hoy,EmpleoParametros p) {
        return pagina(OBRA_COLUMNAS,OBRAS+ASIGNADA,List.of(empresa,operario,hoy,hoy),obraMapper,p,"o.id_obra");
    }
    public ObraOperativa obraPropia(int empresa,int operario,int obra,LocalDate hoy) {
        return jdbc.query("SELECT " + OBRA_COLUMNAS + " " + OBRAS + ASIGNADA + " AND o.id_obra=?",obraMapper,empresa,operario,hoy,hoy,obra)
                .stream().findFirst().orElseThrow(ApiException::noEncontrado);
    }
    public Pagina<ContactoOperativo> contactos(int empresa,int operario,int obra,LocalDate hoy,EmpleoParametros p) {
        String from="""
                FROM Obra_Contacto oc JOIN Contactos k ON k.id_contacto=oc.id_contacto AND k.activo
                JOIN Obras ob ON ob.id_obra=oc.id_obra JOIN Clientes cl ON cl.id_cliente=ob.id_cliente AND cl.id_entidad=k.id_entidad
                WHERE oc.id_obra=? AND EXISTS(SELECT 1
                """ + OBRAS + ASIGNADA + " AND o.id_obra=oc.id_obra)";
        return pagina("oc.id_obra_contacto,k.nombre,k.apellidos,k.telefono,k.movil",from,List.of(obra,empresa,operario,hoy,hoy),
                (rs,n)->new ContactoOperativo(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)),p,"oc.id_obra_contacto");
    }
    private <T> Pagina<T> pagina(String select,String from,List<Object> args,RowMapper<T> mapper,EmpleoParametros p,String orden) {
        long total=jdbc.queryForObject("SELECT count(*) " + from,Long.class,args.toArray());
        var pageArgs=new ArrayList<>(args); pageArgs.add(p.size()); pageArgs.add((long)p.page()*p.size());
        var items=jdbc.query("SELECT " + select + " " + from + " ORDER BY " + orden + " LIMIT ? OFFSET ?",mapper,pageArgs.toArray());
        return new Pagina<>(items,p.page(),p.size(),total,(total+p.size()-1)/p.size(),"id,asc");
    }
}