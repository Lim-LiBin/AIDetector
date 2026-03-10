package com.capstone.aidetector;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;

public class FirebaseManager {

    private static final String TAG = "FirebaseManager";
    // [v] 제공해주신 Realtime Database 주소 적용
    private static final String DB_URL = "https://aidetector-50fe9-default-rtdb.firebaseio.com/";

    /**
     * [v] AnalysisResult 객체를 받아 Firebase(Storage & Realtime DB)에 업로드합니다.
     */
    public void uploadAnalysisResult(AnalysisResult result) {
        if (result == null || result.heatmapBitmap == null) {
            Log.e(TAG, "업로드 실패: 데이터가 비어있습니다.");
            return;
        }

        // 1. 히트맵 비트맵 이미지를 바이트 배열로 변환 (압축)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        result.heatmapBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] imageData = baos.toByteArray();

        // 2. Firebase Storage에 이미지 파일 업로드 경로 설정
        String fileName = "heatmap_" + System.currentTimeMillis() + ".png";
        StorageReference storageRef = FirebaseStorage.getInstance().getReference().child("heatmaps/" + fileName);

        // 3. Storage 업로드 시작
        storageRef.putBytes(imageData).addOnSuccessListener(taskSnapshot -> {
            // 이미지 업로드 성공 시, 해당 이미지의 접근 URL을 가져옴
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String imageUrl = uri.toString();

                // 4. Realtime Database에 저장할 데이터 모델 생성 (확률값 + 이미지URL)
                // [v] AnalysisResult 객체 생성 및 데이터 담기 로직 반영
                DatabaseModel dbEntry = new DatabaseModel(result.probability, imageUrl);

                // 5. 지정된 DB_URL을 사용하여 데이터 저장
                // [v] FirebaseManager의 uploadAnalysisResult 메서드를 호출하여 최종 객체 전달
                FirebaseDatabase.getInstance(DB_URL)
                        .getReference("analysis_results")
                        .push()
                        .setValue(dbEntry)
                        .addOnSuccessListener(aVoid -> {
                            // [v] 로그: [추론 및 시각화 완료] 확률: (값), 히트맵 이미지 생성 및 DB&결과 화면 전달 성공
                            Log.i(TAG, "[추론 및 시각화 완료] 확률: " + result.probability + ", 히트맵 이미지 생성 및 DB&결과 화면 전달 성공");
                        })
                        .addOnFailureListener(e -> Log.e(TAG, "DB 저장 실패: " + e.getMessage()));
            });
        }).addOnFailureListener(e -> Log.e(TAG, "Storage 업로드 실패: " + e.getMessage()));
    }

    /**
     * Firebase Realtime Database 저장용 데이터 모델 클래스
     */
    public static class DatabaseModel {
        public float probability; // 분석 확률값
        public String heatmapUrl; // 저장된 히트맵 이미지 주소
        public long timestamp;    // 저장 시간

        public DatabaseModel() {
            // Firebase 실시간 DB를 위한 기본 생성자
        }

        public DatabaseModel(float probability, String heatmapUrl) {
            this.probability = probability;
            this.heatmapUrl = heatmapUrl;
            this.timestamp = System.currentTimeMillis();
        }
    }
}