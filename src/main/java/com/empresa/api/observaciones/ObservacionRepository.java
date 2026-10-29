package com.empresa.api.observaciones;

import com.empresa.api.common.ApiException;
import com.empresa.api.common.Pagina;
import com.empresa.api.model.ObservacionObra;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static com.empresa.api.observaciones.ObservacionDtos.*;

@Repository
public class ObservacionRepository {
    private final EntityManager entities;
    private final JdbcTemplate jdbc;
    public ObservacionRepository(EntityManager entities,JdbcTemplate jdbc) { this.entities=entities; this.jdbc=jdbc; }

    public void guardar(ObservacionObra entity) { entities.persist(entity); entities.flush(); }
    public void flush() { entities.flush(); }
    public ObservacionObra buscar(int empresa,int obra,int id) {
        return entities.createQuery("select o from ObservacionObra o where o.empresa=:empresa and o.obra=:obra and o.id=:id",ObservacionObra.class)
                .setParameter("empresa",empresa).setParameter("obra",obra).setParameter("id",id)
                .getResultStream().findFirst().orElseThrow(ApiException::noEncontrado);
    }
    public void auditar(int id,int autor,String accion,Instant fecha) {
        jdbc.update("INSERT INTO API_Observaciones_Auditoria(observacion,autor,accion,fecha) VALUES (?,?,?,?)",id,autor,accion,Timestamp.from(fecha));
    }
    public Pagina<Resumen> listar(int empresa,int obra,ObservacionParametros p) {
        String from="FROM API_Observaciones_Obra WHERE id_empresa=? AND id_obra=?";
        var args=new ArrayList<Object>(List.of(empresa,obra));
        if(p.estado()!=null) { from+=" AND estado=?"; args.add(p.estado().name()); }
        long total=jdbc.queryForObject("SELECT count(*) " + from,Long.class,args.toArray());
        args.add(p.size()); args.add((long)p.page()*p.size());
        var items=jdbc.query("SELECT id,id_obra,autor,fecha_creacion,estado,resuelto_por,fecha_resolucion " + from + " ORDER BY id LIMIT ? OFFSET ?",
                (rs,n)->new Resumen(rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getTimestamp(4).toInstant(),Estado.valueOf(rs.getString(5)),
                        rs.getObject(6,Integer.class),rs.getTimestamp(7)==null?null:rs.getTimestamp(7).toInstant()),args.toArray());
        return new Pagina<>(items,p.page(),p.size(),total,(total+p.size()-1)/p.size(),"id,asc");
    }
}
