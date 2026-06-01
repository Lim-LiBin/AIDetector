package com.capstone.aidetector;

// 서버 기본 주소를 관리하는 설정 클래스
public class ServerConfig {
    // 분석 서버 기본 URL
    private static final String SERVER_URL = "https://subphrenic-intensionally-ardis.ngrok-free.dev/";

    public static String getBaseUrl() {
        return SERVER_URL;
    }
}