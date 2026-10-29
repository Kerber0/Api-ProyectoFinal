package com.empresa.api;

import com.empresa.api.common.ApiException;
import com.empresa.api.documentos.*;
import com.empresa.api.operacion.RequestLimitsFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;

class ArchivoStoreTests {
    @TempDir Path raiz;
    @Test void escrituraExclusivaLecturaYBorradoAcotado() {
        var store=new ArchivoLocalStore(raiz.toString()); var clave=UUID.randomUUID().toString();
        byte[] contenido={1,2,3}; store.guardar(clave,contenido);
        assertThatThrownBy(()->store.guardar(clave,new byte[]{4})).isInstanceOf(ApiException.class);
        assertThat(store.leer(clave)).containsExactly(contenido);
        for(String mal:new String[]{"../fuera","C:\\secreto","http://localhost",""}) {
            assertThatThrownBy(()->store.leer(mal)).isInstanceOf(ApiException.class);
            assertThatThrownBy(()->store.borrar(mal)).isInstanceOf(ApiException.class);
        }
        store.borrar(clave); store.borrar(clave);
        assertThatThrownBy(()->store.leer(clave)).isInstanceOf(ApiException.class);
    }
    @Test void raizNoDirectorioNoSeModifica() throws Exception {
        Path archivo=raiz.resolve("archivo"); Files.writeString(archivo,"conservar");
        var store=new ArchivoLocalStore(archivo.toString());
        assertThatThrownBy(()->store.guardar(UUID.randomUUID().toString(),new byte[]{1})).isInstanceOf(ApiException.class);
        assertThat(Files.readString(archivo)).isEqualTo("conservar");
    }
    @Test void formatosImagenSeDecodificanYNoSeConfiaEnMime() throws Exception {
        for(String formato:new String[]{"png","jpeg"}) {
            var output=new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(3,2,BufferedImage.TYPE_INT_RGB),formato,output);
            byte[] bytes=output.toByteArray();
            assertThat(DocumentoValidacion.nombre("imagen."+formato,"image/"+formato,bytes)).isEqualTo("imagen."+formato);
            assertThatThrownBy(()->DocumentoValidacion.nombre("falso.pdf","application/pdf",bytes)).isInstanceOf(ApiException.class);
            assertThatThrownBy(()->DocumentoValidacion.nombre("imagen."+formato,"image/"+formato,java.util.Arrays.copyOf(bytes,20))).isInstanceOf(ApiException.class);
        }
    }
    @Test void imagenConDimensionesDesmesuradasSeRechazaAntesDeDecodificar() throws Exception {
        var output=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB),"png",output);
        byte[] bytes=output.toByteArray();
        java.nio.ByteBuffer.wrap(bytes).putInt(16,100000).putInt(20,100000);
        assertThatThrownBy(()->DocumentoValidacion.nombre("grande.png","image/png",bytes)).isInstanceOf(ApiException.class);
    }
    @Test void limiteBinarioExactoNoAmpliaJsonNiRutasParecidas() throws Exception {
        var filter=new RequestLimitsFilter(Clock.systemUTC(),new ObjectMapper(),50,50,10,16384);
        for(String ruta:new String[]{"/api/v1/empresas/1/documentos/obras/1001","/api/v1/empresas/1/documentos/presupuestos/1","/api/v1/empresas/1/documentos/ausencias/1"}) {
            byte[] bytes=new byte[DocumentoValidacion.MAX_BYTES];
            var request=request(ruta,bytes,false); var response=new MockHttpServletResponse();
            filter.doFilter(request,response,(req,res)->assertThat(req.getInputStream().readAllBytes()).hasSize(bytes.length));
            assertThat(response.getStatus()).isEqualTo(200);
        }
        for(String ruta:new String[]{"/api/v1/auth/login","/api/v1/empresas/1/documentos/obras/1/extra","/api/v1/empresas/1/documentos/otro/1"}) {
            var response=new MockHttpServletResponse();
            filter.doFilter(request(ruta,new byte[16385],false),response,(req,res)->fail("Debe rechazar"));
            assertThat(response.getStatus()).isEqualTo(413);
        }
    }
    @Test void limiteBinarioConYSinContentLength() throws Exception {
        var filter=new RequestLimitsFilter(Clock.systemUTC(),new ObjectMapper(),50,50,10,16384);
        for(boolean chunked:new boolean[]{true,false}) {
            var response=new MockHttpServletResponse();
            filter.doFilter(request("/api/v1/empresas/1/documentos/obras/1001",new byte[DocumentoValidacion.MAX_BYTES+1],chunked),response,(req,res)->fail("Debe rechazar"));
            assertThat(response.getStatus()).isEqualTo(413);
            assertThat(response.getContentAsString()).contains("CUERPO_DEMASIADO_GRANDE");
        }
    }
    private MockHttpServletRequest request(String ruta,byte[] body,boolean chunked) {
        var request=new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return chunked?-1:super.getContentLengthLong(); }
        };
        request.setMethod("POST"); request.setRequestURI(ruta); request.setContent(body); request.setContentType("application/pdf");
        return request;
    }
}
