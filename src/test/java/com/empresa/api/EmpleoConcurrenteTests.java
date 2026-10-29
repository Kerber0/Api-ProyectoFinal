package com.empresa.api;

import com.empresa.api.common.ApiException;
import com.empresa.api.laboral.EmpleoService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;
import static com.empresa.api.laboral.EmpleoDtos.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class EmpleoConcurrenteTests extends PostgreSqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EmpleoService service;
    @Autowired Clock clock;
    @ParameterizedTest @ValueSource(booleans={false,true})
    void altasSolapadasConcurrentesConfirmanSoloUna(boolean ausencia) throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("dorna_test");
        int admin=jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,'inutilizable') RETURNING id_usuario",
                Integer.class,"test-ec-"+UUID.randomUUID().toString().replace("-",""));
        var hoy=LocalDate.now(clock);
        try(var executor=Executors.newFixedThreadPool(2)) {
            jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre='ADMIN'",admin);
            var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
            Callable<Integer> task=()-> {
                ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Coordinacion agotada");
                try {
                    if(ausencia) service.ausencia(admin,1,301,new NuevaAusencia(hoy,hoy,TipoAusencia.PERMISO));
                    else service.asignar(admin,1,301,new NuevaAsignacion(1001,hoy,hoy));
                    return 201;
                } catch(ApiException ex) { return ex.status(); }
            };
            var first=executor.submit(task); var second=executor.submit(task);
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(java.util.List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Laboral_Auditoria WHERE autor=?",Integer.class,admin)).isEqualTo(1);
        } finally {
            // Solo registros creados por la cuenta temporal de este caso.
            jdbc.update("DELETE FROM API_Ausencias WHERE id IN (SELECT referencia FROM API_Laboral_Auditoria WHERE autor=? AND accion='CREAR_AUSENCIA')",admin);
            jdbc.update("DELETE FROM API_Asignaciones_Obra WHERE id IN (SELECT referencia FROM API_Laboral_Auditoria WHERE autor=? AND accion='CREAR_ASIGNACION')",admin);
            jdbc.update("DELETE FROM API_Laboral_Auditoria WHERE autor=?",admin);
            jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?",admin);
            jdbc.update("DELETE FROM Usuarios WHERE id_usuario=?",admin);
        }
    }
}
