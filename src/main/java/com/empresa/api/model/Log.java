package com.empresa.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "Log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "fecha", "accion", "tablaAfectada", "idRegistro"})
public class Log implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_log")
    @EqualsAndHashCode.Include
    private Integer id;

    @NotNull
    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @NotBlank
    @Size(max = 150)
    @Column(name = "accion", nullable = false, length = 150)
    private String accion;

    @Size(max = 100)
    @Column(name = "tabla_afectada", length = 100)
    private String tablaAfectada;

    @Column(name = "id_registro")
    private Integer idRegistro;
    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Size(max = 45)
    @Column(name = "ip", length = 45)
    private String ip;
}
