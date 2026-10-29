package com.empresa.api.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

public record ApiError(int status, String codigo, String detalle, List<String> campos, String requestId) {
    public static ApiError of(HttpServletRequest request, int status, String codigo, String detalle, List<String> campos) {
        return new ApiError(status, codigo, detalle, campos, (String) request.getAttribute(RequestIdFilter.ATTRIBUTE));
    }
}
