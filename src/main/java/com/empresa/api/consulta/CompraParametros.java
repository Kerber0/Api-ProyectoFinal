package com.empresa.api.consulta;

import com.empresa.api.common.ApiException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import static com.empresa.api.consulta.ConsultaParametros.Tipo.PEDIDOS;

record CompraParametros(ConsultaParametros pagina, Integer proveedor) {
    static CompraParametros parse(MultiValueMap<String, String> query, boolean anidado) {
        var resto = new LinkedMultiValueMap<String, String>(query);
        Integer proveedor = null;
        if (!anidado && resto.containsKey("proveedor")) {
            var values = resto.remove("proveedor");
            if (values.size() != 1) { throw ApiException.invalido("proveedor"); }
            proveedor = ConsultaParametros.entero(values.getFirst(), 0, 1, Integer.MAX_VALUE, "proveedor");
        }
        return new CompraParametros(ConsultaParametros.parse(resto, PEDIDOS), proveedor);
    }
}
