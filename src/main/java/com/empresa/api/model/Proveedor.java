package com.empresa.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "Proveedores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "codigoProveedor"})
public class Proveedor implements Serializable {

    @Column(name = "version_gestion", nullable = false)
    private long versionGestion;

    /** Visibilidad del maestro; no transfiere la propiedad de sus documentos. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "API_Proveedor_Empresa",
            joinColumns = @JoinColumn(name = "id_proveedor"),
            inverseJoinColumns = @JoinColumn(name = "id_empresa"))
    private java.util.Set<Empresa> empresasVisibles = new java.util.HashSet<>();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proveedor")
    @EqualsAndHashCode.Include
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entidad", nullable = false, unique = true)
    private Entidad entidad;

    @NotBlank
    @Size(max = 20)
    @Column(name = "codigo_proveedor", nullable = false, unique = true, length = 20)
    private String codigoProveedor;

    @NotBlank
    @Size(max = 20)
    @Column(name = "tipo_proveedor", nullable = false, length = 20)
    private String tipoProveedor;

    @Size(max = 34)
    @Column(name = "iban", length = 34)
    private String iban;

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
