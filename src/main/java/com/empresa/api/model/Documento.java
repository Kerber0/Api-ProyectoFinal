package com.empresa.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@SecondaryTable(name = "API_Archivo_Documento", pkJoinColumns = @PrimaryKeyJoinColumn(name = "id_documento"))
@Table(name = "Documentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "nombre", "ruta"})
public class Documento implements Serializable {

    /** Propietaria exclusiva; compartir maestros no modifica esta relacion. */
    @jakarta.validation.constraints.NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_empresa", table = "API_Archivo_Documento", nullable = false, updatable = false)
    private Empresa empresa;

    @NotBlank
    @Size(min = 36, max = 36)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(name = "clave", table = "API_Archivo_Documento", nullable = false, length = 36, columnDefinition = "char(36)", updatable = false)
    private String claveArchivo;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_documento")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_obra", nullable = false)
    private Obra obra;

    @NotBlank
    @Size(max = 200)
    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @NotBlank
    @Size(max = 500)
    @Column(name = "ruta", nullable = false, length = 500)
    private String ruta;

    @Size(max = 50)
    @Column(name = "tipo", length = 50)
    private String tipo;

    @NotNull
    @Column(name = "fecha_subida", nullable = false)
    private LocalDateTime fechaSubida;
    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

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
