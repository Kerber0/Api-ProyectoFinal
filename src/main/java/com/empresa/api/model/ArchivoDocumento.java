package com.empresa.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="API_Documentos") @Getter @Setter
public class ArchivoDocumento {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @Column(name="id_empresa",nullable=false,updatable=false) private Integer empresa;
    @Column(updatable=false) private Integer obra;
    @Column(updatable=false) private Integer presupuesto;
    @Column(updatable=false) private Integer ausencia;
    @Column(nullable=false,length=200,updatable=false) private String nombre;
    @Column(nullable=false,length=40,updatable=false) private String mime;
    @Column(nullable=false,updatable=false) private Integer tamano;
    @Column(nullable=false,length=64,updatable=false) private String sha256;
    @Column(nullable=false,length=36,updatable=false) private String clave;
    @Column(nullable=false,length=30,updatable=false) private String proveedor;
    @Column(nullable=false,updatable=false) private Integer autor;
    @Column(nullable=false,updatable=false) private Instant fecha;
}
