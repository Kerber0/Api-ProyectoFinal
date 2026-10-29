package com.empresa.api.security;

import com.empresa.api.common.ApiError;
import com.empresa.api.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

@RestControllerAdvice
public class SecurityErrors {
    private final ObjectMapper json;
    public SecurityErrors(ObjectMapper json) { this.json = json; }

    public void write(HttpServletRequest request, HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (status == 401) { response.setHeader("WWW-Authenticate", "Bearer"); }
        json.writeValue(response.getOutputStream(), ApiError.of(request, status,
                status == 401 ? "NO_AUTENTICADO" : "ACCESO_DENEGADO",
                status == 401 ? "Autenticacion requerida o no valida" : "Operacion no permitida", List.of()));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> autenticacion(AuthenticationException ex, HttpServletRequest request) {
        return response(request, 401, "NO_AUTENTICADO", "Credenciales o sesion no validas", List.of());
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> negocio(ApiException ex, HttpServletRequest request) {
        return response(request, ex.status(), ex.codigo(), ex.getMessage(), ex.campos());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> validacion(Exception ex, HttpServletRequest request) {
        List<String> campos = ex instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream().map(error -> error.getField()).distinct().sorted().toList()
                : ex instanceof MethodArgumentTypeMismatchException mismatch ? List.of(mismatch.getName()) : List.of();
        return response(request, 400, "SOLICITUD_INVALIDA", "Revisar los campos de la solicitud", campos);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> persistencia(DataAccessException ex, HttpServletRequest request) {
        return response(request, 500, "ERROR_INTERNO", "No se pudo completar la operacion", List.of());
    }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> contenidoNoAdmitido(Exception ex,HttpServletRequest request) {
        return response(request,415,"TIPO_NO_ADMITIDO","Tipo de contenido no admitido",List.of("Content-Type"));
    }

    private ResponseEntity<ApiError> response(HttpServletRequest request, int status, String codigo,
                                               String detalle, List<String> campos) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .body(ApiError.of(request, status, codigo, detalle, campos));
    }
}
