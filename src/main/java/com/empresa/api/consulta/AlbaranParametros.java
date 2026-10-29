package com.empresa.api.consulta;

import com.empresa.api.common.ApiException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import static com.empresa.api.consulta.ConsultaParametros.Tipo.ALBARANES;

record AlbaranParametros(ConsultaParametros pagina, Integer tercero, Integer relacion) {
    static AlbaranParametros parse(MultiValueMap<String, String> query, AlbaranTipo tipo, boolean anidado) {
        var resto = new LinkedMultiValueMap<String, String>(query);
        Integer tercero = extraer(resto, tipo.filtroTercero);
        Integer relacion = anidado ? null : extraer(resto, tipo.filtroRelacion);
        return new AlbaranParametros(ConsultaParametros.parse(resto, ALBARANES), tercero, relacion);
    }
    private static Integer extraer(MultiValueMap<String, String> query, String key) {
        if (!query.containsKey(key)) { return null; }
        var values = query.remove(key);
        if (values.size() != 1) { throw ApiException.invalido(key); }
        return ConsultaParametros.entero(values.getFirst(), 0, 1, Integer.MAX_VALUE, key);
    }
}
