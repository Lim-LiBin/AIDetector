package com.capstone.aidetector;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

// Retrofit을 이용해 서버 API 요청을 정의하는 인터페이스
public interface PostService {
    // 영상 URL 분석 요청
    @POST("analyze_video")
    Call<VideoAnalysisResponse> analyzeVideo(@Body VideoAnalysisRequest request);

    // URL 안정성 검사 요청
    @POST("check_url")
    Call<UrlCheckResponse> checkUrl(@Body UrlCheckRequest request);
}