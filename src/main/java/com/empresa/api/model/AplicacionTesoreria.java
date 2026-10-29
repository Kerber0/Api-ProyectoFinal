package com.empresa.api.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name="API_Tesoreria_Aplicaciones") @Getter @Setter @NoArgsConstructor
public class AplicacionTesoreria {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @Column(nullable=false) private Integer movimiento;
    @Column(nullable=false) private Integer vencimiento;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal importe;
    private Integer cobro;
    private Integer pago;
    @Column(name="cobro_reversion") private Integer cobroReversion;
    @Column(name="pago_reversion") private Integer pagoReversion;
}
