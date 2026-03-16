package com.capstone.aidetector;

public class ServerConfig {
    // 개발 환경 (에뮬레이터로 테스트 시) - 서버, 앱 같은 기기
    private static final String SERVER_URL = "http://10.0.2.2:5000/";

    // 실제 기기 (같은 Wi-Fi 연결) - 서버, 앱 다른 기기
    // private static final String SERVER_URL = "http://192.168.0.13:5000/";

    public static String getBaseUrl() {
        return SERVER_URL;
    }
}