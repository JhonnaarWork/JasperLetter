package com.jasperletter.preview.dto;

import java.util.List;

public class LetterDetailResponse {
    private String id;
    private String name;
    private String format; // "JR6" | "JR7"
    private String jrxml;
    private String xmlData;
    private String dataAdapter;
    private List<String> imageFiles;
    private boolean dataAdapterConnected;

    public LetterDetailResponse() {
    }

    public LetterDetailResponse(String id, String name, String format, String jrxml, 
                                String xmlData, String dataAdapter, List<String> imageFiles) {
        this(id, name, format, jrxml, xmlData, dataAdapter, imageFiles, false);
    }

    public LetterDetailResponse(String id, String name, String format, String jrxml, 
                                String xmlData, String dataAdapter, List<String> imageFiles,
                                boolean dataAdapterConnected) {
        this.id = id;
        this.name = name;
        this.format = format;
        this.jrxml = jrxml;
        this.xmlData = xmlData;
        this.dataAdapter = dataAdapter;
        this.imageFiles = imageFiles;
        this.dataAdapterConnected = dataAdapterConnected;
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

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getJrxml() {
        return jrxml;
    }

    public void setJrxml(String jrxml) {
        this.jrxml = jrxml;
    }

    public String getXmlData() {
        return xmlData;
    }

    public void setXmlData(String xmlData) {
        this.xmlData = xmlData;
    }

    public String getDataAdapter() {
        return dataAdapter;
    }

    public void setDataAdapter(String dataAdapter) {
        this.dataAdapter = dataAdapter;
    }

    public List<String> getImageFiles() {
        return imageFiles;
    }

    public void setImageFiles(List<String> imageFiles) {
        this.imageFiles = imageFiles;
    }

    public boolean isDataAdapterConnected() {
        return dataAdapterConnected;
    }

    public void setDataAdapterConnected(boolean dataAdapterConnected) {
        this.dataAdapterConnected = dataAdapterConnected;
    }
}
