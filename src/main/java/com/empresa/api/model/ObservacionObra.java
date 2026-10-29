package com.empresa.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="API_Observaciones_Obra") @Getter @Setter
public class ObservacionObra {
    public enum Estado { ABIERTA, RESUELTA }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @Column(name="id_empresa",nullable=false,updatable=false) private Integer empresa;
    @Column(name="id_obra",nullable=false,updatable=false) private Integer obra;
    @Column(nullable=false,updatable=false) private Integer autor;
    @Column(name="fecha_creacion",nullable=false,updatable=false) private Instant fechaCreacion;
    @Column(nullable=false,updatable=false,columnDefinition="text") private String texto;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private Estado estado=Estado.ABIERTA;
    @Column(name="resuelto_por") private Integer resueltoPor;
    @Column(name="fecha_resolucion") private Instant fechaResolucion;
    @Column(columnDefinition="text") private String resolucion;
}
