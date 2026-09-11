package com.jasperletter.preview.dto;

public class DataAdapterOptionInfo {
    private String name;
    private String relativePath;
    private String letterId;
    private String location; // dataFile location inside adapter

    public DataAdapterOptionInfo() {
    }

    public DataAdapterOptionInfo(String name, String relativePath, String letterId, String location) {
        this.name = name;
        this.relativePath = relativePath;
        this.letterId = letterId;
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public String getLetterId() {
        return letterId;
    }

    public void setLetterId(String letterId) {
        this.letterId = letterId;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
