package com.jasperletter.preview.dto;

public record LetterResourceInfo(
        String id,
        String name,
        String folderPath,
        String jrxmlFileName,
        String format, // "JR6" | "JR7"
        boolean hasDataAdapter,
        String dataAdapterFile,
        boolean hasXmlData,
        String xmlDataFile
) {
}
