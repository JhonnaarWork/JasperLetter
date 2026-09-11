package com.jasperletter.preview.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Collections;
import java.util.Map;

public class PreviewRequest {

    @NotBlank(message = "El contenido JRXML no puede estar vacío")
    private String jrxml;
    private Map<String, Object> parameters;

    private String letterId;
    private String xmlData;
    private String format;

    public PreviewRequest() {
    }

    public PreviewRequest(String jrxml, Map<String, Object> parameters) {
        this.jrxml = jrxml;
        this.parameters = parameters;
    }

    public String getJrxml() {
        return jrxml;
    }

    public void setJrxml(String jrxml) {
        this.jrxml = jrxml;
    }

    public Map<String, Object> getParameters() {
        return parameters != null ? parameters : Collections.emptyMap();
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public String getLetterId() {
        return letterId;
    }

    public void setLetterId(String letterId) {
        this.letterId = letterId;
    }

    public String getXmlData() {
        return xmlData;
    }

    public void setXmlData(String xmlData) {
        this.xmlData = xmlData;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
