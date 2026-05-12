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
import android.media.MediaMetadataRetriever;
import android.net.Uri;
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

        boolean isLocalVideo = getIntent().getBooleanExtra("is_local_video", false);
        boolean isVideoMode = getIntent().getBooleanExtra("is_video_mode", false);
        boolean isFromUrl = getIntent().getBooleanExtra("is_from_url", false);
        boolean isAlreadyAnalyzed = getIntent().getBooleanExtra("is_already_analyzed", false);

        startLoadingAnimation();

        if (isAlreadyAnalyzed) {
            savedRecord = (HistoryRecord) getIntent().getSerializableExtra("record");
            savedResult = (AnalysisResult) getIntent().getParcelableExtra("analysis_result");
        } else if (isLocalVideo) {
            // ⭐️ 갤러리 영상 로컬 전수 조사 실행
            Uri videoUri = Uri.parse(getIntent().getStringExtra("video_url"));
            performLocalVideoAnalysis(videoUri);
        } else if (isVideoMode) {
            String videoUrl = getIntent().getStringExtra("video_url");
            checkUrlThenProceed(videoUrl, () -> performVideoAnalysis(videoUrl));
        } else if (isFromUrl) {
            String imageUrl = getIntent().getStringExtra("image_url");
            checkUrlThenProceed(imageUrl, () -> loadAndAnalyzeUrlImage(imageUrl));
        } else {
            Bitmap bitmap = BitmapHolder.originalBitmap;
            if (bitmap != null) {
                new Thread(() -> performAnalysis(bitmap)).start();
            } else {
                finishWithError("분석할 이미지가 없습니다.");
            }
        }
    }

    // ⭐️ [신규] 로컬 영상 0.5초 간격 전수 조사 (최댓값 추출)
    private void performLocalVideoAnalysis(Uri videoUri) {
        new Thread(() -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                Log.d(TAG, "로컬 영상 분석 시작 - URI: " + videoUri);

                // 1. [중요] Context(this)를 함께 전달해야 content:// URI에 안전하게 접근합니다.
                retriever.setDataSource(this, videoUri);

                String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (time == null) {
                    Log.e(TAG, "영상 메타데이터 추출 실패 (Duration is null)");
                    finishWithError("영상을 읽을 수 없습니다.");
                    return;
                }

                long durationUs = Long.parseLong(time) * 1000;
                Log.d(TAG, "영상 총 길이(us): " + durationUs);

                float maxScore = -1f;
                Bitmap bestFrame = null;
                float[][] bestHeatmap = null;

                AiProcessor aiProcessor = new AiProcessor(this);

                // 0.5초 간격으로 분석
                for (long i = 0; i < durationUs; i += 500000) {
                    // 2. [수정] OPTION_CLOSEST_SYNC 대신 OPTION_CLOSEST를 사용하세요.
                    // SYNC는 키프레임만 찾아서 null을 줄 때가 많지만, CLOSEST는 어떻게든 가장 가까운 화면을 그려냅니다.
                    Bitmap frame = retriever.getFrameAtTime(i, MediaMetadataRetriever.OPTION_CLOSEST);

                    if (frame == null) {
                        Log.w(TAG, i + "us 지점 프레임 추출 실패 - 건너뜁니다.");
                        continue;
                    }

                    // AI 분석 수행
                    TensorImage tensorImage = aiProcessor.processImage(frame);
                    Map<String, Object> results = aiProcessor.runInference(tensorImage);
                    float currentScore = (float) results.get("score") * 100f;

                    if (currentScore > maxScore) {
                        maxScore = currentScore;
                        bestFrame = frame;
                        bestHeatmap = (float[][]) results.get("heatmap");
                    }
                }

                if (bestFrame != null) {
                    finalizeAnalysis(bestFrame, maxScore, bestHeatmap);
                } else {
                    Log.e(TAG, "모든 프레임 추출에 실패함");
                    finishWithError("영상에서 이미지를 뽑아낼 수 없습니다.");
                }
            } catch (Exception e) {
                Log.e(TAG, "로컬 영상 분석 중 예외 발생", e);
                finishWithError("분석 오류: " + e.getMessage());
            } finally {
                try {
                    retriever.release();
                } catch (Exception ignored) {}
            }
        }).start();
    }

    // ⭐️ [통합] 원본 크기 유지 및 결과 처리 공통 함수
    private void finalizeAnalysis(Bitmap bitmap, float score, float[][] heatmapMatrix) {
        // 비트맵 객체 저장 (원본 크기 보존)
        BitmapHolder.originalBitmap = bitmap;

        // 원본 크기(bitmap.getWidth/Height)를 사용하여 히트맵 생성
        HeatmapProcessor hp = new HeatmapProcessor();
        Bitmap heatmapBitmap = hp.createHeatmapImage(heatmapMatrix, bitmap.getWidth(), bitmap.getHeight());
        BitmapHolder.heatmapBitmap = heatmapBitmap;

        savedResult = new AnalysisResult(score, null);

        String snsUrl = getIntent().getStringExtra("snsUrl");

        // Firebase 업로드
        if (snsUrl == null && !getIntent().getBooleanExtra("is_local_video", false)) {
            if (getIntent().getBooleanExtra("is_from_url", false)) {
                snsUrl = getIntent().getStringExtra("image_url");
            } else if (getIntent().getBooleanExtra("is_video_mode", false)) {
                snsUrl = getIntent().getStringExtra("video_url");
            }
        }

        new FirebaseManager().uploadAnalysisResult(new AnalysisResult(score, heatmapBitmap), bitmap, snsUrl, record -> {
            savedRecord = record;
            checkDataAndMove();
        });
    }

    // ⭐️ [통합] 이미지 분석 로직
    private void performAnalysis(Bitmap bitmap) {
        if (bitmap == null) { finishWithError("이미지 데이터가 없습니다."); return; }
        try {
            AiProcessor aiProcessor = new AiProcessor(this);
            TensorImage processedImage = aiProcessor.processImage(bitmap);
            Map<String, Object> results = aiProcessor.runInference(processedImage);

            float score = (float) results.get("score") * 100f;
            float[][] heatmapMatrix = (float[][]) results.get("heatmap");

            finalizeAnalysis(bitmap, score, heatmapMatrix);
        } catch (Exception e) {
            Log.e(TAG, "분석 오류", e);
            finishWithError("AI 분석 중 오류가 발생했습니다.");
        }
    }

    // 서버 분석 결과 처리 (원본 크기 대응)
    private void processVideoServerResult(VideoAnalysisResponse res) {
        new Thread(() -> {
            try {
                byte[] bytes = Base64.decode(res.getFrameBase64(), Base64.DEFAULT);
                Bitmap frameBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                float prob = res.getProbability() * 100f;

                List<List<Float>> heatmapList = res.getHeatmap();
                float[][] heatmapMatrix = new float[heatmapList.size()][heatmapList.get(0).size()];
                for (int i = 0; i < heatmapList.size(); i++) {
                    for (int j = 0; j < heatmapList.get(i).size(); j++) {
                        heatmapMatrix[i][j] = heatmapList.get(i).get(j);
                    }
                }
                // 통합 함수 호출 (frameBitmap의 크기 정보 자동 인식)
                finalizeAnalysis(frameBitmap, prob, heatmapMatrix);
            } catch (Exception e) {
                Log.e(TAG, "영상 결과 처리 오류", e);
                finishWithError("결과 처리 중 오류 발생");
            }
        }).start();
    }

    // 기존 URL 체크 로직 (로그 포함 유지)
    private void checkUrlThenProceed(String url, Runnable onSafe) {
        Log.d(TAG, "URL 검사 시작: " + url);
        Retrofit retrofit = new Retrofit.Builder().baseUrl(ServerConfig.getBaseUrl()).addConverterFactory(GsonConverterFactory.create()).build();
        PostService service = retrofit.create(PostService.class);
        Log.d(TAG, "서버 요청 전송 시도 중...");

        service.checkUrl(new UrlCheckRequest(url)).enqueue(new Callback<UrlCheckResponse>() {
            @Override
            public void onResponse(Call<UrlCheckResponse> call, Response<UrlCheckResponse> response) {
                Log.d(TAG, "onResponse 진입: " + response.code());
                if (response.isSuccessful() && response.body() != null) {
                    UrlCheckResponse result = response.body();
                    if (result.isSuspicious()) {
                        runOnUiThread(() -> UrlCheckDialog.showWarning(LoadingActivity.this, result, new UrlCheckDialog.OnUserDecision() {
                            @Override public void onProceed() { onSafe.run(); }
                            @Override public void onBlock() { finishWithError("위험한 링크로 판단되어 접속이 차단되었습니다."); }
                        }));
                    } else { onSafe.run(); }
                } else { onSafe.run(); }
            }
            @Override public void onFailure(Call<UrlCheckResponse> call, Throwable t) {
                Log.e(TAG, "checkUrl onFailure 진입!", t);
                onSafe.run();
            }
        });
    }

    private void performVideoAnalysis(String url) {
        Retrofit retrofit = new Retrofit.Builder().baseUrl(ServerConfig.getBaseUrl()).addConverterFactory(GsonConverterFactory.create()).build();
        PostService service = retrofit.create(PostService.class);
        service.analyzeVideo(new VideoAnalysisRequest(url)).enqueue(new Callback<VideoAnalysisResponse>() {
            @Override
            public void onResponse(Call<VideoAnalysisResponse> call, Response<VideoAnalysisResponse> response) {
                if (response.isSuccessful() && response.body() != null) processVideoServerResult(response.body());
                else finishWithError("서버 응답 실패");
            }
            @Override public void onFailure(Call<VideoAnalysisResponse> call, Throwable t) { finishWithError("네트워크 실패"); }
        });
    }

    private void loadAndAnalyzeUrlImage(String url) {
        Glide.with(this).asBitmap().load(url).into(new CustomTarget<Bitmap>() {
            @Override public void onResourceReady(@NonNull Bitmap res, @Nullable Transition<? super Bitmap> t) {
                BitmapHolder.originalBitmap = res;
                new Thread(() -> performAnalysis(res)).start();
            }
            @Override public void onLoadFailed(@Nullable Drawable d) { finishWithError("이미지 로드 실패"); }
            @Override public void onLoadCleared(@Nullable Drawable p) {}
        });
    }

    private void startLoadingAnimation() {
        ValueAnimator animator = ValueAnimator.ofInt(0, 100);
        animator.setDuration(3000);
        animator.addUpdateListener(animation -> updateLoadingUI((int) animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                isAnimationFinished = true;
                checkDataAndMove();
            }
        });
        animator.start();
    }

    private void updateLoadingUI(int progress) {
        if (progress < 33) { setStepColors("#FF5E62", "#333333", "#333333"); tvStepText.setText("데이터 읽는 중..."); }
        else if (progress < 66) { setStepColors("#333333", "#FFEA00", "#333333"); tvStepText.setText("AI 분석 중..."); }
        else { setStepColors("#333333", "#333333", "#00FF7F"); tvStepText.setText("결과 준비 중..."); }
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

                String snsUrl = getIntent().getStringExtra("snsUrl");
                if (snsUrl == null && !getIntent().getBooleanExtra("is_local_video", false)) {
                    if (getIntent().getBooleanExtra("is_from_url", false)) {
                        snsUrl = getIntent().getStringExtra("image_url");
                    } else if (getIntent().getBooleanExtra("is_video_mode", false)) {
                        snsUrl = getIntent().getStringExtra("video_url");
                    }
                }

                if (snsUrl != null) nextIntent.putExtra("snsUrl", snsUrl);

                String originalUri = getIntent().getStringExtra("original_image_uri");
                if (originalUri == null) originalUri = getIntent().getStringExtra("image_url");
                if (originalUri != null) nextIntent.putExtra("original_image_uri", originalUri);

                startActivity(nextIntent);
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        }
    }

    private void finishWithError(String msg) {
        runOnUiThread(() -> { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); finish(); });
    }
}