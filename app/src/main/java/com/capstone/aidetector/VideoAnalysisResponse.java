package com.capstone.aidetector;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class VideoAnalysisResponse {
    @SerializedName("result")
    private String result;

    @SerializedName("probability")
    private float probability;

    @SerializedName("heatmap")
    private List<List<Float>> heatmap;

    @SerializedName("frame")
    private String frameBase64;

    @SerializedName("face_coords")
    private Map<String, Integer> faceCoords;

    public String getResult() { return result; }
    public float getProbability() { return probability; }
    public List<List<Float>> getHeatmap() { return heatmap; }
    public String getFrameBase64() { return frameBase64; }
    public Map<String, Integer> getFaceCoords() { return faceCoords; }
}