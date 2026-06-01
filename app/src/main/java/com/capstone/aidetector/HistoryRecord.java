package com.capstone.aidetector;

import java.util.Date;
import java.io.Serializable;

// 분석 이력 정보를 저장하고 화면 간 전달하기 위한 데이터 클래스
public class HistoryRecord implements Serializable {
    private String documentId; // Firestore 문서 고유 ID
    private String uid;        // 사용자 식별자
    private String result;     // 분석결과
    private float probability; // 판별 확률
    private String originalUrl; // 원본 이미지 Storage 주소
    private String heatmapUrl;  // 히트맵 이미지 Storage 주소
    private Date timestamp; // 분석 결과 저장 시간

    private String snsUrl; // 분석에 사용된 SNS URL

    public String getSnsUrl() { return snsUrl;}
    public void setSnsUrl(String snsUrl) {this.snsUrl = snsUrl; }

    // Firestore 자동 매핑을 위한 빈 생성자
    public HistoryRecord() {}

    // 분석 이력 객체 생성을 위한 전체 생성자
    public HistoryRecord(String uid, String result, float probability, String originalUrl, String heatmapUrl, Date timestamp) {
        this.uid = uid;
        this.result = result;
        this.probability = probability;
        this.originalUrl = originalUrl;
        this.heatmapUrl = heatmapUrl;
        this.timestamp = timestamp;
    }

    // 각 필드 접근 및 수정 메서드
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