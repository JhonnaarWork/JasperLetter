package com.jasperletter.preview.dto;

import java.util.List;

public class TestDataAdapterResponse {
    private boolean success;
    private String status; // "SUCCESS" | "FILE_NOT_FOUND" | "INVALID_ADAPTER_XML" | "INVALID_DATA_XML" | "ERROR"
    private String message;
    private String adapterName;
    private String location;
    private String resolvedPath;
    private boolean fileExists;
    private long fileSizeBytes;
    private boolean xmlValid;
    private String rootElement;
    private String selectExpression;
    private int xpathMatches;
    private String locale;
    private String timeZone;
    private List<String> testedPaths;
    private String xmlContent;

    public TestDataAdapterResponse() {
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
        TestDataAdapterResponse res = new TestDataAdapterResponse();
        res.setSuccess(true);
        res.setStatus("SUCCESS");
        res.setMessage(message);
        res.setAdapterName(adapterName);
        res.setLocation(location);
        res.setResolvedPath(resolvedPath);
        res.setFileExists(true);
        res.setFileSizeBytes(fileSizeBytes);
        res.setXmlValid(true);
        res.setRootElement(rootElement);
        res.setSelectExpression(selectExpression);
        res.setXpathMatches(xpathMatches);
        res.setLocale(locale);
        res.setTimeZone(timeZone);
        res.setXmlContent(xmlContent);
        return res;
    }

    public static TestDataAdapterResponse error(String status, String message, String location, List<String> testedPaths) {
        TestDataAdapterResponse res = new TestDataAdapterResponse();
        res.setSuccess(false);
        res.setStatus(status);
        res.setMessage(message);
        res.setLocation(location);
        res.setFileExists(false);
        res.setXmlValid(false);
        res.setTestedPaths(testedPaths);
        return res;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAdapterName() {
        return adapterName;
    }

    public void setAdapterName(String adapterName) {
        this.adapterName = adapterName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getResolvedPath() {
        return resolvedPath;
    }

    public void setResolvedPath(String resolvedPath) {
        this.resolvedPath = resolvedPath;
    }

    public boolean isFileExists() {
        return fileExists;
    }

    public void setFileExists(boolean fileExists) {
        this.fileExists = fileExists;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public boolean isXmlValid() {
        return xmlValid;
    }

    public void setXmlValid(boolean xmlValid) {
        this.xmlValid = xmlValid;
    }

    public String getRootElement() {
        return rootElement;
    }

    public void setRootElement(String rootElement) {
        this.rootElement = rootElement;
    }

    public String getSelectExpression() {
        return selectExpression;
    }

    public void setSelectExpression(String selectExpression) {
        this.selectExpression = selectExpression;
    }

    public int getXpathMatches() {
        return xpathMatches;
    }

    public void setXpathMatches(int xpathMatches) {
        this.xpathMatches = xpathMatches;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public List<String> getTestedPaths() {
        return testedPaths;
    }

    public void setTestedPaths(List<String> testedPaths) {
        this.testedPaths = testedPaths;
    }

    public String getXmlContent() {
        return xmlContent;
    }

    public void setXmlContent(String xmlContent) {
        this.xmlContent = xmlContent;
    }
}
