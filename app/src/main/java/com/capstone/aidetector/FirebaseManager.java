package com.capstone.aidetector;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class FirebaseManager {
    private static final String TAG = "FirebaseManager";
    private final FirebaseStorage storage = FirebaseStorage.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void uploadAnalysisResult(AnalysisResult result) {
        if (result == null || result.heatmapBitmap == null) {
            Log.e(TAG, "업로드 실패: 전달받은 결과값이 없습니다.");
            return;
        }

        // 1. 포맷 변환: Bitmap -> ByteArray (JPEG)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        result.heatmapBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
        byte[] data = baos.toByteArray();

        // 2. Storage 저장: heatmaps/ 폴더 내 고유 파일명 생성
        String fileName = "result_" + System.currentTimeMillis() + ".jpg";
        StorageReference storageRef = storage.getReference().child("heatmaps/" + fileName);

        // 3. 업로드 실행 및 예외 처리
        UploadTask uploadTask = storageRef.putBytes(data);
        uploadTask.addOnSuccessListener(taskSnapshot -> {
            // 4. URL 추출 성공 시 Firestore 기록
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                saveToFirestore(result.probability, uri.toString());
            }).addOnFailureListener(e -> {
                // [요구사항] 예외 처리: 에러 메시지 출력
                Log.e(TAG, "URL 추출 실패: " + e.getMessage());
            });
        }).addOnFailureListener(e -> {
            // [요구사항] 예외 처리: 업로드 실패 시 에러 메시지 출력
            Log.e(TAG, "업로드 실패: " + e.getMessage());
        });
    }

    private void saveToFirestore(float probability, String heatmapUrl) {
        // 데이터 매핑
        Map<String, Object> data = new HashMap<>();
        data.put("probability", probability);
        data.put("heatmapUrl", heatmapUrl);
        data.put("createdAt", Timestamp.now());

        // 최종 저장: Firestore 'results' 컬렉션
        db.collection("results")
                .add(data)
                .addOnSuccessListener(documentReference -> {
                    // [요구사항] 로그 출력
                    Log.d(TAG, "[Firebase 저장 완료] 이미지 URL 추출 및 DB 기록 성공");
                })
                .addOnFailureListener(e -> {
                    // [요구사항] 예외 처리: DB 기록 실패 시 에러 메시지 출력
                    Log.e(TAG, "DB 기록 실패: " + e.getMessage());
                });
    }
}