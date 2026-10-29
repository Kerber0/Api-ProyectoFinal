package com.empresa.api.gestion;

import com.empresa.api.common.ApiException;
import com.empresa.api.model.*;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;

@Repository
public class GestionRepository {
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public GestionRepository(EntityManager em, JdbcTemplate jdbc, ObjectMapper json) { this.em=em; this.jdbc=jdbc; this.json=json; }
    public void serializar() { jdbc.query("SELECT pg_advisory_xact_lock(1146049102,4)",rs -> {}); }
    public void guardar(Object o) { em.persist(o); em.flush(); }
    public void flush() { em.flush(); }
    /** Maestros comunes: incorpora la visibilidad en todas las empresas activas del grupo. */
    public Set<Empresa> empresasActivas() {
        var ids = jdbc.queryForList("SELECT id_empresa FROM API_Empresas WHERE activo = TRUE ORDER BY id_empresa", Integer.class);
        var resultado = new LinkedHashSet<Empresa>();
        for (Integer id : ids) { resultado.add(referencia(Empresa.class, id)); }
        return resultado;
    }
    public <T> T referencia(Class<T> clase,int id) { var e=em.find(clase,id); if(e==null) throw ApiException.noEncontrado(); return e; }
    public Cliente cliente(int empresa,int id) {
        if(!existe("SELECT count(*) FROM API_Cliente_Empresa WHERE id_empresa=? AND id_cliente=?",empresa,id)) throw ApiException.noEncontrado();
        return referencia(Cliente.class,id);
    }
    public Proveedor proveedor(int empresa,int id) {
        if(!existe("SELECT count(*) FROM API_Proveedor_Empresa WHERE id_empresa=? AND id_proveedor=?",empresa,id)) throw ApiException.noEncontrado();
        return referencia(Proveedor.class,id);
    }
    public Operario operario(int empresa,int id) {
        var o=referencia(Operario.class,id); if(o.getEmpresa().getId()!=empresa) throw ApiException.noEncontrado(); return o;
    }
    public Obra obra(int empresa,int id) {
        var o=referencia(Obra.class,id); if(o.getEmpresa().getId()!=empresa) throw ApiException.noEncontrado();
        cliente(empresa,o.getCliente().getId()); return o;
    }
    public ObraContacto contactoObra(int obra,int id) {
        var relacion=referencia(ObraContacto.class,id);
        if(relacion.getObra().getId()!=obra) throw ApiException.noEncontrado();
        return relacion;
    }
    public boolean contactoObraDuplicado(int obra,int contacto,String rol,int excluir) {
        return existe("SELECT count(*) FROM Obra_Contacto WHERE id_obra=? AND id_contacto=? AND rol_en_obra=? AND id_obra_contacto<>?",
                obra,contacto,rol,excluir);
    }
    public void desvincularContactoObra(ObraContacto relacion) { em.remove(relacion); }
    public Presupuesto presupuesto(int empresa,int id) {
        var p=referencia(Presupuesto.class,id); if(p.getEmpresa().getId()!=empresa) throw ApiException.noEncontrado();
        cliente(empresa,p.getCliente().getId());
        if(p.getObra()!=null && obra(empresa,p.getObra().getId()).getCliente().getId().intValue()!=p.getCliente().getId()) throw ApiException.noEncontrado();
        return p;
    }
    private boolean existe(String sql,Object... args) { return jdbc.queryForObject(sql,Integer.class,args)>0; }
    public void identidadExclusiva(Entidad e) {
        int usos=jdbc.queryForObject("""
            SELECT (SELECT count(*) FROM Clientes WHERE id_entidad=?) +
                   (SELECT count(*) FROM Proveedores WHERE id_entidad=?) +
                   (SELECT count(*) FROM Operarios WHERE id_entidad=?) +
                   (SELECT count(*) FROM API_Empresas WHERE id_entidad=?) +
                   (SELECT count(*) FROM Usuarios WHERE id_entidad=?)
            """,Integer.class,e.getId(),e.getId(),e.getId(),e.getId(),e.getId());
        if(usos>1) throw ApiException.conflicto("La identidad tiene otros usos; requiere gestion conjunta");
    }
    public void nifLibre(String nif,Integer entidad) {
        if(nif==null) return;
        if(existe("SELECT count(*) FROM Entidades WHERE nif_cif=? AND id_entidad<>?",nif,entidad==null?0:entidad))
            throw ApiException.conflicto("Identificacion ya registrada");
    }
    public void codigoLibre(String tipo,String codigo,Integer id) {
        // Solo nombres internos; nunca recibe identificadores SQL desde HTTP.
        String tabla=switch(tipo){case "CLIENTE"->"Clientes";case "PROVEEDOR"->"Proveedores";case "OPERARIO"->"Operarios";default->throw new IllegalArgumentException();};
        String campo=tipo.toLowerCase(Locale.ROOT);
        if(existe("SELECT count(*) FROM "+tabla+" WHERE codigo_"+campo+"=? AND id_"+campo+"<>?",codigo,id==null?0:id)) throw ApiException.conflicto("Codigo ya registrado");
    }
    public SerieDocumento serie(int empresa,GestionDtos.TipoSerie tipo,int id) {
        var s=referencia(SerieDocumento.class,id);
        if(!Objects.equals(s.getEmpresa(),empresa)||!s.getTipoDocumento().equals(tipo.name())||!s.getActivo()||s.getRectificativa()) throw ApiException.noEncontrado();
        return s;
    }
    public boolean serieDuplicada(int empresa,GestionDtos.TipoSerie tipo,String nombre,String prefijo,int ejercicio) {
        return existe("SELECT count(*) FROM Series_Documentos WHERE id_empresa=? AND tipo_documento=? AND ejercicio=? AND (nombre=? OR prefijo=?)",empresa,tipo.name(),ejercicio,nombre,prefijo);
    }
    public com.empresa.api.common.Pagina<GestionDtos.SerieFicha> series(int empresa,GestionDtos.TipoSerie tipo,int page,int size) {
        long total=jdbc.queryForObject("SELECT count(*) FROM Series_Documentos WHERE id_empresa=? AND tipo_documento=?",Long.class,empresa,tipo.name());
        var items=jdbc.query("SELECT id_serie,nombre,prefijo,ejercicio,longitud,siguiente_numero FROM Series_Documentos WHERE id_empresa=? AND tipo_documento=? ORDER BY id_serie LIMIT ? OFFSET ?",
            (rs,n)->new GestionDtos.SerieFicha(rs.getInt(1),tipo,rs.getString(2),rs.getString(3),rs.getInt(4),rs.getInt(5),rs.getInt(6)),empresa,tipo.name(),size,(long)page*size);
        return new com.empresa.api.common.Pagina<>(items,page,size,total,(total+size-1)/size,"id,asc");
    }
    public EstadoObra estadoObra(String nombre) {
        return em.createQuery("select e from EstadoObra e where e.descripcion=:nombre and e.activo=true",EstadoObra.class).setParameter("nombre",nombre).getResultStream().findFirst().orElseThrow(ApiException::noEncontrado);
    }
    public EstadoFactura estadoPresupuesto(String nombre) {
        return em.createQuery("select e from EstadoFactura e where e.descripcion=:nombre and e.activo=true",EstadoFactura.class).setParameter("nombre",nombre).getResultStream().findFirst().orElseThrow(ApiException::noEncontrado);
    }
    public void periodoOperario(int operario,LocalDate alta,LocalDate baja) {
        if(existe("""
            SELECT count(*) FROM (
                SELECT desde,hasta FROM API_Asignaciones_Obra WHERE id_operario=? AND NOT cancelada
                UNION ALL SELECT desde,hasta FROM API_Ausencias WHERE id_operario=? AND NOT cancelada
                UNION ALL SELECT fecha,fecha FROM Partes_Mano_Obra WHERE id_operario=?
            ) p WHERE desde<? OR (?::date IS NOT NULL AND (hasta IS NULL OR hasta>?))
            """,operario,operario,operario,alta,baja,baja)) throw ApiException.conflicto("Existen asignaciones, ausencias o partes fuera del periodo laboral");
    }
    public void auditar(int autor,int empresa,String entidad,int id,String accion,Map<String,?> antes,Map<String,?> despues) {
        flush();
        var cambios=new LinkedHashMap<String,Object>(); cambios.put("referencias",Map.of()); cambios.put("antes",antes); cambios.put("despues",despues);
        jdbc.update("INSERT INTO API_Gestion_Auditoria(autor,empresa,entidad,entidad_id,accion,cambios) VALUES (?,?,?,?,?,?::jsonb)",autor,empresa,entidad,id,accion,json.writeValueAsString(cambios));
    }
}
