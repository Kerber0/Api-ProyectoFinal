package com.empresa.api.documentos;

import com.empresa.api.common.ApiException;
import com.empresa.api.common.Pagina;
import com.empresa.api.model.ArchivoDocumento;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.MultiValueMap;
import java.util.Set;
import static com.empresa.api.documentos.DocumentoDtos.*;

@Repository
public class DocumentoRepository {
    private final EntityManager entities;
    private final JdbcTemplate jdbc;
    public DocumentoRepository(EntityManager entities,JdbcTemplate jdbc) { this.entities=entities; this.jdbc=jdbc; }
    public void guardar(ArchivoDocumento d) {
        entities.persist(d); entities.flush();
        jdbc.update("INSERT INTO API_Documentos_Auditoria(documento,autor,accion,fecha) VALUES (?,?,'CREAR',?)",
                d.getId(),d.getAutor(),java.sql.Timestamp.from(d.getFecha()));
    }
    public int empleadoAusencia(int empresa,int ausencia,boolean escritura) {
        return jdbc.query("SELECT id_operario FROM API_Ausencias WHERE id_empresa=? AND id=?"+(escritura?" AND NOT cancelada":""),
                (rs,n)->rs.getInt(1),empresa,ausencia).stream().findFirst().orElseThrow(ApiException::noEncontrado);
    }
    public ArchivoDocumento buscar(int empresa,DocumentoTipo tipo,int referencia,int id) {
        var d=entities.find(ArchivoDocumento.class,id);
        if(d==null || d.getEmpresa()!=empresa) throw ApiException.noEncontrado();
        Integer padre=switch(tipo) { case OBRA -> d.getObra(); case PRESUPUESTO -> d.getPresupuesto(); case AUSENCIA -> d.getAusencia(); };
        if(padre==null || padre!=referencia) throw ApiException.noEncontrado();
        return d;
    }
    public Pagina<Detalle> listar(int empresa,DocumentoTipo tipo,int referencia,MultiValueMap<String,String> query) {
        query.forEach((key,values)->{ if(!Set.of("page","size").contains(key) || values.size()!=1) throw ApiException.invalido("query"); });
        int page=numero(query.getFirst("page"),0,0,100000),size=numero(query.getFirst("size"),20,1,100);
        String from=" FROM API_Documentos WHERE id_empresa=? AND "+tipo.columna+"=?";
        long total=jdbc.queryForObject("SELECT count(*)"+from,Long.class,empresa,referencia);
        var items=jdbc.query("SELECT id,nombre,mime,tamano,sha256,autor,fecha"+from+" ORDER BY id LIMIT ? OFFSET ?",
                (rs,n)->new Detalle(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getInt(4),rs.getString(5),rs.getInt(6),rs.getTimestamp(7).toInstant()),
                empresa,referencia,size,(long)page*size);
        return new Pagina<>(items,page,size,total,(total+size-1)/size,"id,asc");
    }
    private int numero(String raw,int fallback,int min,int max) {
        if(raw==null) return fallback;
        try { int value=Integer.parseInt(raw); if(value<min || value>max) throw ApiException.invalido("query"); return value; }
        catch(NumberFormatException ex) { throw ApiException.invalido("query"); }
    }
}
