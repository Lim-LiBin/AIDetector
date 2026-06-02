package com.capstone.aidetector;

import com.google.gson.annotations.SerializedName;
import java.util.List;

// 영상 분석 결과를 서버로부터 받아오는 응답 데이터 클래스
public class VideoAnalysisResponse {
    @SerializedName("result")
    private String result;  // 분석 결과

    @SerializedName("probability")
    private float probability;  // 조작 확률값

    @SerializedName("heatmap")
    private List<List<Float>> heatmap;  // 히트맵 2차원 행렬

    @SerializedName("frame")
    private String frameBase64;  // Base64로 인코딩된 분석 프레임 이미지

    // 분석 결과 반환
    public String getResult() {
        return result;
    }

    // 조작 확률값 반환
    public float getProbability() {
        return probability;
    }

    // 히트맵 데이터 반환
    public List<List<Float>> getHeatmap() {
        return heatmap;
    }

    // 분석 프레임 이미지 데이터 반환
    public String getFrameBase64() {
        return frameBase64;
    }
}