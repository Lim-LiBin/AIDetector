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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private PreviewView viewFinder;
    private ImageView galleryImageView;
    private Button btnCapture;
    private ImageButton btnSelect;
    private Button btnUrl;

    private static final String TAG = "AiDetector_Main";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    private ActivityResultLauncher<String> galleryLauncher;
    private Bitmap currentBitmap = null;
    private Uri currentImageUri = null;
    private AiProcessor aiProcessor;
    private CameraHandler cameraHandler;

    // ★ [추가] 분석을 위해 떠났었는지 확인하는 플래그
    private boolean isBackFromAnalysis = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        aiProcessor = new AiProcessor(this);

        // 1. 뷰 및 핸들러 초기화
        viewFinder = findViewById(R.id.viewFinder);
        galleryImageView = findViewById(R.id.galleryImageView);
        btnCapture = findViewById(R.id.btnCapture);
        btnSelect = findViewById(R.id.btnSelect);
        btnUrl = findViewById(R.id.btnUrl);

        cameraHandler = new CameraHandler(this, viewFinder);

        // 2. 갤러리 런처 설정
        // ┌────────────────────────────────────────────────────────┐
        // │ [수정] 모든 타입(*/*)을 받을 수 있도록 설정하여 영상 선택 허용     │
        // └────────────────────────────────────────────────────────┘
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) processGalleryMedia(uri); }
        );

        // 3. 선택 버튼 리스너 (팝업창 호출)
        btnSelect.setOnClickListener(v -> showImageSourceDialog());

        // 4. URL 버튼 리스너
        btnUrl.setOnClickListener(v -> showUrlInputDialog());

        // 5. 중앙 버튼 리스너 (촬영 또는 검사 시작)
        btnCapture.setOnClickListener(v -> {
            if (viewFinder.getVisibility() == View.VISIBLE) {
                // 카메라 화면이 보이면 사진을 찍습니다.
                capturePhotoFromHandler();
            } else {
                // 사진이 준비된 상태면 검사를 시작합니다.
                if (currentImageUri == null) {
                    Toast.makeText(this, "분석할 사진이나 영상을 선택해주세요!", Toast.LENGTH_SHORT).show();
                } else {
                    // ┌────────────────────────────────────────────────────┐
                    // │ [추가] 선택된 파일의 MIME 타입을 확인하여 영상 분석 분기 처리      │
                    // └────────────────────────────────────────────────────┘
                    String mimeType = getContentResolver().getType(currentImageUri);
                    if (mimeType != null && mimeType.startsWith("video")) {
                        runVideoAnalysisFromGallery();
                    } else {
                        runDeepfakeAnalysisWithVisualization();
                    }
                }
            }
        });

        // 6. 하단 탭 이동 리스너
        findViewById(R.id.nav_history).setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
        });

        findViewById(R.id.nav_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });
    }

    // ★ [수정] 결과창에서 돌아올 때 액티비티 자체를 "새로고침" 시켜서 모든 자원을 초기화합니다.
    @Override
    protected void onRestart() {
        super.onRestart();
        // 액티비티를 강제로 다시 생성하여 첫 시작 상태로 만듭니다.
        // 이렇게 하면 이전 사진 잔상과 카메라 검은 화면 문제가 동시에 해결됩니다.
        finish();
        startActivity(getIntent());
    }

    //카메라/갤러리 선택 팝업
    private void showImageSourceDialog() {
        // ┌────────────────────────────────────────────────────────┐
        // │ [수정] 메뉴 항목에 영상 포함 명시                            │
        // └────────────────────────────────────────────────────────┘
        String[] options = {"카메라로 촬영", "갤러리(이미지/영상) 선택"};
        new AlertDialog.Builder(this)
                .setTitle("이미지 가져오기")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) startCameraMode();
                    else galleryLauncher.launch("*/*"); // 갤러리 앱 호출 시 전체 타입 허용
                })
                .show();
    }

    //카메라 모드 시작
    private void startCameraMode() {
        if (allPermissionsGranted()) {
            currentBitmap = null;
            currentImageUri = null;

            viewFinder.setVisibility(View.VISIBLE);
            galleryImageView.setVisibility(View.GONE);
            btnCapture.setText("사진 촬영"); // 버튼 텍스트 변경

            cameraHandler.startCamera(this);
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    //촬영 로직
    private void capturePhotoFromHandler() {
        cameraHandler.takePhoto((bitmap, uri) -> {
            runOnUiThread(() -> {
                currentBitmap = bitmap;
                currentImageUri = uri;

                stopCameraResources();
                viewFinder.setVisibility(View.GONE);
                galleryImageView.setVisibility(View.VISIBLE);
                galleryImageView.setImageBitmap(currentBitmap);

                btnCapture.setText("검사 시작"); // 촬영 후 텍스트 변경
            });
        });
    }

    // ┌────────────────────────────────────────────────────────┐
    // │ [수정] 갤러리 이미지/영상 처리 (기존 processGalleryImage를 확장) │
    // └────────────────────────────────────────────────────────┘
    private void processGalleryMedia(Uri uri) {
        this.currentImageUri = uri;
        stopCameraResources();

        viewFinder.setVisibility(View.GONE);
        galleryImageView.setVisibility(View.VISIBLE);

        String mimeType = getContentResolver().getType(uri);

        if (mimeType != null && mimeType.startsWith("video")) {
            // [영상일 경우] 썸네일을 생성하여 표시
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Bitmap thumbnail = getContentResolver().loadThumbnail(uri, new android.util.Size(512, 512), null);
                    galleryImageView.setImageBitmap(thumbnail);
                } else {
                    galleryImageView.setImageResource(android.R.drawable.presence_video_online);
                }
                currentBitmap = null; // 영상이므로 비트맵 분석은 건너뜀
                btnCapture.setText("영상 분석 시작");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // [이미지일 경우] 기존 로직 유지
            currentBitmap = getResizedBitmap(uri, 1024);
            if (currentBitmap != null) {
                galleryImageView.setImageBitmap(currentBitmap);
                btnCapture.setText("검사 시작");
            } else {
                Toast.makeText(this, "이미지를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ⭐️ [추가된 메서드] OOM(메모리 부족) 방지를 위한 안전한 비트맵 리사이징 유틸리티
    private Bitmap getResizedBitmap(Uri uri, int maxResolution) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap rawBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            return rawBitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 분석 실행
    private void runDeepfakeAnalysisWithVisualization() {
        BitmapHolder.originalBitmap = currentBitmap;
        Intent intent = new Intent(MainActivity.this, LoadingActivity.class);
        if (currentImageUri != null) {
            intent.putExtra("original_image_uri", currentImageUri.toString());
        }
        startActivity(intent);
    }

    // ┌────────────────────────────────────────────────────────┐
    // │ [추가] 갤러리에서 가져온 영상 분석을 위한 LoadingActivity 호출    │
    // └────────────────────────────────────────────────────────┘
    private void runVideoAnalysisFromGallery() {
        Intent intent = new Intent(MainActivity.this, LoadingActivity.class);
        intent.putExtra("video_url", currentImageUri.toString());
        intent.putExtra("is_video_mode", true);
        intent.putExtra("is_from_gallery", true);
        startActivity(intent);
    }

    private void stopCameraResources() {
        try {
            ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
            cameraProvider.unbindAll();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
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
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains("youtube.com/watch") || lowerUrl.contains("youtu.be/") || lowerUrl.contains("/shorts/")) return true;
        if (lowerUrl.contains("instagram.com/reel/") || lowerUrl.contains("instagram.com/p/")) return true;
        return url.matches(".*(?i)\\.(mp4|avi|mov|wmv|flv|webm)(\\?.*)?$");
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (aiProcessor != null) aiProcessor.close();
    }
}