package com.jasperletter.preview.dto;

public class SaveLetterRequest {
    private String jrxml;
    private String xmlData;
    private String dataAdapter;
    private String format; // "JR6" | "JR7" | null (if null, preserves original format)
    private Boolean saveJrxml;
    private Boolean saveDataAdapter;
    private Boolean saveXmlData;

    public SaveLetterRequest() {
    }

    public SaveLetterRequest(String jrxml, String xmlData, String dataAdapter, String format) {
        this(jrxml, xmlData, dataAdapter, format, true, true, true);
    }

    public SaveLetterRequest(String jrxml, String xmlData, String dataAdapter, String format,
                             Boolean saveJrxml, Boolean saveDataAdapter, Boolean saveXmlData) {
        this.jrxml = jrxml;
        this.xmlData = xmlData;
        this.dataAdapter = dataAdapter;
        this.format = format;
        this.saveJrxml = saveJrxml;
        this.saveDataAdapter = saveDataAdapter;
        this.saveXmlData = saveXmlData;
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

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Boolean getSaveJrxml() {
        return saveJrxml;
    }

    public void setSaveJrxml(Boolean saveJrxml) {
        this.saveJrxml = saveJrxml;
    }

    public Boolean getSaveDataAdapter() {
        return saveDataAdapter;
    }

    public void setSaveDataAdapter(Boolean saveDataAdapter) {
        this.saveDataAdapter = saveDataAdapter;
    }

    public Boolean getSaveXmlData() {
        return saveXmlData;
    }

    public void setSaveXmlData(Boolean saveXmlData) {
        this.saveXmlData = saveXmlData;
    }
}
