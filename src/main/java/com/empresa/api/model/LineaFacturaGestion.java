package com.empresa.api.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name="API_Facturacion_Lineas") @Getter @Setter @NoArgsConstructor
public class LineaFacturaGestion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="documento") private FacturaGestion documento;
    @Column(nullable=false) private Integer posicion;
    @Column(nullable=false,length=300) private String descripcion;
    @Column(nullable=false,precision=10,scale=2) private BigDecimal cantidad;
    @Column(nullable=false,precision=10,scale=2) private BigDecimal precio;
    @Column(nullable=false,precision=5,scale=2) private BigDecimal descuento;
    @Column(nullable=false,precision=5,scale=2) private BigDecimal iva;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal bruto;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal rebaja;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal base;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal cuota;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal total;
}
