package com.capstone.aidetector;

public class ServerConfig {
    // 개발 환경 (에뮬레이터로 테스트 시) - 서버, 앱 같은 기기
    //private static final String SERVER_URL = "http://10.0.2.2:5000/";


    private static final String SERVER_URL = "https://subphrenic-intensionally-ardis.ngrok-free.dev/";
    // AWS 서버
    //private static final String SERVER_URL = "http://15.135.159.28:5000/";

    public static String getBaseUrl() {
        return SERVER_URL;
    }
}