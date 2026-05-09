package com.capstone.aidetector;

import java.io.Serializable;
import java.util.Date;

public class InquiryRecord implements Serializable {
    private String id; // 문서 ID
    private String title;
    private String body;
    private String status; // "접수 완료", "답변 준비 중", "답변 완료"
    private String reply; // 관리자 답변
    private Date timestamp;

    public InquiryRecord() {} // Firestore 빈 생성자

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
}