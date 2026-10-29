package com.empresa.api.maestros;

import com.empresa.api.common.ApiException;
import com.empresa.api.model.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import java.util.Set;

@Repository
public class MaestroRepository {
    private final EntityManager entities;
    public MaestroRepository(EntityManager entities) { this.entities = entities; }

    public void serializar() {
        entities.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(1146049102,4)",Integer.class).getResultList();
    }

    public Empresa empresa(int id) {
        return entities.createQuery("select e from Empresa e left join fetch e.entidad where e.id=:id and e.activo=true", Empresa.class)
                .setParameter("id", id).getResultStream().findFirst().orElseThrow(ApiException::noEncontrado);
    }

    public Set<Empresa> visibilidadCliente(int id, int origen) {
        var cliente = entities.find(Cliente.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (cliente == null || cliente.getEmpresasVisibles().stream().noneMatch(e -> e.getId() == origen)) {
            throw ApiException.noEncontrado();
        }
        return cliente.getEmpresasVisibles();
    }

    public Set<Empresa> visibilidadProveedor(int id, int origen) {
        var proveedor = entities.find(Proveedor.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (proveedor == null || proveedor.getEmpresasVisibles().stream().noneMatch(e -> e.getId() == origen)) {
            throw ApiException.noEncontrado();
        }
        return proveedor.getEmpresasVisibles();
    }

    public void registrar(int usuario, int origen, int destino, Integer cliente, Integer proveedor) {
        entities.createNativeQuery("""
                INSERT INTO API_Visibilidad_Auditoria(id_usuario,empresa_origen,empresa_destino,id_cliente,id_proveedor)
                VALUES (:usuario,:origen,:destino,:cliente,:proveedor)
                """).setParameter("usuario", usuario).setParameter("origen", origen).setParameter("destino", destino)
                .setParameter("cliente", cliente).setParameter("proveedor", proveedor).executeUpdate();
        entities.flush();
    }
}
