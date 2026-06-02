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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.support.image.TensorImage;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

// 이미지/영상 분석 중 로딩 화면을 표시하고 분석 완료 후 결과 화면으로 이동하는 Activity
public class LoadingActivity extends AppCompatActivity {

    private static final String TAG = "LoadingActivity";
    private HistoryRecord savedRecord;
    private AnalysisResult savedResult;
    private boolean isAnimationFinished = false;
    private ProgressBar loadingIndicator;

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
        loadingIndicator = findViewById(R.id.loadingIndicator);

        boolean isLocalVideo = getIntent().getBooleanExtra("is_local_video", false);
        boolean isVideoMode = getIntent().getBooleanExtra("is_video_mode", false);
        boolean isFromUrl = getIntent().getBooleanExtra("is_from_url", false);
        boolean isAlreadyAnalyzed = getIntent().getBooleanExtra("is_already_analyzed", false);

        startLoadingAnimation();

        if (isAlreadyAnalyzed) {
            // 이미 분석된 결과를 전달받은 경우 저장된 데이터 사용
            savedRecord = (HistoryRecord) getIntent().getSerializableExtra("record");
            savedResult = (AnalysisResult) getIntent().getParcelableExtra("analysis_result");
        } else if (isLocalVideo) {
            // 갤러리 영상 로컬 분석 실행
            Uri videoUri = getIntent().getData();
            if (videoUri == null) {
                videoUri = Uri.parse(getIntent().getStringExtra("video_uri"));
            }
            performLocalVideoAnalysis(videoUri);
        } else if (isVideoMode) {
            // 영상 URL 검사 후 서버 영상 분석 실행
            String videoUrl = getIntent().getStringExtra("video_url");
            checkUrlThenProceed(videoUrl, () -> performVideoAnalysis(videoUrl));
        } else if (isFromUrl) {
            // 이미지 URL 검사 후 이미지 로드 및 분석 실행
            String imageUrl = getIntent().getStringExtra("image_url");
            checkUrlThenProceed(imageUrl, () -> loadAndAnalyzeUrlImage(imageUrl));
        } else {
            // 기기에 저장된 이미지 분석 실행
            Bitmap bitmap = BitmapHolder.originalBitmap;
            if (bitmap != null) {
                new Thread(() -> performAnalysis(bitmap)).start();
            } else {
                finishWithError("분석할 이미지가 없습니다.");
            }
        }
    }

    // 로컬 영상을 일정 간격으로 샘플링하여 가장 높은 조작 확률 프레임을 찾는 함수
    private void performLocalVideoAnalysis(Uri videoUri) {
        new Thread(() -> {

            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            java.io.File tempFile = null;

            try {

                Log.d(TAG, "로컬 영상 분석 시작: " + videoUri);
                 tempFile = createTempFileFromUri(videoUri);

                if (tempFile != null && tempFile.exists()) {
                    retriever.setDataSource(tempFile.getAbsolutePath());
                    Log.d(TAG, "temp file 사용: " + tempFile.getAbsolutePath());
                } else {
                    retriever.setDataSource(this, videoUri);
                    Log.d(TAG, "fallback uri 사용");
                }

                // 영상 길이를 가져오고, 값이 없을 경우 기본 길이로 설정
                String durationStr =
                        retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_DURATION
                        );

                long durationUs;

                if (durationStr != null && !durationStr.equals("0")) {
                    durationUs = Long.parseLong(durationStr) * 1000;
                } else {
                    durationUs = 5000000;
                }

                Log.d(TAG, "영상 길이(us): " + durationUs);

                // 가장 높은 점수의 프레임과 히트맵 정보를 저장
                float maxScore = -1f;

                Bitmap bestFrame = null;
                float[][] bestHeatmap = null;

                int bestX1 = 0;
                int bestY1 = 0;
                int bestCropW = 0;
                int bestCropH = 0;

                Bitmap prevFrame = null;

                AiProcessor aiProcessor = new AiProcessor(this);

                // 빠른 얼굴 검출을 위한 ML Kit Face Detector 설정
                FaceDetectorOptions options =
                        new FaceDetectorOptions.Builder()
                                .setPerformanceMode(
                                        FaceDetectorOptions.PERFORMANCE_MODE_FAST
                                )
                                .build();

                com.google.mlkit.vision.face.FaceDetector detector =
                        com.google.mlkit.vision.face.FaceDetection
                                .getClient(options);


                //0.5초 간격으로 프레임을 추출하여 분석
                for (long timeUs = 500000;
                     timeUs < durationUs;
                     timeUs += 500000) {

                    Log.d(TAG, "탐색 위치(us): " + timeUs);

                    Bitmap frame = null;

                    // 현재 시간에 가장 가까운 프레임 추출
                    try {
                        frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);
                    } catch (Exception e) {
                        Log.e(TAG, "OPTION_CLOSEST 실패", e);
                    }

                    // 1차 추출 실패 시 동기 프레임 기준으로 재시도
                    if (frame == null) {
                        try {
                            frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        } catch (Exception e) {
                            Log.e(TAG, "OPTION_CLOSEST_SYNC 실패", e);
                        }
                    }

                    if (frame == null) {
                        Log.w(TAG, "프레임 null -> skip");
                        continue;
                    }

                    // 이전 프레임과 동일할 경우 중복 분석 방지
                    if (prevFrame != null && isSameBitmap(frame, prevFrame)) {
                        Log.w(TAG, "중복 프레임 감지 -> skip");
                        continue;
                    }

                    prevFrame = frame;

                    // 얼굴이 검출되면 얼굴 영역을 기준으로 분석하고, 없으면 전체 프레임을 분석
                    int x1 = 0;
                    int y1 = 0;

                    int cropW = frame.getWidth();
                    int cropH = frame.getHeight();

                    Bitmap inputBitmap = frame;

                    try {
                        com.google.mlkit.vision.common.InputImage
                                visionImage = com.google.mlkit.vision.common
                                        .InputImage
                                        .fromBitmap(frame, 0);

                        List<com.google.mlkit.vision.face.Face> faces = com.google.android.gms.tasks.Tasks.await(detector.process(visionImage));

                        if (faces != null && !faces.isEmpty()) {
                            com.google.mlkit.vision.face.Face face = faces.get(0);
                            android.graphics.Rect bounds = face.getBoundingBox();

                            int w = bounds.width();
                            int h = bounds.height();

                            // 얼굴 패딩
                            int pw = (int) (w * 0.20f);
                            int ph = (int) (h * 0.20f);

                            x1 = Math.max(bounds.left - pw, 0);
                            y1 = Math.max(bounds.top - ph, 0);

                            int x2 = Math.min(bounds.left + w + pw, frame.getWidth());

                            int y2 = Math.min(bounds.top + h + ph, frame.getHeight());

                            cropW = x2 - x1;
                            cropH = y2 - y1;

                            if (cropW > 0 && cropH > 0) {
                                inputBitmap = Bitmap.createBitmap(frame, x1, y1, cropW, cropH);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "얼굴 검출 실패", e);
                    }

                    // 추출된 분석 영역을 TFLite 모델로 추론
                    try {
                        TensorImage tensorImage = aiProcessor.processImage(inputBitmap);

                        Map<String, Object> results = aiProcessor.runInference(tensorImage);

                        float currentScore = (float) results.get("score") * 100f;

                        Log.d(TAG, (timeUs / 1000000.0) + "초 점수: " + currentScore);

                        // 현재 프레임의 점수가 가장 높으면 최종 후보로 저장
                        if (currentScore > maxScore) {
                            maxScore = currentScore;
                            bestFrame = frame;
                            bestHeatmap = (float[][]) results.get("heatmap");

                            bestX1 = x1;
                            bestY1 = y1;
                            bestCropW = cropW;
                            bestCropH = cropH;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "AI 추론 실패", e);
                    }
                }

                // 가장 높은 점수의 프레임을 최종 결과로 처리
                if (bestFrame != null) {
                    finalizeAnalysis(bestFrame, maxScore, bestHeatmap, bestX1, bestY1, bestCropW, bestCropH);
                } else {
                    finishWithError("영상에서 얼굴 프레임을 찾을 수 없습니다.");
                }
            } catch (Exception e) {
                Log.e(TAG, "로컬 영상 분석 오류", e);
                finishWithError("영상 분석 중 오류가 발생했습니다.");
            } finally {
                try {
                    retriever.release();
                } catch (Exception ignored) {}

                // 분석에 사용한 임시 파일 삭제
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }

        }).start();
    }

    // 일부 픽셀을 비교하여 이전 프레임과 동일한 프레임인지 확인
    private boolean isSameBitmap(Bitmap b1, Bitmap b2) {
        if (b1 == null || b2 == null) {
            return false;
        }

        if (b1.getWidth() != b2.getWidth()
                || b1.getHeight() != b2.getHeight()) {
            return false;
        }

        int[] xs = {
                b1.getWidth() / 4,
                b1.getWidth() / 2,
                b1.getWidth() * 3 / 4
        };

        int[] ys = {
                b1.getHeight() / 4,
                b1.getHeight() / 2,
                b1.getHeight() * 3 / 4
        };

        for (int x : xs) {
            for (int y : ys) {
                if (b1.getPixel(x, y) != b2.getPixel(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }


    // URI로 전달된 영상을 분석 가능한 임시 mp4 파일로 복사
    private java.io.File createTempFileFromUri(Uri uri) {
        try {
            java.io.File tempFile = java.io.File.createTempFile("temp_video", ".mp4", getCacheDir());
            java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
            java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempFile);

            byte[] buffer = new byte[4096];
            int len;

            while ((len = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }

            outputStream.close();
            inputStream.close();

            return tempFile;
        } catch (Exception e) {
            Log.e(TAG, "temp file 생성 실패", e);
            return null;
        }
    }

    // 분석 결과를 공통 방식으로 처리하고 Firebase 저장 후 결과 화면 이동을 준비
    private void finalizeAnalysis(Bitmap bitmap, float score, float[][] heatmapMatrix, int x1, int y1, int cropW, int cropH) {
        // 원본 이미지를 결과 화면에서 사용할 수 있도록 임시 보관
        BitmapHolder.originalBitmap = bitmap;

        // 원본 이미지 크기와 분석 영역에 맞춰 히트맵 이미지 생성
        HeatmapProcessor hp = new HeatmapProcessor();
        Bitmap heatmapBitmap = hp.createHeatmapImage(heatmapMatrix, bitmap.getWidth(), bitmap.getHeight(), x1, y1, cropW, cropH);
        BitmapHolder.heatmapBitmap = heatmapBitmap;

        savedResult = new AnalysisResult(score, heatmapBitmap);

        String snsUrl = getIntent().getStringExtra("snsUrl");

        // URL 또는 영상 분석일 경우 원본 주소를 결과 기록에 함께 저장
        if (snsUrl == null && !getIntent().getBooleanExtra("is_local_video", false)) {
            if (getIntent().getBooleanExtra("is_from_url", false)) {
                snsUrl = getIntent().getStringExtra("image_url");
            } else if (getIntent().getBooleanExtra("is_video_mode", false)) {
                snsUrl = getIntent().getStringExtra("video_url");
            }
        }

        // 분석 결과와 이미지를 Firebase에 저장
        new FirebaseManager().uploadAnalysisResult(new AnalysisResult(score, heatmapBitmap), bitmap, snsUrl, record -> {
            savedRecord = record;
            checkDataAndMove();
        });
    }

    // 단일 이미지를 얼굴 영역 기준으로 분석
    private void performAnalysis(Bitmap bitmap) {
        if (bitmap == null) { finishWithError("이미지 데이터가 없습니다."); return; }
        try {
            com.google.mlkit.vision.common.InputImage visionImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0);
            com.google.mlkit.vision.face.FaceDetectorOptions options = new com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
                    .setPerformanceMode(com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build();
            com.google.mlkit.vision.face.FaceDetector detector = com.google.mlkit.vision.face.FaceDetection.getClient(options);

            List<com.google.mlkit.vision.face.Face> faces = com.google.android.gms.tasks.Tasks.await(detector.process(visionImage));

            int x1 = 0, y1 = 0;
            int cropW = bitmap.getWidth();
            int cropH = bitmap.getHeight();
            Bitmap inputNodeBitmap = bitmap;

            // 얼굴이 검출되면 얼굴 주변 영역을 잘라 모델 입력으로 사용
            if (faces != null && !faces.isEmpty()) {
                com.google.mlkit.vision.face.Face targetFace = faces.get(0);
                android.graphics.Rect bounds = targetFace.getBoundingBox();

                int x = bounds.left;
                int y = bounds.top;
                int w = bounds.width();
                int h = bounds.height();

                int pw = (int) (w * 0.20);
                int ph = (int) (h * 0.20);

                x1 = Math.max(x - pw, 0);
                y1 = Math.max(y - ph, 0);
                int x2 = Math.min(x + w + pw, bitmap.getWidth());
                int y2 = Math.min(y + h + ph, bitmap.getHeight());

                cropW = x2 - x1;
                cropH = y2 - y1;

                if (cropW > 0 && cropH > 0) {
                    inputNodeBitmap = Bitmap.createBitmap(bitmap, x1, y1, cropW, cropH);
                }
            }

            // 전처리 후 TFLite 모델 추론 실행
            AiProcessor aiProcessor = new AiProcessor(this);
            TensorImage processedImage = aiProcessor.processImage(inputNodeBitmap);
            Map<String, Object> results = aiProcessor.runInference(processedImage);

            float score = (float) results.get("score") * 100f;
            float[][] heatmapMatrix = (float[][]) results.get("heatmap");

            finalizeAnalysis(bitmap, score, heatmapMatrix, x1, y1, cropW, cropH);
        } catch (Exception e) {
            Log.e(TAG, "분석 오류", e);
            finishWithError("AI 분석 중 오류가 발생했습니다.");
        }
    }

    // 서버에서 바든 영상 분석 결과를 Bitmap과 히트맵 데이터로 변환
    private void processVideoServerResult(VideoAnalysisResponse res) {
        new Thread(() -> {
            try {
                byte[] bytes = Base64.decode(res.getFrameBase64(), Base64.DEFAULT);
                Bitmap frameBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                float prob = res.getProbability() * 100f;

                // 서버에서 받은 히트맵 리스트를 2차원 배열로 변환
                List<List<Float>> heatmapList = res.getHeatmap();
                float[][] heatmapMatrix = new float[heatmapList.size()][heatmapList.get(0).size()];
                for (int i = 0; i < heatmapList.size(); i++) {
                    for (int j = 0; j < heatmapList.get(i).size(); j++) {
                        heatmapMatrix[i][j] = heatmapList.get(i).get(j);
                    }
                }

                finalizeAnalysis(frameBitmap, prob, heatmapMatrix, 0, 0, frameBitmap.getWidth(), frameBitmap.getHeight());
            } catch (Exception e) {
                Log.e(TAG, "영상 결과 처리 오류", e);
                finishWithError("결과 처리 중 오류 발생");
            }
        }).start();
    }

    // URL 안정성 검사 후 문제가 없거나 사용자가 진행을 선택하면 다음 분석 단계 실행
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
                    // 서버가 위험 링크로 판단한 경우 사용자에게 경고 다이얼로그 표시
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
                // URL 검사 실패 시에도 분석 흐름은 계속 진행
                onSafe.run();
            }
        });
    }

    // 영상 URL을 서버에 전달하여 분석 요청
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

    // 이미지 URL을 Bitmap으로 로드한 뒤 로컬 이미지 분석 실행
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

    // 로딩 진행률 애니메이션 시작
    private void startLoadingAnimation() {
        ValueAnimator animator = ValueAnimator.ofInt(0, 100);
        // 분석 대기 시간을 고려해 로딩 애니메이션 시간 설정
        animator.setDuration(7000);

        animator.addUpdateListener(animation -> updateLoadingUI((int) animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                isAnimationFinished = true;
                checkDataAndMove();
            }
        });
        animator.start();
    }

    // 진행률에 따라 현재 분석 단게 문구와 색상 변경
    private void updateLoadingUI(int progress) {
        if (progress < 20) {
            setStepColors("#FF5E62", "#333333", "#333333");
            tvStepText.setText("데이터 읽는 중...");
        }
        else if (progress < 90) {
            setStepColors("#333333", "#FFEA00", "#333333");
            tvStepText.setText("AI 분석 중...");
        }
        else {
            setStepColors("#333333", "#333333", "#00FF7F");
            tvStepText.setText("결과 준비 중...");
        }
    }

    // 단계 표시 View의 색상 변경
    private void setStepColors(String c1, String c2, String c3) {
        step1.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(c1)));
        step2.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(c2)));
        step3.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(c3)));
    }

    // 로딩 애니메이션과 분석 결과 저장이 모두 끝나면 결과 화면으로 이동
    private void checkDataAndMove() {
        if (isAnimationFinished && savedRecord != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                Intent nextIntent = new Intent(LoadingActivity.this, ResultActivity.class);
                nextIntent.putExtra("record", savedRecord);
                nextIntent.putExtra("analysis_result", savedResult);

                // 결과 화면에 표시할 원본 URL 정보 전달
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

    // 오류 메시지를 표시하고 로딩 화면 종료
    private void finishWithError(String msg) {
        runOnUiThread(() -> { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); finish(); });
    }
}