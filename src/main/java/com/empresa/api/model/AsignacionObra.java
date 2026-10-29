package com.empresa.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity @Table(name="API_Asignaciones_Obra") @Getter @Setter
public class AsignacionObra {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @Column(name="id_empresa",nullable=false,updatable=false) private Integer empresa;
    @Column(name="id_operario",nullable=false,updatable=false) private Integer operario;
    @Column(name="id_obra",nullable=false,updatable=false) private Integer obra;
    @Column(nullable=false,updatable=false) private LocalDate desde;
    @Column(updatable=false) private LocalDate hasta;
    @Column(nullable=false) private boolean cancelada;
}
