package com.capstone.aidetector;

import android.Manifest;
import android.content.Intent; // [추가] 화면 전환을 위한 Intent 추가
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
import android.widget.TextView; // [추가] 하단 탭 TextView 추가
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageCapture;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.support.image.TensorImage; // [변경됨: 라이브러리 추가]

import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {
    private PreviewView viewFinder;
    private ImageView galleryImageView;
    private ProgressBar loadingIndicator; // 대기 화면 스피너
    private Button btnCapture;
    private ImageButton btnSelect; // XML 타입에 맞춰 ImageButton으로 변경
    private Button btnUrl;
    private ImageCapture imageCapture;
    private CameraHandler cameraHandler;

    private static final String TAG = "AIDetector_Main";
    // [추가] 하단 탭 변수 선언
    private TextView nav_history;
    private TextView nav_settings;

    private static final String TAG = "GalleryTest";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    // Photo Picker 런처
    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) processGalleryMedia(uri);
            });

    // [변경됨: 전처리용 변수 및 프로세서 추가]
    private Bitmap currentBitmap = null;
    private AiProcessor aiProcessor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 주의: XML 파일 이름이 activity_home.xml이면 여기도 수정이 필요할 수 있습니다.

        // [변경됨: 프로세서 초기화]
        aiProcessor = new AiProcessor();

        viewFinder = findViewById(R.id.viewFinder);
        galleryImageView = findViewById(R.id.galleryImageView);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        btnCapture = findViewById(R.id.btnCapture);
        btnSelect = findViewById(R.id.btnSelect);
        btnUrl = findViewById(R.id.btnUrl);

        // [추가] 하단 탭 뷰 찾기
        nav_history = findViewById(R.id.nav_history);
        nav_settings = findViewById(R.id.nav_settings);

        btnSelect.setOnClickListener(v -> showSelectionDialog());
        // 갤러리 결과 처리
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) processGalleryImage(uri); }
        );

        // [왼쪽 버튼] 갤러리/카메라 선택창
        btnSelect.setOnClickListener(v -> {
            applyClickAnimation(v);
            showSelectionDialog();
        });

        // 카메라 핸들러 초기화
        cameraHandler = new CameraHandler(this, viewFinder);

        btnCapture.setOnClickListener(v -> {
    // 1. UI 디테일: 클릭 애니메이션 적용
    applyClickAnimation(v);

    String mode = btnCapture.getText().toString();

    // 2. 모드 확인: "사진"이라는 글자가 포함되어 있으면 촬영 모드
    if (mode.contains("사진")) {
        takePhoto();
    } else {
        // 3. 분석 모드: MediaHandler에서 데이터 추출 (jihyeon 로직)
        Bitmap bitmapToAnalyze = MediaHandler.getBitmap();
        Uri uriToAnalyze = MediaHandler.getUri();

        // 4. 예외 처리 및 AI 전처리 실행 (feature/ai-preprocessing 로직 통합)
        if (bitmapToAnalyze != null) {
            Log.d(TAG, "== 분석 시작 (MediaHandler 데이터 확인) ==");
            Toast.makeText(this, "딥페이크 분석을 시작합니다...", Toast.LENGTH_SHORT).show();

            try {
                // [핵심] AiProcessor를 이용한 이미지 전처리 실행 (TensorImage 변환)
                // currentBitmap 대신 bitmapToAnalyze를 사용하여 데이터 일관성 유지
                TensorImage processedImage = aiProcessor.processImage(bitmapToAnalyze);
                
                Log.d(TAG, "[체크리스트] AI 전처리 완료: " + 
                        processedImage.getWidth() + "x" + processedImage.getHeight());
                
                // TODO: 모델 추론(Inference) 메서드 호출 (예: runInference(processedImage))
                
            } catch (Exception e) {
                Log.e(TAG, "전처리 중 오류 발생: " + e.getMessage());
                Toast.makeText(this, "이미지 분석 준비 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
            }

        } else {
            Toast.makeText(this, "분석할 사진을 먼저 골라주세요!", Toast.LENGTH_SHORT).show();
        }
    }
});

// [오른쪽 버튼] URL 입력 기능 (master 유지)
btnUrl.setOnClickListener(v -> {
    applyClickAnimation(v);
    showUrlInputDialog();
});

// [하단 탭] 이력 및 설정 이동 (master 유지)
nav_history.setOnClickListener(v -> {
    Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
    startActivity(intent);
});

nav_settings.setOnClickListener(v -> {
    Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
    startActivity(intent);
});

    // --- 0.2초 살짝 눌리는 애니메이션 ---
    private void applyClickAnimation(View view) {
        view.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start();
                }).start();
    }

    private void showSelectionDialog() {
    String[] options = {"📷 카메라 촬영", "🖼️ 갤러리 불러오기"};
    new AlertDialog.Builder(this)
            .setTitle("데이터 가져오기")
            .setItems(options, (dialog, which) -> {
                // 1. [master 반영] 새로운 미디어를 선택하므로 로딩 스피너는 숨김
                loadingIndicator.setVisibility(View.GONE);

                if (which == 0) {
                    // 카메라 선택 시 뷰 전환
                    viewFinder.setVisibility(View.VISIBLE);
                    galleryImageView.setVisibility(View.GONE);
                    
                    // 2. [master 반영] 버튼 텍스트 줄바꿈 (UI 디자인 유지)
                    btnCapture.setText("사진\n촬영");

                    // 3. [jihyeon 반영] cameraHandler 객체를 통한 구조화된 실행
                    if (allPermissionsGranted()) {
                        cameraHandler.startCamera(this);
                    } else {
                        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
                    }
                } else {
                    // 4. [jihyeon 반영] 구글 권장 최신 Photo Picker API 사용
                    // 기존 galleryLauncher.launch("image/*")보다 보안과 UX 면에서 더 우수합니다.
                    pickMedia.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                            .build());
                }
            }).show();
}

    private void processGalleryMedia(Uri uri) {
        Log.d(TAG, "선택한 미디어 Uri: " + uri.toString());

        if (!MediaHandler.isSizeValid(this, uri)) {
            Toast.makeText(this, "20MB 이하만 가능합니다.", Toast.LENGTH_LONG).show();
            return;
        }
    // --- URL 텍스트 다이얼로그 호출 ---
    private void showUrlInputDialog() {
        final EditText input = new EditText(this);
        input.setHint(" URL을 입력하세요");

        new AlertDialog.Builder(this)
                .setTitle("URL 입력")
                .setView(input)
                .setPositiveButton("확인", (dialog, which) -> {
                    String url = input.getText().toString();
                    loadingIndicator.setVisibility(View.GONE);
                    Toast.makeText(this, "URL 확인 완료", Toast.LENGTH_SHORT).show();
                    // 추후 다운로드 로직 연결
                })
                .setNegativeButton("취소", (dialog, which) -> dialog.cancel())
                .show();
    }

    private void processGalleryImage(Uri uri) {
    // UI 상태 초기화
    viewFinder.setVisibility(View.GONE);
    galleryImageView.setVisibility(View.VISIBLE);
    btnCapture.setText("검사\n시작");

    Log.d(TAG, "선택한 사진 Uri: " + uri.toString());

    // [통합] MediaHandler를 통해 비트맵 생성 (Master의 구조적 방식)
    Bitmap bitmap = MediaHandler.processBitmap(this, uri);
    
    if (bitmap != null) {
        // [Feature 반영] AI 전처리에 사용할 변수 업데이트
        currentBitmap = bitmap;
        
        // [Master 반영] MediaHandler에 데이터 저장 및 UI 업데이트
        MediaHandler.setMedia(bitmap, uri);
        galleryImageView.setImageBitmap(bitmap);

        Log.d(TAG, "Bitmap 생성 성공: " + bitmap.getWidth() + "x" + bitmap.getHeight());

        // [Master 반영] 영상 파일인 경우 로그 기록
        String mimeType = getContentResolver().getType(uri);
        if (mimeType != null && mimeType.startsWith("video")) {
            Log.d(TAG, "영상 로드 성공 - URI: " + uri.toString() + " / 썸네일 추출 완료");
        }
    } else {
        Toast.makeText(this, "이미지를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show();
    }
}
      
      private void takePhoto() {
    // CameraHandler를 사용하여 캡처 로직을 캡슐화 (Master 방식 유지)
    cameraHandler.takePhoto((bitmap, uri) -> {
        runOnUiThread(() -> {
            if (bitmap != null) {
                // [Feature 반영] 촬영된 비트맵을 AI 전처리 변수에 저장
                currentBitmap = bitmap;
                
                // [Master 반영] MediaHandler 업데이트 및 UI 전환
                MediaHandler.setMedia(bitmap, uri);
                
                viewFinder.setVisibility(View.GONE);
                galleryImageView.setVisibility(View.VISIBLE);
                galleryImageView.setImageBitmap(currentBitmap);
                btnCapture.setText("검사\n시작");

                Log.d(TAG, "[카메라] Bitmap 생성 성공: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                Log.d(TAG, "카메라 저장 완료 - Uri: " + (uri != null ? uri.toString() : "실패"));
            }
        });
    });
}
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }
}