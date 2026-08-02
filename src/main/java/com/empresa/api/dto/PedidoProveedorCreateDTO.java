package com.empresa.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoProveedorCreateDTO {

    @NotNull
    private Integer idSerie;

    @NotNull
    private LocalDate fecha;

    private String observaciones;

    @NotNull
    private Integer idEstado;

    @NotNull
    private Integer idProveedor;

    @Valid
    @NotNull
    private List<LineaPedidoCreateDTO> lineas;
}
