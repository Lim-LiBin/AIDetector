package com.capstone.aidetector;

import com.google.gson.annotations.SerializedName;

// URL 안전성 검사 결과를 서버로부터 받아오는 응답 데이터 클래스
public class UrlCheckResponse {
    private String domain; // 검사 대상 도메인

    @SerializedName("creation_date")
    private String creationDate; // 도메인 생성일

    @SerializedName("domain_age_days")
    private int domainAgeDays; // 도메인 생성 후 경과일

    @SerializedName("is_suspicious")
    private boolean isSuspicious; // 의심 도메인 여부

    private String message; // 서버에서 전달한 안내 메시지

    public String getDomain() { return domain; }
    public String getCreationDate() { return creationDate; }
    public int getDomainAgeDays() { return domainAgeDays; }
    public boolean isSuspicious() { return isSuspicious; }
    public String getMessage() { return message; }
}