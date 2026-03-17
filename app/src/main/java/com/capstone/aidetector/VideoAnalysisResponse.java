package com.capstone.aidetector;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class VideoAnalysisResponse {
    @SerializedName("result")
    private String result;  // "Real" or "Fake"

    @SerializedName("probability")
    private float probability;  // 0.0 ~ 1.0

    @SerializedName("heatmap")
    private List<List<Float>> heatmap;  // 7x7 행렬

    @SerializedName("frame")
    private String frameBase64;  // Base64 인코딩된 이미지

    // Getters
    public String getResult() {
        return result;
    }

    public float getProbability() {
        return probability;
    }

    public List<List<Float>> getHeatmap() {
        return heatmap;
    }

    public String getFrameBase64() {
        return frameBase64;
    }
}