package com.empresa.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "Series_Documentos", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"id_empresa", "tipo_documento", "ejercicio", "nombre"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "tipoDocumento", "nombre", "prefijo", "ejercicio"})
public class SerieDocumento implements Serializable {

    @Column(name="id_empresa")
    private Integer empresa;
    @Column(nullable=false)
    private Boolean rectificativa=false;
    @Column(name="ultima_fecha")
    private java.time.LocalDate ultimaFecha;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_serie")
    @EqualsAndHashCode.Include
    private Integer id;

    @NotBlank
    @Size(max = 40)
    @Column(name = "tipo_documento", nullable = false, length = 40)
    private String tipoDocumento;

    @NotBlank
    @Size(max = 100)
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @NotBlank
    @Size(max = 20)
    @Column(name = "prefijo", nullable = false, length = 20)
    private String prefijo;

    @NotNull
    @Positive
    @Column(name = "siguiente_numero", nullable = false)
    private Integer siguienteNumero;

    @NotNull
    @Positive
    @Column(name = "longitud", nullable = false)
    private Integer longitud;

    @NotNull
    @Column(name = "ejercicio", nullable = false)
    private Integer ejercicio;

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
