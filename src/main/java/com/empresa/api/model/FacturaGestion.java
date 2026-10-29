package com.empresa.api.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Ciclo de preparacion y trazabilidad; la factura emitida reutiliza el dominio economico. */
@Entity @Table(name="API_Facturacion") @Getter @Setter @NoArgsConstructor
public class FacturaGestion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @Column(name="id_empresa",nullable=false) private Integer empresa;
    @Column(nullable=false,length=10) private String tipo;
    @Column(name="id_serie",nullable=false) private Integer serie;
    private Integer cliente;
    private Integer proveedor;
    private Integer obra;
    @Column(nullable=false) private LocalDate fecha;
    private LocalDate vencimiento;
    @Column(name="referencia_externa",length=100) private String referenciaExterna;
    @Column(nullable=false,length=12) private String estado="BORRADOR";
    private Integer original;
    @Column(length=500) private String motivo;
    @Column(name="factura_cliente") private Integer facturaCliente;
    @Column(name="factura_proveedor") private Integer facturaProveedor;
    @Column(length=30) private String numero;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal subtotal;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal descuento;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal impuestos;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal total;
    @Column(name="empresa_nombre",nullable=false,length=250) private String empresaNombre;
    @Column(name="empresa_nif",nullable=false,length=20) private String empresaNif;
    @Column(name="tercero_nombre",nullable=false,length=250) private String terceroNombre;
    @Column(name="tercero_nif",nullable=false,length=20) private String terceroNif;
    @Column(nullable=false) private Integer autor;
    @Column(nullable=false) private Instant creado;
    private Instant emitido;
    @OneToMany(mappedBy="documento",cascade=CascadeType.PERSIST)
    @OrderBy("posicion ASC") private List<LineaFacturaGestion> lineas=new ArrayList<>();
}
