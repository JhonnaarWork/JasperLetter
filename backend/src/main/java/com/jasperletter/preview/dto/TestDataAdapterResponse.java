package com.jasperletter.preview.dto;

import java.util.List;

public record TestDataAdapterResponse(
        boolean success,
        String status, // "SUCCESS" | "FILE_NOT_FOUND" | "INVALID_ADAPTER_XML" | "INVALID_DATA_XML" | "MISSING_LOCATION" | "ERROR"
        String message,
        String adapterName,
        String location,
        String resolvedPath,
        boolean fileExists,
        long fileSizeBytes,
        boolean xmlValid,
        String rootElement,
        String selectExpression,
        int xpathMatches,
        String locale,
        String timeZone,
        List<String> testedPaths,
        String xmlContent
) {
    public static TestDataAdapterResponse success(
            String message,
            String adapterName,
            String location,
            String resolvedPath,
            long fileSizeBytes,
            String rootElement,
            String selectExpression,
            int xpathMatches,
            String locale,
            String timeZone
    ) {
        return success(message, adapterName, location, resolvedPath, fileSizeBytes, rootElement, selectExpression, xpathMatches, locale, timeZone, null);
    }

    public static TestDataAdapterResponse success(
            String message,
            String adapterName,
            String location,
            String resolvedPath,
            long fileSizeBytes,
            String rootElement,
            String selectExpression,
            int xpathMatches,
            String locale,
            String timeZone,
            String xmlContent
    ) {
        return new TestDataAdapterResponse(
                true, "SUCCESS", message, adapterName, location, resolvedPath, true, fileSizeBytes,
                true, rootElement, selectExpression, xpathMatches, locale, timeZone, null, xmlContent
        );
    }

    public static TestDataAdapterResponse error(String status, String message, String location, List<String> testedPaths) {
        return new TestDataAdapterResponse(
                false, status, message, null, location, null, false, 0,
                false, null, null, 0, null, null, testedPaths, null
        );
    }
}
