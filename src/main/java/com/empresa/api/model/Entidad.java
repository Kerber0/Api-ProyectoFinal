package com.empresa.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "Entidades")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "tipoEntidad", "nombre", "apellidos", "razonSocial", "nombreComercial", "nifCif"})
public class Entidad implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_entidad")
    @EqualsAndHashCode.Include
    private Integer id;

    @NotNull
    @Size(max = 10)
    @Column(name = "tipo_entidad", nullable = false, length = 10)
    private String tipoEntidad;

    @Size(max = 100)
    @Column(name = "nombre", length = 100)
    private String nombre;

    @Size(max = 150)
    @Column(name = "apellidos", length = 150)
    private String apellidos;

    @Size(max = 150)
    @Column(name = "razon_social", length = 150)
    private String razonSocial;

    @Size(max = 150)
    @Column(name = "nombre_comercial", length = 150)
    private String nombreComercial;

    @Size(max = 20)
    @Column(name = "nif_cif", unique = true, length = 20)
    private String nifCif;

    @Size(max = 20)
    @Column(name = "telefono_fijo", length = 20)
    private String telefonoFijo;

    @Size(max = 20)
    @Column(name = "movil", length = 20)
    private String movil;

    @Email
    @Size(max = 150)
    @Column(name = "email", length = 150)
    private String email;

    @Size(max = 150)
    @Column(name = "web", length = 150)
    private String web;

    @Past
    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @NotNull
    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @NotNull
    @Column(name = "fecha_alta", nullable = false)
    private LocalDate fechaAlta;

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

    @OneToMany(mappedBy = "entidad", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Direccion> direcciones;

    @OneToMany(mappedBy = "entidad", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Contacto> contactos;
}
