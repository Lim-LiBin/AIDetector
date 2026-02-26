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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 주의: XML 파일 이름이 activity_home.xml이면 여기도 수정이 필요할 수 있습니다.

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
    // 1. 클릭 애니메이션 적용 (master의 디테일 유지)
          applyClickAnimation(v);

          String mode = btnCapture.getText().toString();

    // 2. "사진" 포함 여부로 분기 (master의 줄바꿈 대응 로직)
          if (mode.contains("사진")) {
              takePhoto();
          } else {
        // 3. MediaHandler에서 데이터 가져오기 (jihyeon의 핵심 로직)
              Bitmap bitmapToAnalyze = MediaHandler.getBitmap();
              Uri uriToAnalyze = MediaHandler.getUri();

        // 4. 데이터 유효성 및 뷰 상태 검사 (두 브랜치 예외처리 합체)
              if (bitmapToAnalyze != null) {
            // 최종 확인 로그 및 분석 시작 안내
                  Log.d(TAG, "== 분석 시작 (MediaHandler 데이터 확인) ==");
                  Log.d(TAG, "최종 Bitmap: " + bitmapToAnalyze.getWidth() + "x" + bitmapToAnalyze.getHeight());
                  Log.d(TAG, "최종 URI: " + (uriToAnalyze != null ? uriToAnalyze.toString() : "No URI"));
            
                  Toast.makeText(this, "딥페이크 분석을 시작합니다...", Toast.LENGTH_SHORT).show();
            
            // TODO: 실제 분석 로직 실행 함수 호출
              } else {
            // 분석할 미디어가 없는 경우
                  Toast.makeText(this, "분석할 사진을 먼저 골라주세요!", Toast.LENGTH_SHORT).show();
              }
          }
        });

        // [오른쪽 버튼] URL 입력 기능
        btnUrl.setOnClickListener(v -> {
            applyClickAnimation(v);
            showUrlInputDialog();
        });

        // [추가] 하단 탭 클릭 이벤트 - 이력
        nav_history.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        });

        // [추가] 하단 탭 클릭 이벤트 - 설정
        nav_settings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        });
    }

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
        viewFinder.setVisibility(View.GONE);
        galleryImageView.setVisibility(View.VISIBLE);
        galleryImageView.setImageURI(uri);
        btnCapture.setText("검사\n시작");

        Log.d(TAG, "선택한 사진 Uri: " + uri.toString()); //[체크리스트] Uri 로그

        // 비트맵 생성 및 MediaHandler 저장
        Bitmap bitmap = MediaHandler.processBitmap(this, uri);
        MediaHandler.setMedia(bitmap, uri);

        if (bitmap != null) {
            galleryImageView.setImageBitmap(bitmap);

            // 비트맵 생성 확인 로그
            Log.d(TAG, "Bitmap 생성 성공: " + bitmap.getWidth() + "x" + bitmap.getHeight());

            String mimeType = getContentResolver().getType(uri);
            if (mimeType != null && mimeType.startsWith("video")) {
                Log.d(TAG, "영상 로드 성공 - URI: " + uri.toString() + " / 썸네일 추출 완료");
            }
        }

        viewFinder.setVisibility(View.GONE);
        galleryImageView.setVisibility(View.VISIBLE);
        btnCapture.setText("검사 시작");
    }

    private void takePhoto() {
        cameraHandler.takePhoto((bitmap, uri) -> {
            runOnUiThread(() -> {
                galleryImageView.setImageBitmap(bitmap);
                viewFinder.setVisibility(View.GONE);
                galleryImageView.setVisibility(View.VISIBLE);
                btnCapture.setText("검사 시작");

                // 카메라 촬영 비트맵 로그
                Log.d(TAG, "[카메라] Bitmap 생성 성공: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                Log.d(TAG, "카메라 저장 완료 - Uri: " + (uri != null ? uri.toString() : "실패"));
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