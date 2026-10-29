package com.empresa.api.auditoria;

import com.empresa.api.common.ApiException;
import org.springframework.util.MultiValueMap;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import static com.empresa.api.auditoria.AuditoriaDtos.Modulo;

record AuditoriaParametros(int page, int size, Integer empresa, Integer autor, Modulo modulo,
                           Instant desde, Instant hasta) {
    static AuditoriaParametros leer(MultiValueMap<String,String> query) {
        query.forEach((key,values) -> {
            if (!Set.of("page","size","empresa","autor","modulo","desde","hasta").contains(key)
                    || values.size()!=1) throw ApiException.invalido("query");
        });
        Modulo modulo=null;
        if(query.containsKey("modulo")) {
            try { modulo=Modulo.valueOf(query.getFirst("modulo")); }
            catch(IllegalArgumentException ex) { throw ApiException.invalido("modulo"); }
        }
        Instant desde=instante(query.getFirst("desde"),"desde"), hasta=instante(query.getFirst("hasta"),"hasta");
        if(desde!=null && hasta!=null && !desde.isBefore(hasta)) throw ApiException.invalido("hasta");
        return new AuditoriaParametros(numero(query.getFirst("page"),0,0,100000,"page"),
                numero(query.getFirst("size"),20,1,100,"size"),
                numero(query.getFirst("empresa"),null,1,Integer.MAX_VALUE,"empresa"),
                numero(query.getFirst("autor"),null,1,Integer.MAX_VALUE,"autor"),modulo,desde,hasta);
    }
    private static Instant instante(String value,String campo) {
        if(value==null) return null;
        try {
            Instant instant=Instant.parse(value);
            if(instant.isBefore(Instant.parse("0001-01-01T00:00:00Z")) || !instant.isBefore(Instant.parse("+10000-01-01T00:00:00Z")))
                throw ApiException.invalido(campo);
            return instant;
        } catch(DateTimeParseException ex) { throw ApiException.invalido(campo); }
    }
    private static Integer numero(String value,Integer fallback,int min,int max,String campo) {
        if(value==null) return fallback;
        try { int n=Integer.parseInt(value); if(n<min || n>max) throw ApiException.invalido(campo); return n; }
        catch(NumberFormatException ex) { throw ApiException.invalido(campo); }
    }
}
