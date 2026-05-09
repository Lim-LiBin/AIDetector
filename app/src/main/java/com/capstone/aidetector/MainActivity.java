package com.capstone.aidetector;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
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

import java.io.InputStream;

// ⭐️ 말풍선 라이브러리 import
import com.skydoves.balloon.ArrowOrientation;
import com.skydoves.balloon.ArrowPositionRules;
import com.skydoves.balloon.Balloon;
import com.skydoves.balloon.BalloonAnimation;
import com.skydoves.balloon.BalloonSizeSpec;
import com.skydoves.balloon.overlay.BalloonOverlayOval;
import com.skydoves.balloon.overlay.BalloonOverlayRect;

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
    // 튜토리얼 내부 메모장 설정 키값
    private static final String PREF_NAME = "TutorialPrefs";
    private static final String KEY_HAS_SEEN_MAIN_TUTORIAL = "HasSeenMainTutorial";
    private static final String KEY_HAS_SEEN_ACTION_TUTORIAL = "HasSeenActionTutorial";

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
                uri -> {
                    if (uri != null) {
                        processGalleryImage(uri);
                        // 갤러리 이미지 로드 후 튜토리얼 실행
                        showActionTutorial();
                    }
                }
        );

        // 3. 선택 버튼 리스너 (팝업창 호출)
        btnSelect.setOnClickListener(v -> showImageSourceDialog());

        // 4. URL 버튼 리스너
        btnUrl.setOnClickListener(v -> showUrlInputDialog());

        // 5. 중앙 버튼 리스너 (촬영 또는 검사 시작)
        btnCapture.setOnClickListener(v -> {
            if (viewFinder.getVisibility() == View.VISIBLE) {
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

        // 앱이 켜진 후 메인 튜토리얼 실행 (0.5초 지연)
        btnSelect.postDelayed(this::startInteractiveTutorial, 500);
    }

    private void startInteractiveTutorial() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // 이미 튜토리얼을 봤다면 실행하지 않음 (테스트 시엔 주석 처리하세요)
        // if (prefs.getBoolean(KEY_HAS_SEEN_MAIN_TUTORIAL, false)) return;

        // 뷰가 완전히 그려진 후 1단계 시작
        View centerContainer = findViewById(R.id.centerContainer);
        centerContainer.post(this::step1_Preview);
    }

    // 사진 영역
    private void step1_Preview() {
        View centerContainer = findViewById(R.id.centerContainer);

        Balloon balloon = new Balloon.Builder(this)
                .setWidthRatio(0.7f)
                .setHeight(BalloonSizeSpec.WRAP)
                .setText("이 넓은 영역에\n카메라 화면이나 불러온 사진이\n표시됩니다.")
                .setTextColorResource(android.R.color.black)
                .setBackgroundColor(android.graphics.Color.parseColor("#FFFF00"))
                .setCornerRadius(8f)
                .setArrowSize(0)
                .setPadding(16)
                .setTextSize(20f)
                .setIsVisibleOverlay(true)
                .setOverlayColor(android.graphics.Color.parseColor("#80000000"))
                .setOverlayShape(BalloonOverlayRect.INSTANCE) // 넓은 영역(centerContainer)만 밝게 강조
                .setBalloonAnimation(BalloonAnimation.FADE)
                .setLifecycleOwner(this)
                .setDismissWhenClicked(true)
                .build();

        balloon.setOnBalloonDismissListener(this::step2_SelectButton);

        // 타겟의 정중앙에 띄우기
        balloon.showAtCenter(centerContainer);
    }

    // 선택 버튼 설명 및 팝업 띄우기
    private void step2_SelectButton() {
        Balloon balloon = createBaseBalloon("이 버튼을 누르면\n갤러리/카메라 선택이 \n가능합니다.");
        balloon.setOnBalloonDismissListener(this::showTutorialImageSourceDialog);
        balloon.showAlignBottom(btnSelect);
    }

    // 이미지 소스 선택 팝업 안에서의 설명
    private void showTutorialImageSourceDialog() {
        String[] options = {"카메라로 촬영", "갤러리에서 선택"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("이미지 가져오기")
                .setItems(options, null) // 튜토리얼용이라 클릭 동작은 임시로 비움
                .create();

        dialog.setOnShowListener(d -> {
            Balloon balloon = createBaseBalloon("이곳에서 촬영할지,\n갤러리에서 고를지 \n선택하세요!");
            View contentView = dialog.getWindow().findViewById(android.R.id.content);
            contentView.post(() -> balloon.showAlignBottom(contentView));
            balloon.setOnBalloonDismissListener(dialog::dismiss);
        });

        dialog.setOnDismissListener(d -> step3_UrlButton());
        dialog.show();
    }

    // URL 버튼 설명 및 입력창 띄우기
    private void step3_UrlButton() {
        Balloon balloon = createBaseBalloon("유튜브나 인스타 등\n영상 주소를 입력합니다.");
        balloon.setOnBalloonDismissListener(this::showTutorialUrlDialog);
        balloon.showAlignBottom(btnUrl);
    }

    // URL 입력창 안에서의 설명
    private void showTutorialUrlDialog() {
        final EditText input = new EditText(this);
        input.setHint("URL을 입력해주세요");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("URL 입력")
                .setView(input)
                .setPositiveButton("확인", null)
                .setNegativeButton("취소", null)
                .create();

        dialog.setOnShowListener(d -> {
            Balloon balloon = createBaseBalloon("여기에 유튜브나 인스타\n링크를 붙여넣으세요!");
            balloon.showAlignBottom(input);
            balloon.setOnBalloonDismissListener(dialog::dismiss);
        });

        dialog.setOnDismissListener(d -> step4_HistoryTab());
        dialog.show();
    }

    // 이력 탭 설명 후 화면 이동
    private void step4_HistoryTab() {
        View navHistory = findViewById(R.id.nav_history);
        Balloon balloon = createBaseBalloon("과거 분석 기록을 \n보는곳 입니다!\n화면이 이동됩니다!");

        balloon.setOnBalloonDismissListener(() -> {
            SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putBoolean(KEY_HAS_SEEN_MAIN_TUTORIAL, true)
                    .putBoolean("NEEDS_HISTORY_TUTORIAL", true)
                    .apply();

            startActivity(new Intent(MainActivity.this, HistoryActivity.class));
        });

        balloon.showAlignTop(navHistory);
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
    // 공통 말풍선 생성기
    private Balloon createBaseBalloon(String text) {
        return new Balloon.Builder(this)
                .setWidthRatio(0.7f)
                .setHeight(BalloonSizeSpec.WRAP)
                .setText(text)
                .setTextColorResource(android.R.color.black)
                .setBackgroundColor(android.graphics.Color.parseColor("#FFFF00"))
                .setCornerRadius(8f)
                .setArrowSize(12)
                .setArrowOrientation(ArrowOrientation.TOP)
                .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                .setArrowPosition(0.5f)
                .setPadding(16)
                .setTextSize(20f)
                .setIsVisibleOverlay(true)
                .setOverlayColor(android.graphics.Color.parseColor("#E6000000"))
                .setOverlayShape(BalloonOverlayRect.INSTANCE)
                .setBalloonAnimation(BalloonAnimation.FADE)
                .setLifecycleOwner(this)
                .setDismissWhenClicked(true)
                .build();
    }

    //  사진 촬영/선택 완료 시, "검사 시작" 버튼 안내
    private void showActionTutorial() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // 이미 튜토리얼 2탄을 봤다면 실행하지 않음
        // if (prefs.getBoolean(KEY_HAS_SEEN_ACTION_TUTORIAL, false)) return;

        Balloon balloonAction = new Balloon.Builder(this)
                .setWidthRatio(0.7f)
                .setHeight(BalloonSizeSpec.WRAP)
                .setText("분석할 사진이 준비되었습니다.\n이제 '검사 시작' 버튼을 누르세요!")
                .setTextColorResource(android.R.color.black)
                .setBackgroundColor(android.graphics.Color.parseColor("#FFFF00"))
                .setCornerRadius(8f)
                .setArrowSize(12)
                .setArrowOrientation(ArrowOrientation.TOP)
                .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                .setArrowPosition(0.5f)
                .setPadding(16)
                .setTextSize(20f)
                .setIsVisibleOverlay(true)
                .setOverlayColor(android.graphics.Color.parseColor("#E6000000"))
                .setOverlayShape(BalloonOverlayOval.INSTANCE) // 검사 시작 버튼 조명
                .setOverlayPadding(8f)
                .setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                .setLifecycleOwner(this)
                .setDismissWhenClicked(true)
                .build();

        // 🚀 "검사 시작" 버튼 위에 띄우기 (뷰가 완전히 그려질 때까지 기다립니다)
        btnCapture.post(() -> {
            balloonAction.showAlignTop(btnCapture);
            // 튜토리얼 2탄을 봤다고 내부 메모장에 기록
            prefs.edit().putBoolean(KEY_HAS_SEEN_ACTION_TUTORIAL, true).apply();
        });
    }

    //카메라/갤러리 선택 팝업 (실제 기능)
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

            // ⭐️ [추가] 카메라 화면이 켜지면, "여기 화면에 나오게 하고"라는 설명 실행
            showCameraPreviewTutorial();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    // 카메라 켜졌을 때, "여기 화면에 나오게" 설명
    private void showCameraPreviewTutorial() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // 이미 카메라 미리보기 튜토리얼을 봤다면 실행하지 않음
        // if (prefs.getBoolean("HasSeenCameraPreviewTutorial", false)) return;

        Balloon balloonCameraPreview = new Balloon.Builder(this)
                .setWidthRatio(0.7f)
                .setHeight(BalloonSizeSpec.WRAP)
                .setText("이 넓은 영역에\n카메라가 비추는 화면이 표시됩니다.\n원하는 대상을 찍어주세요!")
                .setTextColorResource(android.R.color.black)
                .setBackgroundColor(android.graphics.Color.parseColor("#FFFF00"))
                .setCornerRadius(8f)
                .setArrowSize(0) // 중앙 배치 시 화살표 제거
                .setPadding(16)
                .setTextSize(20f)
                .setIsVisibleOverlay(true)
                .setOverlayColor(android.graphics.Color.parseColor("#E6000000"))
                .setOverlayShape(BalloonOverlayRect.INSTANCE) // 사진 영역(viewFinder) 조명
                .setOverlayPadding(0f)
                .setBalloonAnimation(BalloonAnimation.FADE)
                .setLifecycleOwner(this)
                .setDismissWhenClicked(true)
                .build();

        // 🚀 카메라 영역 정중앙에 띄우기 (뷰가 완전히 그려질 때까지 기다립니다)
        viewFinder.post(() -> {
            balloonCameraPreview.showAtCenter(viewFinder);
            // 메모장에 기록
            prefs.edit().putBoolean("HasSeenCameraPreviewTutorial", true).apply();
        });
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

                // 촬영 완료 후 튜토리얼 2탄 실행
                showActionTutorial();
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
        // ⭐️ [수정 핵심] 원본 해상도 그대로 가져오지 않고 안전하게 리사이징하여 가져옵니다.
        currentBitmap = MediaHandler.processBitmap(this, uri);

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

    // URL 입력 (실제 기능)
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