package com.capstone.aidetector;

import android.graphics.Bitmap;
import android.util.Log;

import com.capstone.aidetector.model.HistoryRecord;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseManager {
    private static final String TAG = "FirebaseManager";
    private final FirebaseStorage storage = FirebaseStorage.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface OnUploadCompleteListener {
        void onComplete(HistoryRecord record);
    }

    // 고정 UID 설정
    private final String TEST_UID = "test_user_01";

    public void uploadAnalysisResult(AnalysisResult result, Bitmap originalBitmap, OnUploadCompleteListener listener) {
        if (result == null || result.heatmapBitmap == null || originalBitmap == null) {
            Log.e(TAG, "업로드 실패: 데이터가 부족합니다.");
            return;
        }

        String timestampStr = String.valueOf(System.currentTimeMillis());

        ByteArrayOutputStream originalBaos = new ByteArrayOutputStream();
        originalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, originalBaos);
        StorageReference originalRef = storage.getReference().child("originals/original_" + timestampStr + ".jpg");

        originalRef.putBytes(originalBaos.toByteArray()).addOnSuccessListener(task -> {
            originalRef.getDownloadUrl().addOnSuccessListener(originalUri -> {
                // [수정] listener를 다음 함수로 전달
                uploadHeatmap(result, timestampStr, originalUri.toString(), listener);
            });
        }).addOnFailureListener(e -> Log.e(TAG, "원본 업로드 실패: " + e.getMessage()));
    }

    private void uploadHeatmap(AnalysisResult result, String timestampStr, String originalUrl, OnUploadCompleteListener listener) {
        ByteArrayOutputStream heatmapBaos = new ByteArrayOutputStream();
        result.heatmapBitmap.compress(Bitmap.CompressFormat.JPEG, 90, heatmapBaos);
        StorageReference heatmapRef = storage.getReference().child("heatmaps/heatmap_" + timestampStr + ".jpg");

        heatmapRef.putBytes(heatmapBaos.toByteArray()).addOnSuccessListener(task -> {
            heatmapRef.getDownloadUrl().addOnSuccessListener(heatmapUri -> {
                // [수정] listener를 최종 저장 함수로 전달
                saveToFirestore(result.probability, originalUrl, heatmapUri.toString(), listener);
            });
        }).addOnFailureListener(e -> Log.e(TAG, "히트맵 업로드 실패: " + e.getMessage()));
    }

    private void saveToFirestore(float probability, String originalUrl, String heatmapUrl, OnUploadCompleteListener listener) {
        //50% 기준으로 Real/Fake 판별 로직 추가
        String resultStatus = (probability >= 50.0f) ? "Fake" : "Real";

        java.util.Date now = new java.util.Date();

        Map<String, Object> data = new HashMap<>();
        data.put("uid", TEST_UID); // UID 추가
        data.put("result", resultStatus); // 결과값 추가
        data.put("probability", probability);
        data.put("originalUrl", originalUrl);
        data.put("heatmapUrl", heatmapUrl);
        data.put("timestamp", now);

        db.collection("results").add(data)
                .addOnSuccessListener(ref -> {
                    Log.d(TAG, "[저장 완료] ID: " + ref.getId());

                    // [핵심!] 방금 저장된 정보를 HistoryRecord 객체로 만들어 MainActivity에 전달
                    HistoryRecord newRecord = new HistoryRecord();
                    newRecord.setDocumentId(ref.getId());
                    newRecord.setUid(TEST_UID);
                    newRecord.setResult(resultStatus);
                    newRecord.setProbability(probability);
                    newRecord.setOriginalUrl(originalUrl);
                    newRecord.setHeatmapUrl(heatmapUrl);
                    newRecord.setTimestamp(now);

                    if (listener != null) {
                        listener.onComplete(newRecord);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "DB 저장 실패: " + e.getMessage()));
    }

    //최신순 데이터 조회
    public void loadHistory(OnHistoryLoadedListener listener) {
        db.collection("results")
                .whereEqualTo("uid", TEST_UID)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<HistoryRecord> historyList = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        HistoryRecord record = doc.toObject(HistoryRecord.class);
                        if (record != null) {
                            record.setDocumentId(doc.getId()); // 삭제를 위해 문서 ID 저장
                            historyList.add(record);
                        }
                    }
                    listener.onSuccess(historyList);
                });
    }

    //DB 문서 + Storage 이미지 동시 삭제
    public void deleteHistory(HistoryRecord record, Runnable onSuccess) {
        if (record == null) return;

        // 원본 삭제 시도 -> 히트맵 삭제 시도 -> DB 삭제 순서 (완료 콜백 사용)
        storage.getReferenceFromUrl(record.getOriginalUrl()).delete().addOnCompleteListener(t1 -> {
            storage.getReferenceFromUrl(record.getHeatmapUrl()).delete().addOnCompleteListener(t2 -> {
                db.collection("results").document(record.getDocumentId()).delete()
                        .addOnSuccessListener(unused -> onSuccess.run());
            });
        });
    }

    // 인터페이스 정의
    public interface OnHistoryLoadedListener {
        void onSuccess(List<HistoryRecord> list);
    }
}