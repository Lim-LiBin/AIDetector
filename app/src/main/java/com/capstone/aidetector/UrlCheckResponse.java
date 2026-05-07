package com.capstone.aidetector;

import com.google.gson.annotations.SerializedName;

public class UrlCheckResponse {
    private String domain;

    @SerializedName("creation_date")
    private String creationDate;

    @SerializedName("domain_age_days")
    private int domainAgeDays;

    @SerializedName("is_suspicious")
    private boolean isSuspicious;

    private String message;

    public String getDomain() { return domain; }
    public String getCreationDate() { return creationDate; }
    public int getDomainAgeDays() { return domainAgeDays; }
    public boolean isSuspicious() { return isSuspicious; }
    public String getMessage() { return message; }
}