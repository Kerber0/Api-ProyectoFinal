package com.empresa.api.documentos;

import com.empresa.api.common.ApiException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

public final class DocumentoValidacion {
    private DocumentoValidacion() {}
    public static final int MAX_BYTES=5*1024*1024;
    public static String mime(String value) {
        try {
            var media=org.springframework.http.MediaType.parseMediaType(value);
            return (media.getType()+"/"+media.getSubtype()).toLowerCase(Locale.ROOT);
        } catch(RuntimeException ex) { return ""; }
    }
    public static String nombre(String nombre,String mime,byte[] bytes) {
        if(nombre==null || nombre.isBlank() || nombre.length()>200 || !nombre.equals(nombre.strip())
                || nombre.contains("/") || nombre.contains("\\") || nombre.contains(":")
                || nombre.codePoints().anyMatch(c->Character.isISOControl(c) || Character.getType(c)==Character.FORMAT))
            throw ApiException.invalido("nombre");
        if(bytes.length>MAX_BYTES) throw ApiException.cuerpoGrande();
        if(bytes.length==0) throw ApiException.invalido("contenido");
        String lower=nombre.toLowerCase(Locale.ROOT);
        boolean valido=switch(mime==null?"":mime) {
            case "application/pdf" -> lower.endsWith(".pdf") && pdf(bytes);
            case "image/png" -> lower.endsWith(".png") && imagen(bytes,"png");
            case "image/jpeg" -> (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) && imagen(bytes,"JPEG");
            default -> false;
        };
        if(!valido) throw ApiException.invalido("contenido");
        return nombre;
    }
    private static boolean pdf(byte[] bytes) {
        if(bytes.length<20) return false;
        String head=new String(bytes,0,8,StandardCharsets.US_ASCII);
        String tail=new String(bytes,Math.max(0,bytes.length-1024),Math.min(bytes.length,1024),StandardCharsets.US_ASCII);
        return head.matches("%PDF-[12]\\.[0-9]") && tail.stripTrailing().endsWith("%%EOF");
    }
    private static boolean imagen(byte[] bytes,String formato) {
        try(var stream=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext()) return false;
            var reader=readers.next();
            try {
                reader.setInput(stream,true,true);
                if(!reader.getFormatName().equalsIgnoreCase(formato)) return false;
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1 || height<1 || (long)width*height>16_000_000) return false;
                return reader.read(0)!=null;
            } finally { reader.dispose(); }
        } catch(java.io.IOException | RuntimeException ex) { return false; }
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
