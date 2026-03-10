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

    public void uploadAnalysisResult(AnalysisResult result, Bitmap originalBitmap) {
        if (result == null || result.heatmapBitmap == null || originalBitmap == null) {
            Log.e(TAG, "업로드 실패: 결과값 또는 원본 이미지가 없습니다.");
            return;
        }

        // 1. 원본 사진 업로드 준비
        ByteArrayOutputStream originalBaos = new ByteArrayOutputStream();
        originalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, originalBaos);
        byte[] originalData = originalBaos.toByteArray();

        String timestamp = String.valueOf(System.currentTimeMillis());
        String originalFileName = "original_" + timestamp + ".jpg";
        StorageReference originalRef = storage.getReference().child("originals/" + originalFileName);

        // 2. 원본 사진 업로드 시작
        originalRef.putBytes(originalData).addOnSuccessListener(originalTask -> {
            originalRef.getDownloadUrl().addOnSuccessListener(originalUri -> {

                // 3. 원본 업로드 성공 시 히트맵 업로드 시작
                uploadHeatmap(result, timestamp, originalUri.toString());

            });
        }).addOnFailureListener(e -> Log.e(TAG, "원본 업로드 실패: " + e.getMessage()));
    }

    private void uploadHeatmap(AnalysisResult result, String timestamp, String originalUrl) {
        ByteArrayOutputStream heatmapBaos = new ByteArrayOutputStream();
        result.heatmapBitmap.compress(Bitmap.CompressFormat.JPEG, 90, heatmapBaos);
        byte[] heatmapData = heatmapBaos.toByteArray();

        String heatmapFileName = "heatmap_" + timestamp + ".jpg";
        StorageReference heatmapRef = storage.getReference().child("heatmaps/" + heatmapFileName);

        heatmapRef.putBytes(heatmapData).addOnSuccessListener(heatmapTask -> {
            heatmapRef.getDownloadUrl().addOnSuccessListener(heatmapUri -> {

                // 4. 두 URL을 모두 가지고 Firestore에 최종 저장
                saveToFirestore(result.probability, originalUrl, heatmapUri.toString());

            });
        }).addOnFailureListener(e -> Log.e(TAG, "히트맵 업로드 실패: " + e.getMessage()));
    }

    private void saveToFirestore(float probability, String originalUrl, String heatmapUrl) {
        Map<String, Object> data = new HashMap<>();
        data.put("probability", probability);
        data.put("originalUrl", originalUrl); // 원본 URL 추가
        data.put("heatmapUrl", heatmapUrl);
        data.put("createdAt", Timestamp.now());

        db.collection("results").add(data)
                .addOnSuccessListener(ref -> Log.d(TAG, "[Firebase 저장 완료] 모든 이미지 및 DB 기록 성공"))
                .addOnFailureListener(e -> Log.e(TAG, "DB 기록 실패: " + e.getMessage()));
    }
}