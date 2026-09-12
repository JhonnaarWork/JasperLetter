package com.jasperletter.preview.dto;

public record SaveLetterRequest(
        String jrxml,
        String xmlData,
        String dataAdapter,
        String format, // "JR6" | "JR7" | null (si es null, se preserva el formato original)
        Boolean saveJrxml,
        Boolean saveDataAdapter,
        Boolean saveXmlData
) {
    public SaveLetterRequest(String jrxml, String xmlData, String dataAdapter, String format) {
        this(jrxml, xmlData, dataAdapter, format, true, true, true);
    }
}
