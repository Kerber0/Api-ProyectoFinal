package com.empresa.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity @Table(name="API_Ausencias") @Getter @Setter
public class Ausencia {
    public enum Tipo { VACACIONES, PERMISO, BAJA, FALTA, OTRA }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @Column(name="id_empresa",nullable=false,updatable=false) private Integer empresa;
    @Column(name="id_operario",nullable=false,updatable=false) private Integer operario;
    @Column(nullable=false,updatable=false) private LocalDate desde;
    @Column(nullable=false,updatable=false) private LocalDate hasta;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) private Tipo tipo;
    @Column(name="justificada") private Boolean justificada;
    @Column(name="observaciones",length=1000) private String observaciones;
    @Column(nullable=false) private boolean cancelada;
}