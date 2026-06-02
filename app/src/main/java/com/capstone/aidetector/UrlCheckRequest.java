package com.capstone.aidetector;

// URL 안전성 검사 요청에 사용할 데이터를 담는 클래스
public class UrlCheckRequest {
    private String url; // 검사할 URL

    public UrlCheckRequest(String url) {
        this.url = url;
    }

    public String getUrl() { return url; }
}