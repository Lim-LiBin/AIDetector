package com.capstone.aidetector;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.capstone.aidetector.model.HistoryRecord;
import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.support.image.TensorImage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import java.io.ByteArrayOutputStream;
// Retrofit
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Glide
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import android.graphics.drawable.Drawable;

// 다이얼로그
import androidx.appcompat.app.AlertDialog;
import android.widget.EditText;

// Base64
import android.util.Base64;

// List
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;

public class MainActivity extends AppCompatActivity {

    private PreviewView viewFinder;
    private ImageView galleryImageView;
    private Button btnCapture;
    private ImageButton btnSelect; // 왼쪽 갤러리 버튼
    private ImageCapture imageCapture;

    private Button btnUrl; //URL 버튼

    private static final String TAG = "AiDetector_Main";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    private ActivityResultLauncher<String> galleryLauncher;
    private Bitmap currentBitmap = null;
    private AiProcessor aiProcessor;
    private Uri currentImageUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // [TFLite 모델 로드 및 최적화 완료]
        aiProcessor = new AiProcessor(this);

        viewFinder = findViewById(R.id.viewFinder);
        galleryImageView = findViewById(R.id.galleryImageView);
        btnCapture = findViewById(R.id.btnCapture);
        btnSelect = findViewById(R.id.btnSelect);
        btnUrl = findViewById(R.id.btnUrl);

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) processGalleryImage(uri); }
        );

        // 왼쪽 카메라 아이콘: 갤러리 실행
        btnSelect.setOnClickListener(v -> galleryLauncher.launch("image/*"));

        btnUrl.setOnClickListener(v -> showUrlInputDialog());

        // 중앙 큰 버튼: 촬영 시작 또는 검사 수행
        btnCapture.setOnClickListener(v -> {
            String mode = btnCapture.getText().toString();
            if (mode.contains("사진")) {
                if (viewFinder.getVisibility() != View.VISIBLE) {
                    startCameraMode();
                } else {
                    takePhoto();
                }
            } else {
                if (currentBitmap == null) {
                    Toast.makeText(this, "분석할 사진을 선택해주세요!", Toast.LENGTH_SHORT).show();
                } else {
                    // [시각화 & DB 연동 로직 실행]
                    runDeepfakeAnalysisWithVisualization();
                }
            }
        });

        // 하단 '이력' 탭 클릭 시 HistoryActivity로 이동
        View tabHistory = findViewById(R.id.nav_history);
        tabHistory.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });
    }

    /**
     * [시각화 & DB 연동]
     * 추론 결과를 바탕으로 히트맵 이미지를 생성하고 DB 업로드 및 화면 전환을 수행합니다.
     */
    private void runDeepfakeAnalysisWithVisualization() {
        Toast.makeText(this, "분석 및 시각화 중...", Toast.LENGTH_SHORT).show();
        try {
            TensorImage processedImage = aiProcessor.processImage(currentBitmap);
            if (processedImage != null) {
                Map<String, Object> results = aiProcessor.runInference(processedImage);

                if (results != null) {
                    float score = (float) results.get("score");
                    float[][] heatmapMatrix = (float[][]) results.get("heatmap");

                    // 1. 진짜 히트맵 비트맵 생성 (이 이미지는 서버에 업로드해야 함!)
                    HeatmapProcessor heatmapProcessor = new HeatmapProcessor();
                    Bitmap heatmapBitmap = heatmapProcessor.createHeatmapImage(heatmapMatrix);

                    // [v] 보관소에 저장 (ResultActivity가 꺼내 쓸 용도)
                    BitmapHolder.heatmapBitmap = heatmapBitmap;

                    float fakeProbability = score * 100f;

                    // 2. [핵심] 목적에 따라 객체를 두 개로 나눕니다.
                    // (1) uploadResult: 서버 전송용 (진짜 비트맵 포함)
                    AnalysisResult uploadResult = new AnalysisResult(fakeProbability, heatmapBitmap);

                    // (2) intentResult: 화면 전환용 (용량 줄이기 위해 비트맵은 null)
                    AnalysisResult intentResult = new AnalysisResult(fakeProbability, null);
                  
                    // [추가] 갤러리/카메라에서 선택된 이미지가 있다면 URI 전달
                    // 팁: 전역 변수로 Uri를 관리하거나, 테스트를 위해 임시로 넘겨야 합니다.
                    // 만약 URI가 없다면 ResultActivity에서 별도 처리가 필요합니다.
                    if (currentImageUri != null) {
                        intent.putExtra("original_image_uri", currentImageUri.toString());
                    }else {
                        // currentBitmap을 byte array로 변환해서 전달
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        currentBitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream);
                        byte[] byteArray = stream.toByteArray();
                        intent.putExtra("original_image_bytes", byteArray);
                    }

                    // 3. Firebase 업로드 시작 (진짜 데이터가 든 uploadResult를 줍니다!)
                    FirebaseManager firebaseManager = new FirebaseManager();
                    firebaseManager.uploadAnalysisResult(uploadResult, currentBitmap, new FirebaseManager.OnUploadCompleteListener() {
                        @Override
                        public void onComplete(HistoryRecord record) {
                            // [v] 서버 저장이 완전히 끝나서 ID가 담긴 record가 도착하면 실행!
                            Log.i(TAG, "[분석 및 저장 완료] 가짜 확률: " + fakeProbability + "%");

                            // 4. ResultActivity로 이동
                            Intent intent = new Intent(MainActivity.this, ResultActivity.class);

                            // 서버에서 받아온 record를 넘깁니다. (이게 있어야 바로 삭제 가능!)
                            intent.putExtra("record", record);
                            intent.putExtra("analysis_result", intentResult); // 가벼운 객체 전달

                            if (currentImageUri != null) {
                                intent.putExtra("original_image_uri", currentImageUri.toString());
                            }

                            // 이제 모든 준비가 끝났으니 화면 전환!
                            startActivity(intent);
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "분석 실패: " + e.getMessage());
            Toast.makeText(this, "분석 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void processGalleryImage(Uri uri) {
        this.currentImageUri = uri;
        stopCamera();
        viewFinder.setVisibility(View.GONE);
        galleryImageView.setVisibility(View.VISIBLE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                currentBitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(this.getContentResolver(), uri), (decoder, info, src) -> decoder.setMutableRequired(true));
            } else {
                currentBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
            }
            galleryImageView.setImageBitmap(currentBitmap);
            btnCapture.setText("검사 시작");
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void startCameraMode() {
        viewFinder.setVisibility(View.VISIBLE);
        galleryImageView.setVisibility(View.GONE);
        if (allPermissionsGranted()) startCamera();
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
    }

    private void takePhoto() {
        if (imageCapture == null) return;
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                currentBitmap = imageProxyToBitmap(image);
                runOnUiThread(() -> {
                    stopCamera();
                    viewFinder.setVisibility(View.GONE);
                    galleryImageView.setVisibility(View.VISIBLE);
                    galleryImageView.setImageBitmap(currentBitmap);
                    btnCapture.setText("검사 시작");
                });
                image.close();
            }
            @Override
            public void onError(@NonNull ImageCaptureException e) { Log.e(TAG, "촬영 실패", e); }
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (Exception e) { e.printStackTrace(); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void stopCamera() {
        try {
            ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
            cameraProvider.unbindAll();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (aiProcessor != null) aiProcessor.close();
    }
    //URL 입력 다이얼로그 표시
    private void showUrlInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("URL 입력");

        final EditText input = new EditText(this);
        input.setHint("URL을 입력해주세요");
        builder.setView(input);

        builder.setPositiveButton("확인", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "URL을 입력해주세요", Toast.LENGTH_SHORT).show();
            } else {
                processUrl(url);
            }
        });

        builder.setNegativeButton("취소", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    //타입 판별 및 처리
    private void processUrl(String url) {
        if (isImageUrl(url)) {
            // 이미지 URL
            processImageUrl(url);
        } else if (isVideoUrl(url)) {
            // 영상 URL (유튜브 포함)
            processVideoUrl(url);
        } else {
            Toast.makeText(this, "지원하지 않는 URL 형식입니다", Toast.LENGTH_SHORT).show();
        }
    }

    //이미지 URL 판별
    private boolean isImageUrl(String url) {
        // 1. 확장자로 판별
        String extPattern = "(?i)\\.(jpg|jpeg|png|gif|bmp|webp)(\\?.*)?$";
        if (url.matches(".*" + extPattern)) {
            return true;
        }

        // 2. URL에 이미지 관련 키워드 포함 시 이미지로 판별
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains("/image") ||
                lowerUrl.contains("/img") ||
                lowerUrl.contains("image.") ||
                lowerUrl.contains("img.") ||
                lowerUrl.contains("photo")) {
            return true;
        }

        return false;
    }

    //영상 URL 판별 (유튜브 + 일반 영상)
    private boolean isVideoUrl(String url) {
        // 유튜브 URL
        if (url.contains("youtube.com/watch") || url.contains("youtu.be/")) {
            return true;
        }

        // 일반 영상 확장자
        String pattern = "(?i)\\.(mp4|avi|mov|wmv|flv|webm)(\\?.*)?$";
        return url.matches(".*" + pattern);
    }
    //이미지 URL 처리 (Glide 사용)
    private void processImageUrl(String url) {
        Toast.makeText(this, "이미지 로딩 중...", Toast.LENGTH_SHORT).show();

        Glide.with(this)
                .asBitmap()
                .load(url)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                        // 이미지 로드 성공
                        currentBitmap = bitmap;
                        currentImageUri = null;  // URL에서 가져온 것이므로 URI는 null

                        // 화면 업데이트
                        stopCamera();
                        viewFinder.setVisibility(View.GONE);
                        galleryImageView.setVisibility(View.VISIBLE);
                        galleryImageView.setImageBitmap(bitmap);

                        Toast.makeText(MainActivity.this, "이미지 로드 완료, 분석 시작", Toast.LENGTH_SHORT).show();

                        // ✅ 체크리스트: 로드된 비트맵으로 바로 분석 실행!
                        runDeepfakeAnalysisWithVisualization();
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        // ✅ 체크리스트: 접근 권한 없거나 깨진 링크일 경우 토스트 메시지
                        Toast.makeText(MainActivity.this,
                                "이미지를 불러올 수 없습니다. URL을 확인해주세요.",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        // 필수 오버라이드 (비워둠)
                    }
                });
    }
    //영상 URL 처리 (서버로 전송)
    private void processVideoUrl(String url) {
        Toast.makeText(this, "영상 분석 중... 시간이 걸릴 수 있습니다", Toast.LENGTH_LONG).show();

        // Retrofit 인스턴스 생성
        Retrofit retrofit = getRetrofitInstance();
        PostService service = retrofit.create(PostService.class);

        // 요청 생성
        VideoAnalysisRequest request = new VideoAnalysisRequest(url);

        // 서버 호출
        service.analyzeVideo(request).enqueue(new Callback<VideoAnalysisResponse>() {
            @Override
            public void onResponse(Call<VideoAnalysisResponse> call, Response<VideoAnalysisResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    handleVideoAnalysisResult(response.body());
                } else {
                    Toast.makeText(MainActivity.this,
                            "서버 오류가 발생했습니다",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<VideoAnalysisResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this,
                        "네트워크 오류: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
                Log.e(TAG, "서버 통신 실패", t);
            }
        });
    }
    //Retrofit 인스턴스 생성
    private Retrofit getRetrofitInstance() {
        // 로깅 인터셉터 (디버깅용)
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        // OkHttp 클라이언트 (타임아웃 설정)
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        return new Retrofit.Builder()
                .baseUrl(ServerConfig.getBaseUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    //서버에서 받은 영상 분석 결과 처리
    private void handleVideoAnalysisResult(VideoAnalysisResponse response) {
        try {
            // 1. Base64 이미지를 Bitmap으로 변환
            byte[] decodedBytes = Base64.decode(response.getFrameBase64(), Base64.DEFAULT);
            Bitmap frameBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

            // 2. 히트맵 행렬을 float[][]로 변환
            List<List<Float>> heatmapList = response.getHeatmap();
            int rows = heatmapList.size();
            int cols = heatmapList.get(0).size();
            float[][] heatmapMatrix = new float[rows][cols];

            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    heatmapMatrix[i][j] = heatmapList.get(i).get(j);
                }
            }

            // 3. HeatmapProcessor로 히트맵 비트맵 생성
            HeatmapProcessor heatmapProcessor = new HeatmapProcessor();
            Bitmap heatmapBitmap = heatmapProcessor.createHeatmapImage(heatmapMatrix);

            // 4. 확률값 변환 (0.0~1.0 → 0~100%)
            float fakeProbability = response.getProbability() * 100f;

            // 5. AnalysisResult 객체 생성
            AnalysisResult result = new AnalysisResult(fakeProbability, heatmapBitmap);

            // 6. Firebase에 저장
            FirebaseManager firebaseManager = new FirebaseManager();
            firebaseManager.uploadAnalysisResult(result, frameBitmap);

            // 7. ResultActivity로 이동
            Intent intent = new Intent(MainActivity.this, ResultActivity.class);
            intent.putExtra("analysis_result", result);
            // ✅ 영상의 대표 프레임을 byte array로 전달
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            frameBitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream);
            byte[] byteArray = stream.toByteArray();
            intent.putExtra("original_image_bytes", byteArray);

            startActivity(intent);

            Toast.makeText(this, "분석 완료!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "결과 처리 중 오류 발생: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "결과 처리 오류", e);
        }
    }
}