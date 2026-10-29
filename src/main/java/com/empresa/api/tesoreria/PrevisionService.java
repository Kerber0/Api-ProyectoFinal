package com.empresa.api.tesoreria;

import com.empresa.api.common.ApiException;
import com.empresa.api.security.AutorizacionService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import static com.empresa.api.tesoreria.PrevisionDtos.*;

@Service
@Transactional(readOnly = true)
public class PrevisionService {
    private final AutorizacionService acceso;
    private final PrevisionRepository repository;
    private final Clock clock;
    public PrevisionService(AutorizacionService acceso, PrevisionRepository repository, Clock clock) {
        this.acceso=acceso; this.repository=repository; this.clock=clock;
    }
    public Prevision consultar(int usuario,int empresa,MultiValueMap<String,String> query) {
        acceso.exigirLectura(usuario,empresa,"EMPRESA");
        acceso.exigirLectura(usuario,empresa,"INFORMES");
        for(var e:query.entrySet()) {
            if (!java.util.Set.of("desde","hasta","tipo","page","size").contains(e.getKey())
                    || e.getValue().size()!=1 || e.getValue().getFirst()==null)
                throw ApiException.invalido(e.getKey());
        }
        LocalDate desde=fecha(query,"desde"), hasta=fecha(query,"hasta");
        if (desde==null || hasta==null || desde.isAfter(hasta)
                || ChronoUnit.MONTHS.between(YearMonth.from(desde),YearMonth.from(hasta))>=24)
            throw ApiException.invalido("rango");
        String tipo=query.getFirst("tipo");
        if(tipo==null)tipo="TODOS";
        if(!java.util.Set.of("CLIENTE","PROVEEDOR","TODOS").contains(tipo))throw ApiException.invalido("tipo");
        if(!tipo.equals("PROVEEDOR")) {
            acceso.exigirLectura(usuario,empresa,"FACTURAS_CLIENTE");
            acceso.exigirLectura(usuario,empresa,"COBROS");
        }
        if(!tipo.equals("CLIENTE")) {
            acceso.exigirLectura(usuario,empresa,"FACTURAS_PROVEEDOR");
            acceso.exigirLectura(usuario,empresa,"PAGOS");
        }
        int page=numero(query,"page",0,0,100000),size=numero(query,"size",25,1,100);
        return repository.consultar(empresa,desde,hasta,tipo,page,size,LocalDate.now(clock));
    }
    private static LocalDate fecha(MultiValueMap<String,String> q,String campo) {
        try {
            String valor=q.getFirst(campo);
            if(valor==null || !valor.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return null;
            LocalDate fecha=LocalDate.parse(valor);
            return fecha.getYear()>=1000?fecha:null;
        } catch(java.time.DateTimeException ex) { throw ApiException.invalido(campo); }
    }
    private static int numero(MultiValueMap<String,String> q,String campo,int defecto,int min,int max) {
        if(!q.containsKey(campo))return defecto;
        try {
            String texto=q.getFirst(campo);
            if(!texto.matches("[0-9]{1,8}"))throw new NumberFormatException();
            int valor=Integer.parseInt(texto);
            if(valor<min||valor>max)throw new NumberFormatException();
            return valor;
        }catch(NumberFormatException ex){throw ApiException.invalido(campo);}
    }
}
