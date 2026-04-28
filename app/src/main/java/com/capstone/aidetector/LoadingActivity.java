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

        tvStepText = findViewById(R.id.tv_step_text);
        step1 = findViewById(R.id.step1);
        step2 = findViewById(R.id.step2);
        step3 = findViewById(R.id.step3);

        boolean isVideoMode = getIntent().getBooleanExtra("is_video_mode", false);
        boolean isFromUrl = getIntent().getBooleanExtra("is_from_url", false);
        boolean isAlreadyAnalyzed = getIntent().getBooleanExtra("is_already_analyzed", false);
        boolean isLocalImage = getIntent().getBooleanExtra("is_local_image", false);

        startLoadingAnimation();

        if (isAlreadyAnalyzed) {
            savedRecord = (HistoryRecord) getIntent().getSerializableExtra("record");
            savedResult = (AnalysisResult) getIntent().getParcelableExtra("analysis_result");
        } else if (isVideoMode) {
            performVideoAnalysis(getIntent().getStringExtra("video_url"));
        } else if (isFromUrl) {
            loadAndAnalyzeUrlImage(getIntent().getStringExtra("image_url"));
        } else {
            // ⭐️ [수정] Intent 용량 제한 회피를 위해 BitmapHolder에서 직접 가져옵니다.
            Bitmap bitmap = BitmapHolder.originalBitmap;
            if (bitmap != null) {
                new Thread(() -> performAnalysis(bitmap)).start();
            } else {
                finishWithError("분석할 이미지가 없습니다.");
            }
        }
    }

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

    private void processVideoServerResult(VideoAnalysisResponse res) {
        new Thread(() -> {
            try {
                byte[] bytes = Base64.decode(res.getFrameBase64(), Base64.DEFAULT);
                Bitmap frameBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                // 원본 크기 저장
                int originalWidth = frameBitmap.getWidth();
                int originalHeight = frameBitmap.getHeight();

                // 영상 분석의 경우 서버에서 받은 프레임을 홀더에 저장
                BitmapHolder.originalBitmap = frameBitmap;

                // 원본 영상 프레임을 보관소에 저장
                BitmapHolder.originalBitmap = frameBitmap;

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
                // 원본 크기에 맞춰 히트맵 생성
                Bitmap heatmapBitmap = heatmapProcessor.createHeatmapImage(
                        heatmapMatrix,
                        originalWidth,
                        originalHeight
                );
                BitmapHolder.heatmapBitmap = heatmapBitmap;

                float prob = res.getProbability() * 100f;
                savedResult = new AnalysisResult(prob, null);
                AnalysisResult uploadResult = new AnalysisResult(prob, heatmapBitmap);

                // MainActivity에서 받아온 snsUrl을 전달
                String snsUrl = getIntent().getStringExtra("snsUrl");

                new FirebaseManager().uploadAnalysisResult(uploadResult, frameBitmap, snsUrl, record -> {
                    savedRecord = record;
                    checkDataAndMove();
                });
            } catch (Exception e) {
                Log.e(TAG, "영상 결과 처리 오류", e);
                finishWithError("결과 처리 중 오류 발생");
            }
        }).start();
    }

    private void performAnalysis(Bitmap bitmap) {
        if (bitmap == null) {
            finishWithError("이미지 데이터가 없습니다.");
            return;
        }

        try {
            // 원본 크기 저장 (히트맵 생성용)
            int originalWidth = bitmap.getWidth();
            int originalHeight = bitmap.getHeight();

            // ⭐️ [수정] 이중 리사이징 제거: 1024px로 줄이지 않고 원본 그대로 분석에 사용합니다.
            // AiProcessor 내부의 ResizeOp(224, 224)만 거치게 되어 화질 손실이 최소화됩니다.
            AiProcessor aiProcessor = new AiProcessor(this);
            TensorImage processedImage = aiProcessor.processImage(bitmap); // bitmap 직접 사용
            Map<String, Object> results = aiProcessor.runInference(processedImage);

            float score = (float) results.get("score");
            float[][] heatmapMatrix = (float[][]) results.get("heatmap");

            HeatmapProcessor heatmapProcessor = new HeatmapProcessor();
            Bitmap heatmapBitmap = heatmapProcessor.createHeatmapImage(
                    heatmapMatrix,
                    originalWidth,
                    originalHeight
            );
            BitmapHolder.heatmapBitmap = heatmapBitmap;

            float prob = score * 100f;
            savedResult = new AnalysisResult(prob, null);

            // MainActivity에서 받아온 snsUrl을 전달
            String snsUrl = getIntent().getStringExtra("snsUrl");

            new FirebaseManager().uploadAnalysisResult(new AnalysisResult(prob, heatmapBitmap), scaledBitmap, snsUrl, record -> {
                savedRecord = record;
                checkDataAndMove();
            });
        } catch (Exception e) {
            Log.e(TAG, "분석 오류", e);
            finishWithError("AI 분석 중 오류가 발생했습니다.");
        }
    }

    private Bitmap scaleBitmapIfNeeded(Bitmap bitmap) {
        int maxSide = 1024;
        if (bitmap.getWidth() <= maxSide && bitmap.getHeight() <= maxSide) return bitmap;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float ratio = (float) width / (float) height;

        if (width > height) {
            width = maxSide;
            height = (int) (maxSide / ratio);
        } else {
            height = maxSide;
            width = (int) (maxSide * ratio);
        }
        return Bitmap.createScaledBitmap(bitmap, width, height, true);
    }

    private void loadAndAnalyzeUrlImage(String url) {
        Glide.with(this).asBitmap().load(url).into(new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap res, @Nullable Transition<? super Bitmap> t) {
                // ⭐️ URL 분석 이미지도 홀더에 보관
                BitmapHolder.originalBitmap = res;
                new Thread(() -> performAnalysis(res)).start();
            }
            @Override
            public void onLoadFailed(@Nullable Drawable d) { finishWithError("이미지 로드 실패"); }
            @Override
            public void onLoadCleared(@Nullable Drawable p) {}
        });
    }

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

    private void checkDataAndMove() {
        if (isAnimationFinished && savedRecord != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                Intent nextIntent = new Intent(LoadingActivity.this, ResultActivity.class);

                nextIntent.putExtra("record", savedRecord);
                nextIntent.putExtra("analysis_result", savedResult);

                // ⭐️ 이제 용량 큰 byte[]는 아예 보내지 않습니다.
                String originalUri = getIntent().getStringExtra("original_image_uri");
                if (originalUri == null) {
                    originalUri = getIntent().getStringExtra("image_url");
                }

                if (originalUri != null) {
                    nextIntent.putExtra("original_image_uri", originalUri);
                }

                startActivity(nextIntent);
                finish();
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