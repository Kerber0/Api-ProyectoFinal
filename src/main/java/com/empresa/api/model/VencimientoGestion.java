package com.empresa.api.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name="API_Vencimientos") @Getter @Setter @NoArgsConstructor
public class VencimientoGestion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @Column(nullable=false) private Integer documento;
    @Column(nullable=false) private Integer posicion;
    @Column(nullable=false) private LocalDate fecha;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal importe;
}
