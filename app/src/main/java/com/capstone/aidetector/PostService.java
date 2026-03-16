package com.capstone.aidetector;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface PostService {
    @POST("analyze_video")
    Call<VideoAnalysisResponse> analyzeVideo(@Body VideoAnalysisRequest request);
}