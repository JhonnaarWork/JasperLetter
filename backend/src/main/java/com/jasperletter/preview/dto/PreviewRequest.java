package com.jasperletter.preview.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Collections;
import java.util.Map;

public record PreviewRequest(
        @NotBlank(message = "El contenido JRXML no puede estar vacío") String jrxml,
        Map<String, Object> parameters,
        String letterId,
        String xmlData,
        String format
) {
    public PreviewRequest(String jrxml, Map<String, Object> parameters) {
        this(jrxml, parameters, null, null, null);
    }

    @Override
    public Map<String, Object> parameters() {
        return parameters != null ? parameters : Collections.emptyMap();
    }
}
