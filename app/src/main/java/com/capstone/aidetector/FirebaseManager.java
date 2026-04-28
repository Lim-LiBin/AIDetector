package com.capstone.aidetector;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
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
    private final FirebaseAuth auth = FirebaseAuth.getInstance(); // 인증 정보 인스턴스 (UID 획득용)

    public interface OnUploadCompleteListener {
        void onComplete(HistoryRecord record);
    }

    //실시간으로 로그인한 사용자의 UID를 가져오는 메서드
    private String getUid() {
        if (auth.getCurrentUser() != null) {
            return auth.getCurrentUser().getUid();
        }
        return "unknown_user"; // 예외 방지용 (로그인 안 된 상태)
    }

    public void uploadAnalysisResult(AnalysisResult result, Bitmap originalBitmap, String snsUrl, OnUploadCompleteListener listener) {
        if (result == null || result.heatmapBitmap == null || originalBitmap == null) {
            Log.e(TAG, "업로드 실패: 데이터가 부족합니다.");
            return;
        }

        String uid = getUid(); // 현재 사용자의 UID 획득
        String timestampStr = String.valueOf(System.currentTimeMillis());

        ByteArrayOutputStream originalBaos = new ByteArrayOutputStream();
        originalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, originalBaos);

        //경로 체계화: originals/{uid}/파일명
        StorageReference originalRef = storage.getReference()
                .child("originals/" + uid + "/original_" + timestampStr + ".jpg");

        originalRef.putBytes(originalBaos.toByteArray()).addOnSuccessListener(task -> {
            originalRef.getDownloadUrl().addOnSuccessListener(originalUri -> {
                // 히트맵 업로드 시 SNS 주소 동시 전달 (데이터 유지용)
                uploadHeatmap(result, timestampStr, originalUri.toString(), snsUrl, listener);
            });
        }).addOnFailureListener(e -> Log.e(TAG, "원본 업로드 실패: " + e.getMessage()));
    }

    private void uploadHeatmap(AnalysisResult result, String timestampStr, String originalUrl, String snsUrl, OnUploadCompleteListener listener) {
        String uid = getUid();
        ByteArrayOutputStream heatmapBaos = new ByteArrayOutputStream();
        result.heatmapBitmap.compress(Bitmap.CompressFormat.JPEG, 90, heatmapBaos);

        //경로 체계화: heatmaps/{uid}/파일명
        StorageReference heatmapRef = storage.getReference()
                .child("heatmaps/" + uid + "/heatmap_" + timestampStr + ".jpg");

        heatmapRef.putBytes(heatmapBaos.toByteArray()).addOnSuccessListener(task -> {
            heatmapRef.getDownloadUrl().addOnSuccessListener(heatmapUri -> {
                // Firestore 저장 시 SNS 주소 동시 전달 (데이터 유지용)
                saveToFirestore(result.probability, originalUrl, heatmapUri.toString(), snsUrl, listener);
            });
        }).addOnFailureListener(e -> Log.e(TAG, "히트맵 업로드 실패: " + e.getMessage()));
    }

    private void saveToFirestore(float probability, String originalUrl, String heatmapUrl, String snsUrl, OnUploadCompleteListener listener) {
        String uid = getUid(); // 실제 UID 사용
        String resultStatus = (probability >= 50.0f) ? "Fake" : "Real";
        java.util.Date now = new java.util.Date();

        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid); // 실제 UID 저장
        data.put("result", resultStatus);
        data.put("probability", probability);
        data.put("originalUrl", originalUrl);
        data.put("heatmapUrl", heatmapUrl);
        data.put("snsUrl", snsUrl); // 원본 SNS 주소 추가 (Firestore 기록용)
        data.put("timestamp", now);

        db.collection("results").add(data)
                .addOnSuccessListener(ref -> {
                    Log.d(TAG, "[저장 완료] ID: " + ref.getId());

                    HistoryRecord newRecord = new HistoryRecord();
                    newRecord.setDocumentId(ref.getId());
                    newRecord.setUid(uid);
                    newRecord.setResult(resultStatus);
                    newRecord.setProbability(probability);
                    newRecord.setOriginalUrl(originalUrl);
                    newRecord.setHeatmapUrl(heatmapUrl);
                    newRecord.setSnsUrl(snsUrl); // 원본 SNS 주소 세팅 (객체 반환용)
                    newRecord.setTimestamp(now);

                    if (listener != null) {
                        listener.onComplete(newRecord);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "DB 저장 실패: " + e.getMessage()));
    }

    // 최신순 데이터 조회 (실제 UID 기반)
    public void loadHistory(OnHistoryLoadedListener listener) {
        String uid = getUid();
        db.collection("results")
                .whereEqualTo("uid", uid) // 내 데이터만 조회
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<HistoryRecord> historyList = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        HistoryRecord record = doc.toObject(HistoryRecord.class);
                        if (record != null) {
                            record.setDocumentId(doc.getId());
                            historyList.add(record);
                        }
                    }
                    listener.onSuccess(historyList);
                });
    }

    // DB 문서 + Storage 이미지 동시 삭제
    public void deleteHistory(HistoryRecord record, Runnable onSuccess) {
        if (record == null) return;

        storage.getReferenceFromUrl(record.getOriginalUrl()).delete().addOnCompleteListener(t1 -> {
            storage.getReferenceFromUrl(record.getHeatmapUrl()).delete().addOnCompleteListener(t2 -> {
                db.collection("results").document(record.getDocumentId()).delete()
                        .addOnSuccessListener(unused -> onSuccess.run());
            });
        });
    }

    public interface OnHistoryLoadedListener {
        void onSuccess(List<HistoryRecord> list);
    }
}