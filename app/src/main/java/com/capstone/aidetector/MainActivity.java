package com.capstone.aidetector;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.support.image.TensorImage;

public class MainActivity extends AppCompatActivity {
    private PreviewView viewFinder;
    private ImageView galleryImageView;
    private ProgressBar loadingIndicator;
    private Button btnCapture;
    private ImageButton btnSelect;
    private Button btnUrl;
    private CameraHandler cameraHandler;
    private AiProcessor aiProcessor;

    private static final String TAG = "AIDetector_Main";
    private TextView nav_history, nav_settings;

    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    // [수정] 메서드 이름을 아래 정의된 processGalleryImage와 일치시킴
    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) processGalleryImage(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. 객체 및 뷰 초기화
        aiProcessor = new AiProcessor();
        viewFinder = findViewById(R.id.viewFinder);
        galleryImageView = findViewById(R.id.galleryImageView);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        btnCapture = findViewById(R.id.btnCapture);
        btnSelect = findViewById(R.id.btnSelect);
        btnUrl = findViewById(R.id.btnUrl);
        nav_history = findViewById(R.id.nav_history);
        nav_settings = findViewById(R.id.nav_settings);

        cameraHandler = new CameraHandler(this, viewFinder);

        // 2. 리스너 설정
        btnSelect.setOnClickListener(v -> {
            applyClickAnimation(v);
            showSelectionDialog();
        });

        btnCapture.setOnClickListener(v -> {
            applyClickAnimation(v);
            String mode = btnCapture.getText().toString();
            if (mode.contains("사진")) takePhoto();
            else startAnalysis();
        });

        btnUrl.setOnClickListener(v -> {
            applyClickAnimation(v);
            showUrlInputDialog();
        });

        nav_history.setOnClickListener(v -> navigateTo(HistoryActivity.class));
        nav_settings.setOnClickListener(v -> navigateTo(SettingsActivity.class));
    }

    // --- 헬퍼 메서드: UI 업데이트 및 데이터 저장 통합 ---
    private void updateUIWithMedia(Bitmap bitmap, Uri uri) {
        MediaHandler.setMedia(bitmap, uri); // 관리자 저장

        galleryImageView.setImageBitmap(bitmap);
        viewFinder.setVisibility(View.GONE);
        galleryImageView.setVisibility(View.VISIBLE);
        btnCapture.setText("검사\n시작");
        Log.d(TAG, "미디어 로드 및 UI 업데이트 완료");
    }

    // --- 주요 기능 메서드 ---

    private void processGalleryImage(Uri uri) {
        if (!MediaHandler.isSizeValid(this, uri)) {
            Toast.makeText(this, "20MB 이하만 가능합니다.", Toast.LENGTH_LONG).show();
            return;
        }
        Bitmap bitmap = MediaHandler.processBitmap(this, uri);
        if (bitmap != null) {
            updateUIWithMedia(bitmap, uri);
        }
    }

    private void takePhoto() {
        cameraHandler.takePhoto((bitmap, uri) -> {
            runOnUiThread(() -> {
                if (bitmap != null) updateUIWithMedia(bitmap, uri);
            });
        });
    }

    private void startAnalysis() {
        // 1. 갤러리나 카메라에서 고른 원본 사진 주소 가져오기
        Uri originalUri = MediaHandler.getUri();
        if (originalUri == null) {
            Toast.makeText(this, "분석할 사진을 먼저 골라주세요!", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "UI 테스트 화면으로 강제 이동합니다!", Toast.LENGTH_SHORT).show();

        try {
            // [팀원 코드가 완성되기 전까지 쓸 가짜 데이터]

            // 2. 가짜 히트맵 비트맵 만들기 (대충 반투명한 빨간색 사각형으로 덮음)
            Bitmap dummyHeatmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888);
            dummyHeatmap.eraseColor(android.graphics.Color.argb(128, 255, 0, 0));

            // 3. 가짜 확률 설정 (88.5%로 설정해서 Fake 빨간색 UI가 잘 뜨는지 확인)
            float fakeProbability = 24.5f;

            // 4. AnalysisResult 객체에 가짜 데이터 담기
            AnalysisResult result = new AnalysisResult(fakeProbability, dummyHeatmap);

            // 5. Intent 만들어서 로딩 화면으로 먼저 쏘기!
            // (여기서 ResultActivity.class 였던 걸 LoadingActivity.class로 변경)
            Intent intent = new Intent(MainActivity.this, LoadingActivity.class);
            intent.putExtra("analysis_result", result);
            intent.putExtra("original_image_uri", originalUri.toString());

            // 6. 결과 화면 띄우기
            startActivity(intent);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "테스트 중 에러: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // --- 공통 UI/기타 메서드 ---
    private void showSelectionDialog() {
        String[] options = {"📷 카메라 촬영", "🖼️ 갤러리 불러오기"};
        new AlertDialog.Builder(this)
                .setTitle("데이터 가져오기")
                .setItems(options, (dialog, which) -> {
                    loadingIndicator.setVisibility(View.GONE);
                    if (which == 0) {
                        viewFinder.setVisibility(View.VISIBLE);
                        galleryImageView.setVisibility(View.GONE);
                        btnCapture.setText("사진\n촬영");
                        if (allPermissionsGranted()) cameraHandler.startCamera(this);
                        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
                    } else {
                        pickMedia.launch(new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE).build());
                    }
                }).show();
    }

    private void navigateTo(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
    }

    private void showUrlInputDialog() {
        final EditText input = new EditText(this);
        input.setHint(" URL을 입력하세요");
        new AlertDialog.Builder(this).setTitle("URL 입력").setView(input)
                .setPositiveButton("확인", (dialog, which) -> Toast.makeText(this, "URL 확인 완료", Toast.LENGTH_SHORT).show())
                .setNegativeButton("취소", (dialog, which) -> dialog.cancel()).show();
    }

    private void applyClickAnimation(View view) {
        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                .withEndAction(() -> view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()).start();
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }
}