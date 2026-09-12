package com.jasperletter.preview.dto;

import java.util.List;

public record LetterDetailResponse(
        String id,
        String name,
        String format, // "JR6" | "JR7"
        String jrxml,
        String xmlData,
        String dataAdapter,
        List<String> imageFiles,
        boolean dataAdapterConnected
) {
    public LetterDetailResponse(String id, String name, String format, String jrxml,
                                 String xmlData, String dataAdapter, List<String> imageFiles) {
        this(id, name, format, jrxml, xmlData, dataAdapter, imageFiles, false);
    }
}
