package com.empresa.api.observaciones;

import com.empresa.api.common.ApiException;
import org.springframework.util.MultiValueMap;
import java.util.Set;

record ObservacionParametros(int page,int size,ObservacionDtos.Estado estado) {
    static ObservacionParametros leer(MultiValueMap<String,String> query) {
        query.forEach((key,values)-> {
            if(!Set.of("page","size","estado").contains(key) || values.size()!=1) throw ApiException.invalido("query");
        });
        ObservacionDtos.Estado estado=null;
        if(query.containsKey("estado")) {
            try { estado=ObservacionDtos.Estado.valueOf(query.getFirst("estado")); }
            catch(IllegalArgumentException | NullPointerException ex) { throw ApiException.invalido("estado"); }
        }
        return new ObservacionParametros(numero(query.getFirst("page"),0,0,100000,"page"),
                numero(query.getFirst("size"),20,1,100,"size"),estado);
    }
    private static int numero(String raw,int fallback,int min,int max,String campo) {
        if(raw==null) return fallback;
        try {
            int value=Integer.parseInt(raw);
            if(value<min || value>max) throw ApiException.invalido(campo);
            return value;
        } catch(NumberFormatException ex) { throw ApiException.invalido(campo); }
    }
}
