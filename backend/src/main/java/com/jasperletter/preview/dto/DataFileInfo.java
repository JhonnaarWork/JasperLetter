package com.jasperletter.preview.dto;

public class DataFileInfo {
    private String fileName;
    private String relativePath;
    private long sizeBytes;

    public DataFileInfo() {
    }

    public DataFileInfo(String fileName, String relativePath, long sizeBytes) {
        this.fileName = fileName;
        this.relativePath = relativePath;
        this.sizeBytes = sizeBytes;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }
}
