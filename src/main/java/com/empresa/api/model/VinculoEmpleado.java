package com.empresa.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="API_Usuario_Operario") @Getter @Setter
public class VinculoEmpleado {
    @Id @Column(name="id_usuario") private Integer usuario;
    @Column(name="id_operario",nullable=false,unique=true) private Integer operario;
}
