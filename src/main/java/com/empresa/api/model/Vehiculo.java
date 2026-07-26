package com.empresa.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Vehiculos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "matricula", "marca", "modelo"})
public class Vehiculo implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_vehiculo")
    @EqualsAndHashCode.Include
    private Integer id;

    @NotBlank
    @Size(max = 15)
    @Column(name = "matricula", nullable = false, unique = true, length = 15)
    private String matricula;

    @NotBlank
    @Size(max = 100)
    @Column(name = "marca", nullable = false, length = 100)
    private String marca;

    @NotBlank
    @Size(max = 100)
    @Column(name = "modelo", nullable = false, length = 100)
    private String modelo;

    @Size(max = 100)
    @Column(name = "version", length = 100)
    private String version;

    @Size(max = 30)
    @Column(name = "combustible", length = 30)
    private String combustible;

    @Size(max = 30)
    @Column(name = "color", length = 30)
    private String color;

    @Column(name = "fecha_matriculacion")
    private LocalDate fechaMatriculacion;

    @NotNull
    @PositiveOrZero
    @Column(name = "kilometros", nullable = false)
    private Integer kilometros;

    @Column(name = "fecha_itv")
    private LocalDate fechaItv;

    @Column(name = "fecha_seguro")
    private LocalDate fechaSeguro;

    @Size(max = 100)
    @Column(name = "numero_poliza", length = 100)
    private String numeroPoliza;

    @Size(max = 100)
    @Column(name = "compania_seguro", length = 100)
    private String companiaSeguro;

    @Lob
    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @NotNull
    @Column(name = "activo", nullable = false)
    private Boolean activo;

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
