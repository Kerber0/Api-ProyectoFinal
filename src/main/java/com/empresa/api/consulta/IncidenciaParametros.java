package com.empresa.api.consulta;

import com.empresa.api.common.ApiException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

record IncidenciaParametros(ConsultaParametros pagina, String prioridad) {
    static IncidenciaParametros parse(MultiValueMap<String, String> query, String padre) {
        var resto = new LinkedMultiValueMap<String, String>(query);
        if (padre != null && resto.containsKey(padre)) { throw ApiException.invalido(padre); }
        String prioridad = "";
        if (resto.containsKey("prioridad")) {
            var values = resto.remove("prioridad");
            if (values.size() != 1 || values.getFirst() == null || values.getFirst().length() > 300) {
                throw ApiException.invalido("prioridad");
            }
            prioridad = values.getFirst().trim();
        }
        return new IncidenciaParametros(ConsultaParametros.parse(resto, ConsultaParametros.Tipo.INCIDENCIAS), prioridad);
    }
}
