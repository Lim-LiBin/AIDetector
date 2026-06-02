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

// Firebase Storage와 Firestore를 이용한 분석 결과 저장 및 이력 관리 클래스
public class FirebaseManager {
    private static final String TAG = "FirebaseManager";
    private final FirebaseStorage storage = FirebaseStorage.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    // 업로드 완료 후 생성된 이력 객체를 전달하기 위한 콜백 인터페이스
    public interface OnUploadCompleteListener {
        void onComplete(HistoryRecord record);
    }

    // 현재 로그인한 사용자의 UID 반환
    private String getUid() {
        if (auth.getCurrentUser() != null) {
            return auth.getCurrentUser().getUid();
        }
        return "unknown_user";
    }

    // 분석 결과 이미지와 히트맵을 Firebase Storage에 업로드
    public void uploadAnalysisResult(AnalysisResult result, Bitmap originalBitmap, String snsUrl, OnUploadCompleteListener listener) {
        if (result == null || result.heatmapBitmap == null || originalBitmap == null) {
            Log.e(TAG, "업로드 실패: 데이터가 부족합니다.");
            return;
        }

        String uid = getUid();
        String timestampStr = String.valueOf(System.currentTimeMillis());

        // 원본 이미지를 JPEG 형식으로 압축
        ByteArrayOutputStream originalBaos = new ByteArrayOutputStream();
        originalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, originalBaos);

        // 사용자별 경로에 원본 이미지 저장
        StorageReference originalRef = storage.getReference()
                .child("originals/" + uid + "/original_" + timestampStr + ".jpg");

        // 원본 이미지 업로드 후 다운로드 URL을 받아 히트맵 업로드 진행
        originalRef.putBytes(originalBaos.toByteArray()).addOnSuccessListener(task -> {
            originalRef.getDownloadUrl().addOnSuccessListener(originalUri -> {
                uploadHeatmap(result, timestampStr, originalUri.toString(), snsUrl, listener);
            });
        }).addOnFailureListener(e -> Log.e(TAG, "원본 업로드 실패: " + e.getMessage()));
    }

    // 히트맵 이미지를 Firebase Storage에 업로드
    private void uploadHeatmap(AnalysisResult result, String timestampStr, String originalUrl, String snsUrl, OnUploadCompleteListener listener) {
        String uid = getUid();
        // 히트맵 이미지를 PNG 형식으로 압축
        ByteArrayOutputStream heatmapBaos = new ByteArrayOutputStream();
        result.heatmapBitmap.compress(Bitmap.CompressFormat.PNG, 100, heatmapBaos);

        // 사용자별 경로에 히트맵 이미지 저장
        StorageReference heatmapRef = storage.getReference()
                .child("heatmaps/" + uid + "/heatmap_" + timestampStr + ".png");

        // 히트맵 업로드 후 다운로드 URL을 받아 분석 결과 정보를 Firestore에 저장
        heatmapRef.putBytes(heatmapBaos.toByteArray()).addOnSuccessListener(task -> {
            heatmapRef.getDownloadUrl().addOnSuccessListener(heatmapUri -> {
                saveToFirestore(result.probability, originalUrl, heatmapUri.toString(), snsUrl, listener);
            });
        }).addOnFailureListener(e -> Log.e(TAG, "히트맵 업로드 실패: " + e.getMessage()));
    }

    // 분석 결과 정보를 Firestore에 저장
    private void saveToFirestore(float probability, String originalUrl, String heatmapUrl, String snsUrl, OnUploadCompleteListener listener) {
        String uid = getUid();
        String resultStatus = (probability >= 50.0f) ? "Fake" : "Real";
        java.util.Date now = new java.util.Date();

        // Firestore에 저장할 분석 결과 데이터 구성
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("result", resultStatus);
        data.put("probability", probability);
        data.put("originalUrl", originalUrl);
        data.put("heatmapUrl", heatmapUrl);
        data.put("snsUrl", snsUrl);
        data.put("timestamp", now);

        // results 컬렉션에 분석 결과 저장
        db.collection("results").add(data)
                .addOnSuccessListener(ref -> {
                    Log.d(TAG, "[저장 완료] ID: " + ref.getId());

                    // 저장 완료 후 화면 갱신에 사용할 HistoryRecord 객체 생성
                    HistoryRecord newRecord = new HistoryRecord();
                    newRecord.setDocumentId(ref.getId());
                    newRecord.setUid(uid);
                    newRecord.setResult(resultStatus);
                    newRecord.setProbability(probability);
                    newRecord.setOriginalUrl(originalUrl);
                    newRecord.setHeatmapUrl(heatmapUrl);
                    newRecord.setSnsUrl(snsUrl);
                    newRecord.setTimestamp(now);

                    if (listener != null) {
                        listener.onComplete(newRecord);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "DB 저장 실패: " + e.getMessage()));
    }

    // 로그인한 사용자의 분석 이력을 최신순으로 조회
    public void loadHistory(OnHistoryLoadedListener listener) {
        String uid = getUid();

        // 사용자 UID 기준으로 결과 목록 조회
        db.collection("results")
                .whereEqualTo("uid", uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<HistoryRecord> historyList = new ArrayList<>();

                    // Firestore 문서를 HistoryRecord 객체로 변환
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

    // 분석 이력의 Firestore 문서와 Storage 이미지를 삭제
    public void deleteHistory(HistoryRecord record, Runnable onSuccess) {
        if (record == null) return;

        // 원본 이미지와 히트맵 이미지를 삭제한 뒤 Firestore 문서 삭제
        storage.getReferenceFromUrl(record.getOriginalUrl()).delete().addOnCompleteListener(t1 -> {
            storage.getReferenceFromUrl(record.getHeatmapUrl()).delete().addOnCompleteListener(t2 -> {
                db.collection("results").document(record.getDocumentId()).delete()
                        .addOnSuccessListener(unused -> onSuccess.run());
            });
        });
    }

    // 분석 이력 조회 결과 전달용 콜백 인터페이스
    public interface OnHistoryLoadedListener {
        void onSuccess(List<HistoryRecord> list);
    }
}