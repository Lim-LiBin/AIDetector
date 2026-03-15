package com.capstone.aidetector.model;
import java.util.Date;

import java.io.Serializable;

public class HistoryRecord implements Serializable {
    private String documentId; // Firestore 문서 고유 ID (삭제 시 필요)
    private String uid;        // 사용자 식별자 (일단 "test_user_01"로)
    private String result;     // "Real" 또는 "Fake"
    private float probability; // 판별 확률 수치
    private String originalUrl; // 원본 이미지 Storage 주소
    private String heatmapUrl;  // 히트맵 이미지 Storage 주소
    private Date timestamp; // 서버 저장 시간 (정렬 기준)

    // 빈 생성자 (Firestore가 데이터를 자동으로 변환할 때 필요)
    public HistoryRecord() {}

    // 전체 생성자
    public HistoryRecord(String uid, String result, float probability, String originalUrl, String heatmapUrl, Date timestamp) {
        this.uid = uid;
        this.result = result;
        this.probability = probability;
        this.originalUrl = originalUrl;
        this.heatmapUrl = heatmapUrl;
        this.timestamp = timestamp;
    }

    // Getter & Setter
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String id) { this.documentId = id; }
    public String getUid() { return uid; }
    public void setUid(String id) { this.uid = id; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public float getProbability() { return probability; }
    public void setProbability(float probability) { this.probability = probability; }
    public String getOriginalUrl() { return originalUrl; }
    public void setOriginalUrl(String url) { this.originalUrl = url; }
    public String getHeatmapUrl() { return heatmapUrl; }
    public void setHeatmapUrl(String url) { this.heatmapUrl = url; }
    public java.util.Date getTimestamp() { return timestamp; }
    public void setTimestamp(java.util.Date timestamp) { this.timestamp = timestamp; }

}