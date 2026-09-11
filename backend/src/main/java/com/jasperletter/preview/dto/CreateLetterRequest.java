package com.jasperletter.preview.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateLetterRequest {
    @NotBlank(message = "El ID de la carta es obligatorio.")
    private String letterId;
    private String name;
    private String format; // "JR6" | "JR7"
    private String description;
    private boolean createDataAdapter;
    private boolean createXmlData;

    public CreateLetterRequest() {
    }

    public CreateLetterRequest(String letterId, String name, String format, String description, boolean createDataAdapter, boolean createXmlData) {
        this.letterId = letterId;
        this.name = name;
        this.format = format;
        this.description = description;
        this.createDataAdapter = createDataAdapter;
        this.createXmlData = createXmlData;
    }

    public String getLetterId() {
        return letterId;
    }

    public void setLetterId(String letterId) {
        this.letterId = letterId;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isCreateDataAdapter() {
        return createDataAdapter;
    }

    public void setCreateDataAdapter(boolean createDataAdapter) {
        this.createDataAdapter = createDataAdapter;
    }

    public boolean isCreateXmlData() {
        return createXmlData;
    }

    public void setCreateXmlData(boolean createXmlData) {
        this.createXmlData = createXmlData;
    }
}
