package com.empresa.api.consulta;

import com.empresa.api.common.ApiException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

record CalendarioParametros(ConsultaParametros pagina, String filtro) {
    static CalendarioParametros parse(MultiValueMap<String, String> query, boolean tarea, String padre) {
        var resto = new LinkedMultiValueMap<String, String>(query);
        if (padre != null && resto.containsKey(padre)) { throw ApiException.invalido(padre); }
        String campo = tarea ? "prioridad" : "tipo";
        String filtro = "";
        if (resto.containsKey(campo)) {
            var values = resto.remove(campo);
            if (values.size() != 1 || values.getFirst() == null || values.getFirst().length() > (tarea ? 300 : 30)) {
                throw ApiException.invalido(campo);
            }
            filtro = values.getFirst().trim();
        }
        return new CalendarioParametros(ConsultaParametros.parse(resto,
                tarea ? ConsultaParametros.Tipo.TAREAS : ConsultaParametros.Tipo.EVENTOS), filtro);
    }
}
