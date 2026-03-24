package com.capstone.aidetector;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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

import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    private PreviewView viewFinder;
    private ImageView galleryImageView;
    private Button btnCapture;
    private ImageButton btnSelect;
    private ImageCapture imageCapture;
    private Button btnUrl;

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

        btnSelect.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        btnUrl.setOnClickListener(v -> showUrlInputDialog());

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
                    runDeepfakeAnalysisWithVisualization();
                }
            }
        });

        findViewById(R.id.nav_history).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });
    }

    private void runDeepfakeAnalysisWithVisualization() {
        // ⭐️ [해결] Intent 용량 제한 회피: 대용량 비트맵을 인텐트에 넣지 않고 Holder에 저장
        BitmapHolder.originalBitmap = currentBitmap;

        Intent intent = new Intent(MainActivity.this, LoadingActivity.class);
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

        // ⭐️ [수정 핵심] 원본 해상도 그대로 가져오지 않고 안전하게 리사이징하여 가져옵니다.
        currentBitmap = getResizedBitmap(uri, 1024);

        if (currentBitmap != null) {
            galleryImageView.setImageBitmap(currentBitmap);
            btnCapture.setText("검사 시작");
        } else {
            Toast.makeText(this, "이미지를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    // ⭐️ [추가된 메서드] OOM(메모리 부족) 방지를 위한 안전한 비트맵 리사이징 유틸리티
    private Bitmap getResizedBitmap(Uri uri, int maxResolution) {
        try {
            // 1. 메모리 할당 없이 이미지의 크기만 먼저 읽어옵니다.
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            InputStream inputStream = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            // 2. 얼마나 줄일지 비율(inSampleSize)을 계산합니다.
            int width = options.outWidth;
            int height = options.outHeight;
            int inSampleSize = 1;

            if (width > maxResolution || height > maxResolution) {
                final int halfHeight = height / 2;
                final int halfWidth = width / 2;
                while ((halfHeight / inSampleSize) >= maxResolution && (halfWidth / inSampleSize) >= maxResolution) {
                    inSampleSize *= 2;
                }
            }

            // 3. 계산된 비율로 진짜 비트맵을 메모리에 올립니다.
            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;
            inputStream = getContentResolver().openInputStream(uri);
            Bitmap resizedBitmap = BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            return resizedBitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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

    private void showUrlInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("URL 입력");
        final EditText input = new EditText(this);
        input.setHint("URL을 입력해주세요");
        builder.setView(input);
        builder.setPositiveButton("확인", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (url.isEmpty()) Toast.makeText(this, "URL을 입력해주세요", Toast.LENGTH_SHORT).show();
            else processUrl(url);
        });
        builder.setNegativeButton("취소", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void processUrl(String url) {
        if (isImageUrl(url)) processImageUrl(url);
        else if (isVideoUrl(url)) processVideoUrl(url);
        else Toast.makeText(this, "지원하지 않는 URL 형식입니다", Toast.LENGTH_SHORT).show();
    }

    private boolean isImageUrl(String url) {
        String extPattern = "(?i)\\.(jpg|jpeg|png|gif|bmp|webp)(\\?.*)?$";
        return url.matches(".*" + extPattern) || url.toLowerCase().contains("/image") || url.toLowerCase().contains("/img");
    }

    private boolean isVideoUrl(String url) {
        return url.contains("youtube.com/watch") || url.contains("youtu.be/") || url.matches(".*(?i)\\.(mp4|avi|mov|wmv|flv|webm)(\\?.*)?$");
    }

    private void processImageUrl(String url) {
        Intent intent = new Intent(MainActivity.this, LoadingActivity.class);
        intent.putExtra("image_url", url);
        intent.putExtra("is_from_url", true);
        startActivity(intent);
    }

    private void processVideoUrl(String url) {
        Intent intent = new Intent(MainActivity.this, LoadingActivity.class);
        intent.putExtra("video_url", url);
        intent.putExtra("is_video_mode", true);
        startActivity(intent);
    }
}
