package com.capstone.aidetector;

import java.io.Serializable;
import java.util.Date;

// 문의 내역 정보를 저장하고 화면 간 전달하기 위한 데이터 클래스
public class InquiryRecord implements Serializable {
    private String id; // Firestore 문서 ID
    private String title; // 문서 제목
    private String body; // 문서 내용
    private String status; // 문서 처리 상태
    private String reply; // 관리자 답변
    private Date timestamp; // 문의 작성 시간

    // Firestore 자동 매핑을 위한 빈 생성자
    public InquiryRecord() {}

    // 각 필드 접근 및 수정 메서드
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