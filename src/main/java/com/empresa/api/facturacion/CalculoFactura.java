package com.empresa.api.facturacion;

import com.empresa.api.common.ApiException;
import com.empresa.api.model.LineaFacturaGestion;
import java.math.*;
import static com.empresa.api.facturacion.FacturacionDtos.*;

/** EUR, dos decimales, HALF_UP por linea; no se recalculan importes historicos. */
public final class CalculoFactura {
    private CalculoFactura() {}
    public static String texto(String value,int max,String campo) {
        if(value==null || value.isBlank() || value.codePointCount(0,value.length())>max || value.codePoints().anyMatch(Character::isISOControl)) throw ApiException.invalido(campo);
        return value.strip();
    }
    private static BigDecimal decimal(String value,String campo,boolean porcentaje) {
        if(value==null || !value.matches("[0-9]{1,8}(\\.[0-9]{1,2})?")) throw ApiException.invalido(campo);
        BigDecimal n=new BigDecimal(value).setScale(2);
        if(porcentaje && n.compareTo(new BigDecimal("100"))>0) throw ApiException.invalido(campo);
        return n;
    }
    public static BigDecimal dinero(BigDecimal n) {
        n=n.setScale(2,RoundingMode.HALF_UP);
        if(n.abs().compareTo(new BigDecimal("9999999999.99"))>0) throw ApiException.invalido("importes");
        return n;
    }
    public static LineaFacturaGestion linea(EntradaLinea e,int posicion) {
        if(e==null) throw ApiException.invalido("lineas");
        var l=new LineaFacturaGestion(); l.setPosicion(posicion); l.setDescripcion(texto(e.descripcion(),300,"descripcion"));
        l.setCantidad(decimal(e.cantidad(),"cantidad",false)); l.setPrecio(decimal(e.precio(),"precio",false));
        if(l.getCantidad().signum()<=0) throw ApiException.invalido("cantidad");
        l.setDescuento(decimal(e.descuento(),"descuento",true)); l.setIva(decimal(e.iva(),"iva",true));
        l.setBruto(dinero(l.getCantidad().multiply(l.getPrecio())));
        l.setRebaja(dinero(l.getBruto().multiply(l.getDescuento()).movePointLeft(2)));
        l.setBase(l.getBruto().subtract(l.getRebaja()));
        l.setCuota(dinero(l.getBase().multiply(l.getIva()).movePointLeft(2)));
        l.setTotal(dinero(l.getBase().add(l.getCuota()))); return l;
    }
}
