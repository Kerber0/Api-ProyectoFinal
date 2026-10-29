package com.empresa.api.common;

import java.util.List;

/** Contrato de paginacion compartido; no depende de entidades ni de un modulo de negocio. */
public record Pagina<T>(List<T> items, int page, int size, long totalElements, long totalPages, String sort) {}
