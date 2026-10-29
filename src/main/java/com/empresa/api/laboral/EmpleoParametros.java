package com.empresa.api.laboral;

import com.empresa.api.common.ApiException;
import org.springframework.util.MultiValueMap;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

record EmpleoParametros(int page, int size, LocalDate desde, LocalDate hasta) {
    static EmpleoParametros leer(MultiValueMap<String,String> query, boolean periodo) {
        var permitidos = periodo ? Set.of("page","size","desde","hasta") : Set.of("page","size");
        query.forEach((key,values) -> { if (!permitidos.contains(key) || values.size()!=1) throw ApiException.invalido("query"); });
        int page=numero(query.getFirst("page"),0,0,100000,"page");
        int size=numero(query.getFirst("size"),20,1,100,"size");
        LocalDate desde=null,hasta=null;
        if (periodo) {
            try { desde=LocalDate.parse(query.getFirst("desde")); hasta=LocalDate.parse(query.getFirst("hasta")); }
            catch (RuntimeException ex) { throw ApiException.invalido("periodo"); }
            if(desde.getYear()<1 || hasta.getYear()>9999 || hasta.isBefore(desde) || ChronoUnit.DAYS.between(desde,hasta)>366)
                throw ApiException.invalido("periodo");
        }
        return new EmpleoParametros(page,size,desde,hasta);
    }
    private static int numero(String raw,int fallback,int min,int max,String campo) {
        if(raw==null) return fallback;
        try { int value=Integer.parseInt(raw); if(value<min || value>max) throw ApiException.invalido(campo); return value; }
        catch(NumberFormatException ex) { throw ApiException.invalido(campo); }
    }
}
