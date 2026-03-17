package com.capstone.aidetector;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.capstone.aidetector.HistoryRecord;

import org.tensorflow.lite.support.image.TensorImage;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LoadingActivity extends AppCompatActivity {

    private static final String TAG = "LoadingActivity";
    private HistoryRecord savedRecord;
    private AnalysisResult savedResult;
    private boolean isAnimationFinished = false;

    private TextView tvStepText;
    private View step1, step2, step3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        // 1. 뷰 초기화
        tvStepText = findViewById(R.id.tv_step_text);
        step1 = findViewById(R.id.step1);
        step2 = findViewById(R.id.step2);
        step3 = findViewById(R.id.step3);

        // 2. 인텐트 플래그 확인
        boolean isVideoMode = getIntent().getBooleanExtra("is_video_mode", false);
        boolean isFromUrl = getIntent().getBooleanExtra("is_from_url", false);
        boolean isAlreadyAnalyzed = getIntent().getBooleanExtra("is_already_analyzed", false);

        startLoadingAnimation();

        // 3. 모드별 로직 실행
        if (isAlreadyAnalyzed) {
            // Case 1: 이미 MainActivity에서 분석/저장이 끝난 경우 (단순 로딩 대기)
            savedRecord = (HistoryRecord) getIntent().getSerializableExtra("record");
            savedResult = (AnalysisResult) getIntent().getParcelableExtra("analysis_result");
        } else if (isVideoMode) {
            // Case 2: 영상 URL 분석 (서버 통신 필요)
            performVideoAnalysis(getIntent().getStringExtra("video_url"));
        } else if (isFromUrl) {
            // Case 3: 이미지 URL 분석 (다운로드 후 AI 분석)
            loadAndAnalyzeUrlImage(getIntent().getStringExtra("image_url"));
        } else {
            // Case 4: 일반 이미지 (갤러리/카메라 비트맵 분석)
            byte[] bytes = getIntent().getByteArrayExtra("original_image_bytes");
            if (bytes != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                new Thread(() -> performAnalysis(bitmap)).start();
            }
        }
    }

    // [서버] 영상 분석 요청
    private void performVideoAnalysis(String url) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ServerConfig.getBaseUrl())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        PostService service = retrofit.create(PostService.class);
        service.analyzeVideo(new VideoAnalysisRequest(url)).enqueue(new Callback<VideoAnalysisResponse>() {
            @Override
            public void onResponse(Call<VideoAnalysisResponse> call, Response<VideoAnalysisResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    processVideoServerResult(response.body());
                } else { finishWithError("서버 응답 실패"); }
            }
            @Override
            public void onFailure(Call<VideoAnalysisResponse> call, Throwable t) { finishWithError("네트워크 실패"); }
        });
    }

    // [서버] 영상 분석 결과 처리 (히트맵 생성 및 DB 저장)
    private void processVideoServerResult(VideoAnalysisResponse res) {
        new Thread(() -> {
            try {
                byte[] bytes = Base64.decode(res.getFrameBase64(), Base64.DEFAULT);
                Bitmap frameBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                List<List<Float>> heatmapList = res.getHeatmap();
                int rows = heatmapList.size();
                int cols = heatmapList.get(0).size();
                float[][] heatmapMatrix = new float[rows][cols];

                for (int i = 0; i < rows; i++) {
                    for (int j = 0; j < cols; j++) {
                        heatmapMatrix[i][j] = heatmapList.get(i).get(j);
                    }
                }

                HeatmapProcessor heatmapProcessor = new HeatmapProcessor();
                Bitmap heatmapBitmap = heatmapProcessor.createHeatmapImage(heatmapMatrix);
                BitmapHolder.heatmapBitmap = heatmapBitmap;

                float prob = res.getProbability() * 100f;
                savedResult = new AnalysisResult(prob, null);
                AnalysisResult uploadResult = new AnalysisResult(prob, heatmapBitmap);

                new FirebaseManager().uploadAnalysisResult(uploadResult, frameBitmap, record -> {
                    savedRecord = record;
                    checkDataAndMove();
                });
            } catch (Exception e) {
                Log.e(TAG, "영상 결과 처리 오류", e);
                finishWithError("결과 처리 중 오류 발생");
            }
        }).start();
    }

    // [AI] 이미지 분석 로직 (로컬 TFLite)
    private void performAnalysis(Bitmap bitmap) {
        try {
            AiProcessor aiProcessor = new AiProcessor(this);
            TensorImage processedImage = aiProcessor.processImage(bitmap);
            Map<String, Object> results = aiProcessor.runInference(processedImage);

            float score = (float) results.get("score");
            float[][] heatmapMatrix = (float[][]) results.get("heatmap");

            HeatmapProcessor heatmapProcessor = new HeatmapProcessor();
            Bitmap heatmapBitmap = heatmapProcessor.createHeatmapImage(heatmapMatrix);
            BitmapHolder.heatmapBitmap = heatmapBitmap;

            float prob = score * 100f;
            savedResult = new AnalysisResult(prob, null);

            new FirebaseManager().uploadAnalysisResult(new AnalysisResult(prob, heatmapBitmap), bitmap, record -> {
                savedRecord = record;
                checkDataAndMove();
            });
        } catch (Exception e) { Log.e(TAG, "분석 오류", e); }
    }

    private void loadAndAnalyzeUrlImage(String url) {
        Glide.with(this).asBitmap().load(url).into(new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap res, @Nullable Transition<? super Bitmap> t) {
                new Thread(() -> performAnalysis(res)).start();
            }
            @Override
            public void onLoadFailed(@Nullable Drawable d) { finishWithError("이미지 로드 실패"); }
            @Override
            public void onLoadCleared(@Nullable Drawable p) {}
        });
    }

    // --- 애니메이션 및 화면 전환 로직 ---

    private void startLoadingAnimation() {
        ValueAnimator animator = ValueAnimator.ofInt(0, 100);
        animator.setDuration(3000);
        animator.addUpdateListener(animation -> updateLoadingUI((int) animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                isAnimationFinished = true;
                checkDataAndMove();
            }
        });
        animator.start();
    }

    private void updateLoadingUI(int progress) {
        if (progress < 33) {
            setStepColors("#FF5E62", "#333333", "#333333");
            tvStepText.setText("데이터 읽는 중...");
        } else if (progress < 66) {
            setStepColors("#333333", "#FFEA00", "#333333");
            tvStepText.setText("AI 분석 중...");
        } else {
            setStepColors("#333333", "#333333", "#00FF7F");
            tvStepText.setText("결과 준비 중...");
        }
    }

    private void setStepColors(String c1, String c2, String c3) {
        step1.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(c1)));
        step2.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(c2)));
        step3.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(c3)));
    }

    // LoadingActivity.java 의 checkDataAndMove 메서드 부분만 바꿔도 됩니다.
    private void checkDataAndMove() {
        // 애니메이션이 끝났고(isAnimationFinished), DB 저장(savedRecord)도 완료되었다면
        if (isAnimationFinished && savedRecord != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                Intent nextIntent = new Intent(LoadingActivity.this, ResultActivity.class);

                // 1. 분석 결과 정보 전달
                nextIntent.putExtra("record", savedRecord);
                nextIntent.putExtra("analysis_result", savedResult);

                // ⭐️ [가장 중요] MainActivity에서 받은 원본 바이트 배열을 "다시" 꺼내서 전달!
                // 이걸 안 하면 ResultActivity에서 사진을 그릴 재료가 없어요.
                byte[] originalBytes = getIntent().getByteArrayExtra("original_image_bytes");
                if (originalBytes != null) {
                    nextIntent.putExtra("original_image_bytes", originalBytes);
                }

                // ⭐️ URL 분석인 경우 주소도 다시 전달
                String originalUri = getIntent().getStringExtra("original_image_uri");
                if (originalUri != null) {
                    nextIntent.putExtra("original_image_uri", originalUri);
                }

                startActivity(nextIntent);
                finish();
                // 부드러운 화면 전환
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        }
    }

    private void finishWithError(String msg) {
        runOnUiThread(() -> {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}