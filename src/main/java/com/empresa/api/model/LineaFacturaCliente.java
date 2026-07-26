package com.empresa.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Lineas_Factura_Cliente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "descripcion", "cantidad", "precio"})
public class LineaFacturaCliente implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_linea")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_factura", nullable = false)
    private FacturaCliente factura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referencia_articulo")
    private Articulo articulo;

    @Size(max = 300)
    @Column(name = "descripcion", length = 300)
    private String descripcion;

    @Column(name = "cantidad", precision = 10, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "precio", precision = 10, scale = 2)
    private BigDecimal precio;

    @PositiveOrZero
    @Column(name = "descuento", precision = 5, scale = 2)
    private BigDecimal descuento;

    @PositiveOrZero
    @Column(name = "iva", precision = 5, scale = 2)
    private BigDecimal iva;

    @PositiveOrZero
    @Column(name = "importe", precision = 12, scale = 2)
    private BigDecimal importe;

    @NotNull
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @NotNull
    @Column(name = "fecha_modificacion", nullable = false)
    private LocalDateTime fechaModificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_creacion", referencedColumnName = "id_usuario")
    private Usuario usuarioCreacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_modificacion", referencedColumnName = "id_usuario")
    private Usuario usuarioModificacion;
}
