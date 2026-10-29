package com.empresa.api.tesoreria;

import com.empresa.api.common.ApiException;
import org.springframework.util.MultiValueMap;
import java.time.LocalDate;
import java.util.Set;

public record TesoreriaParametros(int page,int size,Integer documento,Integer tercero,LocalDate desde,LocalDate hasta,boolean pendientes) {
    public static TesoreriaParametros parse(MultiValueMap<String,String> q,boolean vencimientos) {
        var permitidos=vencimientos?Set.of("page","size","documento","tercero","desde","hasta","pendientes"):Set.of("page","size");
        for(var e:q.entrySet()) if(!permitidos.contains(e.getKey())||e.getValue().size()!=1||e.getValue().getFirst()==null) throw ApiException.invalido(e.getKey());
        int page=numero(q,"page",0,0,100000),size=numero(q,"size",20,1,100);
        Integer doc=q.containsKey("documento")?numero(q,"documento",0,1,Integer.MAX_VALUE):null;
        Integer tercero=q.containsKey("tercero")?numero(q,"tercero",0,1,Integer.MAX_VALUE):null;
        LocalDate desde=fecha(q,"desde"),hasta=fecha(q,"hasta");
        if(desde!=null&&hasta!=null&&desde.isAfter(hasta)) throw ApiException.invalido("hasta");
        String pendientes=q.getFirst("pendientes");
        if(pendientes!=null&&!Set.of("true","false").contains(pendientes)) throw ApiException.invalido("pendientes");
        return new TesoreriaParametros(page,size,doc,tercero,desde,hasta,!"false".equals(pendientes));
    }
    private static int numero(MultiValueMap<String,String> q,String campo,int defecto,int min,int max) {
        if(!q.containsKey(campo)) return defecto;
        try { var v=q.getFirst(campo); if(!v.matches("[0-9]{1,10}")) throw new IllegalArgumentException(); int n=Integer.parseInt(v); if(n<min||n>max) throw new IllegalArgumentException(); return n; }
        catch(IllegalArgumentException e) { throw ApiException.invalido(campo); }
    }
    private static LocalDate fecha(MultiValueMap<String,String> q,String campo) {
        if(!q.containsKey(campo)) return null;
        try { String s=q.getFirst(campo); if(!s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException(); return LocalDate.parse(s); }
        catch(java.time.DateTimeException|IllegalArgumentException e) { throw ApiException.invalido(campo); }
    }
}
