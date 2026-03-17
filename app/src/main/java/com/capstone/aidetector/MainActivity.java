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
        // 1. 즉시 LoadingActivity로 이동
        Intent intent = new Intent(MainActivity.this, LoadingActivity.class);

        // 비트맵 전달 (byte array로 변환)
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        currentBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
        byte[] byteArray = stream.toByteArray();
        intent.putExtra("original_image_bytes", byteArray);

        if (currentImageUri != null) {
            intent.putExtra("original_image_uri", currentImageUri.toString());
        }

        startActivity(intent);
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

        Intent intent = new Intent(MainActivity.this, LoadingActivity.class);
        intent.putExtra("image_url", url); // URL 문자열 전달 (매우 가벼움)
        intent.putExtra("is_from_url", true); // URL 모드임을 표시

        startActivity(intent);
    }
    //영상 URL 처리 (서버로 전송)
    private void processVideoUrl(String url) {
        Intent intent = new Intent(MainActivity.this, LoadingActivity.class);
        intent.putExtra("video_url", url);
        intent.putExtra("is_video_mode", true); // 영상 분석 모드임을 표시
        startActivity(intent);
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
    // 서버에서 받은 영상 분석 결과 처리
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

            // 3. HeatmapProcessor로 히트맵 비트맵 생성 (ResultActivity 공유용)
            HeatmapProcessor heatmapProcessor = new HeatmapProcessor();
            Bitmap heatmapBitmap = heatmapProcessor.createHeatmapImage(heatmapMatrix);
            BitmapHolder.heatmapBitmap = heatmapBitmap;

            // 4. 확률값 변환 (0.0~1.0 → 0~100%)
            float fakeProbability = response.getProbability() * 100f;

            // 5. AnalysisResult 객체 생성 (화면 전달용은 비트맵 제외하여 가볍게)
            AnalysisResult uploadResult = new AnalysisResult(fakeProbability, heatmapBitmap);
            AnalysisResult intentResult = new AnalysisResult(fakeProbability, null);

            // 6. Firebase에 저장 및 완료 후 이동
            FirebaseManager firebaseManager = new FirebaseManager();

            // ✅ 콜백 인터페이스를 사용하여 업로드 완료 후 실행
            firebaseManager.uploadAnalysisResult(uploadResult, frameBitmap, new FirebaseManager.OnUploadCompleteListener() {
                @Override
                public void onComplete(HistoryRecord record) {
                    // 7. ResultActivity로 이동
                    Intent intent = new Intent(MainActivity.this, LoadingActivity.class);

                    // 생성된 DB 레코드 정보 전달
                    intent.putExtra("record", record);
                    intent.putExtra("analysis_result", intentResult);

                    intent.putExtra("is_already_analyzed", true);

                    // 영상의 대표 프레임을 byte array로 전달
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    frameBitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream);
                    byte[] byteArray = stream.toByteArray();
                    intent.putExtra("original_image_bytes", byteArray);

                    startActivity(intent);
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "결과 처리 중 오류 발생: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "결과 처리 오류", e);
        }
    }
}