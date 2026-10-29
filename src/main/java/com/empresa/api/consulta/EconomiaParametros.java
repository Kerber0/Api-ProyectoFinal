package com.empresa.api.consulta;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import com.empresa.api.common.ApiException;
import static com.empresa.api.consulta.ConsultaParametros.Tipo.*;

record EconomiaParametros(ConsultaParametros pagina, Integer tercero, Integer obra, Integer factura) {
    static EconomiaParametros parse(MultiValueMap<String, String> query, EconomiaTipo tipo,
                                     ConsultaParametros.Tipo coleccion, boolean anidado) {
        var resto = new LinkedMultiValueMap<String, String>(query);
        Integer tercero = null, obra = null, factura = null;
        if (!anidado) {
            tercero = extraer(resto, tipo.filtroTercero);
            if (tipo == EconomiaTipo.CLIENTE) { obra = extraer(resto, "obra"); }
            if (coleccion == MOVIMIENTOS) { factura = extraer(resto, "factura"); }
        }
        return new EconomiaParametros(ConsultaParametros.parse(resto, coleccion), tercero, obra, factura);
    }
    private static Integer extraer(MultiValueMap<String, String> query, String key) {
        if (!query.containsKey(key)) { return null; }
        var values = query.remove(key);
        if (values.size() != 1) { throw ApiException.invalido(key); }
        return ConsultaParametros.entero(values.getFirst(), 0, 1, Integer.MAX_VALUE, key);
    }
}
