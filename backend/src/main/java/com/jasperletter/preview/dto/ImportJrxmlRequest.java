package com.jasperletter.preview.dto;

import jakarta.validation.constraints.NotBlank;

public record ImportJrxmlRequest(
        @NotBlank(message = "El ID de la carta es obligatorio.") String letterId,
        @NotBlank(message = "El contenido del JRXML a importar está vacío.") String jrxmlContent,
        boolean createDataAdapter,
        boolean createXmlData
) {
}
