package com.empresa.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@SecondaryTable(name = "API_Operario_Empresa", pkJoinColumns = @PrimaryKeyJoinColumn(name = "id_operario"))
@Table(name = "Operarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "codigoOperario"})
public class Operario implements Serializable {

    @Column(name = "version_gestion", nullable = false)
    private long versionGestion;

    /** Propietaria exclusiva; compartir maestros no modifica esta relacion. */
    @jakarta.validation.constraints.NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_empresa", table = "API_Operario_Empresa", nullable = false, updatable = false)
    private Empresa empresa;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_operario")
    @EqualsAndHashCode.Include
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entidad", nullable = false, unique = true)
    private Entidad entidad;

    @NotBlank
    @Size(max = 20)
    @Column(name = "codigo_operario", nullable = false, unique = true, length = 20)
    private String codigoOperario;

    @NotNull
    @Column(name = "fecha_alta", nullable = false)
    private LocalDate fechaAlta;

    @Column(name = "fecha_baja")
    private LocalDate fechaBaja;

    @Size(max = 30)
    @Column(name = "nss", unique = true, length = 30)
    private String nss;

    @Positive
    @Column(name = "horas_semanales", precision = 5, scale = 2)
    private BigDecimal horasSemanales;

    @Positive
    @Column(name = "coste_hora_real", precision = 10, scale = 2)
    private BigDecimal costeHoraReal;

    @Size(max = 34)
    @Column(name = "iban", length = 34)
    private String iban;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria")
    private CategoriaOperario categoria;

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
