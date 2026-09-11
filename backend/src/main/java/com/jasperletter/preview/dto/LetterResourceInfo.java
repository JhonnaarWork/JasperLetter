package com.jasperletter.preview.dto;

public class LetterResourceInfo {
    private String id;
    private String name;
    private String folderPath;
    private String jrxmlFileName;
    private String format; // "JR6" | "JR7"
    private boolean hasDataAdapter;
    private String dataAdapterFile;
    private boolean hasXmlData;
    private String xmlDataFile;

    public LetterResourceInfo() {
    }

    public LetterResourceInfo(String id, String name, String folderPath, String jrxmlFileName, 
                              String format, boolean hasDataAdapter, String dataAdapterFile, 
                              boolean hasXmlData, String xmlDataFile) {
        this.id = id;
        this.name = name;
        this.folderPath = folderPath;
        this.jrxmlFileName = jrxmlFileName;
        this.format = format;
        this.hasDataAdapter = hasDataAdapter;
        this.dataAdapterFile = dataAdapterFile;
        this.hasXmlData = hasXmlData;
        this.xmlDataFile = xmlDataFile;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public String getJrxmlFileName() {
        return jrxmlFileName;
    }

    public void setJrxmlFileName(String jrxmlFileName) {
        this.jrxmlFileName = jrxmlFileName;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean isHasDataAdapter() {
        return hasDataAdapter;
    }

    public void setHasDataAdapter(boolean hasDataAdapter) {
        this.hasDataAdapter = hasDataAdapter;
    }

    public String getDataAdapterFile() {
        return dataAdapterFile;
    }

    public void setDataAdapterFile(String dataAdapterFile) {
        this.dataAdapterFile = dataAdapterFile;
    }

    public boolean isHasXmlData() {
        return hasXmlData;
    }

    public void setHasXmlData(boolean hasXmlData) {
        this.hasXmlData = hasXmlData;
    }

    public String getXmlDataFile() {
        return xmlDataFile;
    }

    public void setXmlDataFile(String xmlDataFile) {
        this.xmlDataFile = xmlDataFile;
    }
}
