package com.jasperletter.preview.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateLetterRequest(
        @NotBlank(message = "El ID de la carta es obligatorio.") String letterId,
        String name,
        String format, // "JR6" | "JR7"
        String description,
        boolean createDataAdapter,
        boolean createXmlData
) {
}
