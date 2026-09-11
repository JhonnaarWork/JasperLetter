package com.jasperletter.preview.dto;

public class TestDataAdapterRequest {
    private String letterId;
    private String dataAdapterXml;
    private String customXmlData;

    public TestDataAdapterRequest() {
    }

    public TestDataAdapterRequest(String letterId, String dataAdapterXml, String customXmlData) {
        this.letterId = letterId;
        this.dataAdapterXml = dataAdapterXml;
        this.customXmlData = customXmlData;
    }

    public String getLetterId() {
        return letterId;
    }

    public void setLetterId(String letterId) {
        this.letterId = letterId;
    }

    public String getDataAdapterXml() {
        return dataAdapterXml;
    }

    public void setDataAdapterXml(String dataAdapterXml) {
        this.dataAdapterXml = dataAdapterXml;
    }

    public String getCustomXmlData() {
        return customXmlData;
    }

    public void setCustomXmlData(String customXmlData) {
        this.customXmlData = customXmlData;
    }
}
