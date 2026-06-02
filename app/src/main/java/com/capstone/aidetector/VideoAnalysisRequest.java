package com.capstone.aidetector;

// 영상 분석 요청에 사용할 URL 데이터를 담는 클래스
public class VideoAnalysisRequest {
    private String url; // 분석할 영상 URL

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