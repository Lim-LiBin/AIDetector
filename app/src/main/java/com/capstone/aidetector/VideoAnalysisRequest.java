package com.capstone.aidetector;

public class VideoAnalysisRequest {
    private String url;

    public VideoAnalysisRequest(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}