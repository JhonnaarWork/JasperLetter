package com.jasperletter.preview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Forma tipada y consistente para todas las respuestas de error de la API, en reemplazo de
 * los Map&lt;String, Object&gt; ad-hoc construidos a mano en cada controller (hallazgo A-3).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String status, String message, String cause) {

    public ErrorResponse(String status, String message) {
        this(status, message, null);
    }
}
