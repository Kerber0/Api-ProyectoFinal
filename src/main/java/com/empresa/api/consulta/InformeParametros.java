package com.empresa.api.consulta;

import com.empresa.api.common.ApiException;
import org.springframework.util.MultiValueMap;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Set;

record InformeParametros(LocalDate desde, LocalDate hasta) {
    static InformeParametros parse(MultiValueMap<String, String> query) {
        query.forEach((key, values) -> {
            if (!Set.of("desde", "hasta").contains(key) || values.size() != 1) { throw ApiException.invalido("query"); }
        });
        if (!query.containsKey("desde")) { throw ApiException.invalido("desde"); }
        if (!query.containsKey("hasta")) { throw ApiException.invalido("hasta"); }
        LocalDate desde = ConsultaParametros.fecha(query.getFirst("desde"), "desde");
        LocalDate hasta = ConsultaParametros.fecha(query.getFirst("hasta"), "hasta");
        if (desde.isAfter(hasta) || ChronoUnit.MONTHS.between(YearMonth.from(desde), YearMonth.from(hasta)) >= 24) {
            throw ApiException.invalido("hasta");
        }
        return new InformeParametros(desde, hasta);
    }
}
