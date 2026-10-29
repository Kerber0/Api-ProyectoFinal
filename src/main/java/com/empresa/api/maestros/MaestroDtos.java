package com.empresa.api.maestros;

public final class MaestroDtos {
    private MaestroDtos() {}
    public record FichaEmpresa(int id, String nombre, DatosFiscales datosFiscales) {}
    public record DatosFiscales(String razonSocial, String nifCif, String telefono, String email, String web) {}
}
