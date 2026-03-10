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
        Bitmap bitmapToAnalyze = MediaHandler.getBitmap();
        if (bitmapToAnalyze != null) {
            Toast.makeText(this, "딥페이크 분석을 시작합니다...", Toast.LENGTH_SHORT).show();

            try {
                TensorImage processedImage = aiProcessor.processImage(bitmapToAnalyze);

                // [중요] Null 체크 추가: 전처리가 성공했을 때만 로그와 분석 진행
                if (processedImage != null) {
                    Log.d(TAG, "전처리 완료: " + processedImage.getWidth() + "x" + processedImage.getHeight());

                    // TODO: 여기에 추론(inference) 코드를 넣으세요.
                    // runInference(processedImage);

                    // 화면에 알림
                    Toast.makeText(this, "서버에 분석 결과를 기록 중입니다.", Toast.LENGTH_SHORT).show();
                } else {
                    // 전처리 실패 시 사용자 알림
                    Log.e(TAG, "전처리 결과가 null입니다. Logcat에서 'AiProcessor' 에러 메시지를 확인하세요.");
                    Toast.makeText(this, "이미지 처리 중 문제가 발생했습니다.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "분석 시작 중 예외 발생", e);
            }
        } else {
            Toast.makeText(this, "분석할 사진을 먼저 골라주세요!", Toast.LENGTH_SHORT).show();
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