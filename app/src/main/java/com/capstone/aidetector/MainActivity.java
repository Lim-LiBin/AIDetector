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

public class MainActivity extends AppCompatActivity {

    private PreviewView viewFinder;
    private ImageView galleryImageView;
    private Button btnCapture;
    private ImageButton btnSelect; // 왼쪽 갤러리 버튼
    private ImageCapture imageCapture;

    private static final String TAG = "AiDetector_Main";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    private ActivityResultLauncher<String> galleryLauncher;
    private Bitmap currentBitmap = null;
    private AiProcessor aiProcessor;

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

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) processGalleryImage(uri); }
        );

        // 왼쪽 카메라 아이콘: 갤러리 실행
        btnSelect.setOnClickListener(v -> galleryLauncher.launch("image/*"));

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
                // [다중 출력 추론 구현] 확률값과 히트맵 행렬 동시 추출
                Map<String, Object> results = aiProcessor.runInference(processedImage);

                if (results != null) {
                    float score = (float) results.get("score");
                    float[][] heatmapMatrix = (float[][]) results.get("heatmap");

                    // [ ] HeatmapProcessor의 createHeatmapImage 메서드를 호출하여 7x7 행렬 전달 및 비트맵 수신
                    // → Bitmap heatmapBitmap = heatmapProcessor.createHeatmapImage(heatmapMatrix)
                    HeatmapProcessor heatmapProcessor = new HeatmapProcessor();
                    Bitmap heatmapBitmap = heatmapProcessor.createHeatmapImage(heatmapMatrix);

                    // [ ] AnalysisResult 객체 생성 및 데이터 담기 (확률값과 리턴받은 비트맵 이미지)
                    // → AnalysisResult result = new AnalysisResult(probability, heatmap)
                    AnalysisResult result = new AnalysisResult(score, heatmapBitmap);

                    // [ ] FirebaseManager의 uploadAnalysisResult 메서드를 호출하여 최종 객체 전달
                    // → firebaseManager.uploadAnalysisResult(result)
                    FirebaseManager firebaseManager = new FirebaseManager();
                    firebaseManager.uploadAnalysisResult(result);

                    // [ ] 로그: [추론 및 시각화 완료] 확률: (값), 히트맵 이미지 생성 및 DB&결과 화면 전달 성공
                    Log.i(TAG, "[추론 및 시각화 완료] 확률: " + score + ", 히트맵 이미지 생성 및 DB&결과 화면 전달 성공");

                    // [ ] ResultActivity.java로 Intent를 생성하여 AnalysisResult 객체 전달
                    Intent intent = new Intent(MainActivity.this, ResultActivity.class);
                    intent.putExtra("analysis_result", result);
                    startActivity(intent);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "분석 및 전달 실패: " + e.getMessage());
            Toast.makeText(this, "오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void processGalleryImage(Uri uri) {
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
}