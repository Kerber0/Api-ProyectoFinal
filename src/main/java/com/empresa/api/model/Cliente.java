package com.empresa.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Clientes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "codigoCliente"})
public class Cliente implements Serializable {

    @Column(name = "version_gestion", nullable = false)
    private long versionGestion;

    /** Visibilidad del maestro; no transfiere la propiedad de sus documentos. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "API_Cliente_Empresa",
            joinColumns = @JoinColumn(name = "id_cliente"),
            inverseJoinColumns = @JoinColumn(name = "id_empresa"))
    private java.util.Set<Empresa> empresasVisibles = new java.util.HashSet<>();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    @EqualsAndHashCode.Include
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entidad", nullable = false, unique = true)
    private Entidad entidad;

    @NotBlank
    @Size(max = 20)
    @Column(name = "codigo_cliente", nullable = false, unique = true, length = 20)
    private String codigoCliente;

    @NotNull
    @PositiveOrZero
    @Column(name = "descuento", nullable = false, precision = 5, scale = 2)
    private BigDecimal descuento;

    @PositiveOrZero
    @Column(name = "limite_credito", precision = 12, scale = 2)
    private BigDecimal limiteCredito;

    @Size(max = 34)
    @Column(name = "iban", length = 34)
    private String iban;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_zona")
    private Zona zona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_forma_pago")
    private FormaPago formaPago;

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
