package com.empresa.api.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;

@Entity @Table(name="API_Tesoreria") @Getter @Setter @NoArgsConstructor
public class MovimientoTesoreria {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @Column(name="id_empresa",nullable=false) private Integer empresa;
    @Column(nullable=false,length=10) private String tipo;
    @Column(nullable=false) private Integer tercero;
    @Column(nullable=false,length=36) private String clave;
    @Column(nullable=false,length=64) private String huella;
    @Column(nullable=false) private LocalDate fecha;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal importe;
    @Column(nullable=false,length=20) private String metodo;
    @Column(nullable=false,length=12) private String estado="REGISTRADO";
    @Column(length=100) private String referencia;
    private Instant conciliado;
    @Column(length=500) private String motivo;
    @Column(name="fecha_reversion") private LocalDate fechaReversion;
    @Column(nullable=false) private Integer autor;
    @Column(nullable=false) private Instant creado;
}
