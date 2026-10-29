package com.empresa.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.io.Serializable;

@Entity
@Table(name = "API_Empresas")
@Getter @Setter @NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(of = {"id", "nombre"})
public class Empresa implements Serializable {
    @Id @Column(name = "id_empresa") @EqualsAndHashCode.Include
    private Integer id;
    @NotBlank @Column(nullable = false, length = 150)
    private String nombre;
    @NotNull @Column(nullable = false)
    private Boolean activo;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entidad", unique = true)
    private Entidad entidad;
}
