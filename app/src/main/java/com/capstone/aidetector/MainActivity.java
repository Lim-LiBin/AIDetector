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
        TextView navHistory = findViewById(R.id.nav_history);

        navHistory.setOnClickListener(v -> {
            // HistoryActivity로 이동하는 의도(Intent) 생성
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);

            // 화면 전환 애니메이션을 넣고 싶다면 여기에 추가 (선택사항)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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
}