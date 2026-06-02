package com.capstone.aidetector;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ViewFlipper;
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

import com.skydoves.balloon.ArrowOrientation;
import com.skydoves.balloon.ArrowPositionRules;
import com.skydoves.balloon.Balloon;
import com.skydoves.balloon.BalloonAnimation;
import com.skydoves.balloon.BalloonSizeSpec;
import com.skydoves.balloon.overlay.BalloonOverlayOval;
import com.skydoves.balloon.overlay.BalloonOverlayRect;

// 메인 화면에서 카메라 촬영, 갤러리 선택, URL 입력 및 분석 화면 이동을 담당하는 Activity
public class MainActivity extends AppCompatActivity {

    private PreviewView viewFinder;
    private ImageView galleryImageView;
    private Button btnCapture;
    private ImageButton btnSelect;
    private Button btnUrl;

    private ViewFlipper sampleFlipper;
    private static final String TAG = "AiDetector_Main";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    private ActivityResultLauncher<String> galleryLauncher;
    private Bitmap currentBitmap = null;
    private Uri currentImageUri = null;
    private AiProcessor aiProcessor;
    private CameraHandler cameraHandler;
    private boolean isAnalyzing = false;

    private boolean isBackFromAnalysis = false;

    // 메인 화면 튜토리얼 실행 여부 저장 키
    private static final String PREF_NAME = "TutorialPrefs";
    private static final String KEY_HAS_SEEN_MAIN_TUTORIAL = "HasSeenMainTutorial";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        aiProcessor = new AiProcessor(this);

        // 메인 화면 View 및 카메라 핸들러 초기화
        viewFinder = findViewById(R.id.viewFinder);
        galleryImageView = findViewById(R.id.galleryImageView);
        btnCapture = findViewById(R.id.btnCapture);
        btnSelect = findViewById(R.id.btnSelect);
        btnUrl = findViewById(R.id.btnUrl);

        cameraHandler = new CameraHandler(this, viewFinder);

        sampleFlipper = findViewById(R.id.sampleFlipper);
        // 갤러리에서 이미지 또는 영상을 선택하기 위한 런처 설정
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        processGalleryMedia(uri);
                    }
                }
        );

        // 이미지 가져오기 선택 팝업 표시
        btnSelect.setOnClickListener(v -> showImageSourceDialog());

        // URL 입력 팝업 표시
        btnUrl.setOnClickListener(v -> showUrlInputDialog());

        // 현재 화면 상태에 따라 사진 촬영 또는 분석 시작
        btnCapture.setOnClickListener(v -> {
            if (viewFinder.getVisibility() == View.VISIBLE) {
                capturePhotoFromHandler();
            } else {
                if (currentImageUri == null) {
                    Toast.makeText(this, "분석할 사진이나 영상을 선택해주세요!", Toast.LENGTH_SHORT).show();
                } else {
                    // 선택된 파일의 MIME 타입에 따라 이미지 분석과 영상 분석을 구분
                    String mimeType = getContentResolver().getType(currentImageUri);
                    if (mimeType != null && mimeType.startsWith("video")) {
                        runVideoAnalysisFromGallery();
                    } else {
                        runDeepfakeAnalysisWithVisualization();
                    }
                }
            }
        });

        // 하단 이력 탭으로 이동
        findViewById(R.id.nav_history).setOnClickListener(v -> {
            isAnalyzing = true;
            startActivity(new Intent(this, HistoryActivity.class));
        });

        // 하단 설정 탭으로 이동
        findViewById(R.id.nav_settings).setOnClickListener(v -> {
            isAnalyzing = true;
            startActivity(new Intent(this, SettingsActivity.class));
        });

        // 메인 화면 첫 실행 시 튜토리얼 시작
        btnSelect.postDelayed(this::startInteractiveTutorial, 500);
    }

    // 메인 화면 튜토리얼 실행 여부 확인 후 시작
    private void startInteractiveTutorial() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_HAS_SEEN_MAIN_TUTORIAL, false)) return;

        View centerContainer = findViewById(R.id.centerContainer);
        centerContainer.post(this::step1_Preview);
    }

    // 튜토리얼 1단계: 이미지/카메라 미리보기 영역 설명
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
                .setOverlayShape(BalloonOverlayRect.INSTANCE)
                .setBalloonAnimation(BalloonAnimation.FADE)
                .setLifecycleOwner(this)
                .setDismissWhenClicked(true)
                .build();

        balloon.setOnBalloonDismissListener(this::step2_SelectButton);
        balloon.showAtCenter(centerContainer);
    }

    // 튜토리얼 2단계: 이미지 선택 버튼 설명
    private void step2_SelectButton() {
        Balloon balloon = createBaseBalloon("이 버튼을 누르면\n갤러리/카메라 선택이 \n가능합니다.");
        balloon.setOnBalloonDismissListener(this::showTutorialImageSourceDialog);
        balloon.showAlignBottom(btnSelect);
    }

    // 튜토리얼용 이미지 가져오기 팝업 표시
    private void showTutorialImageSourceDialog() {
        String[] options = {"카메라로 촬영", "갤러리(이미지/영상) 선택"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("이미지 가져오기")
                .setItems(options, null)
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

    // 튜토리얼 3단계: URL 입력 버튼 설명
    private void step3_UrlButton() {
        Balloon balloon = createBaseBalloon("유튜브나 인스타 등\n영상 주소를 입력합니다.");
        balloon.setOnBalloonDismissListener(this::showTutorialUrlDialog);
        balloon.showAlignBottom(btnUrl);
    }

    // 튜토리얼용 URL 입력 팝업 표시
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

        dialog.setOnDismissListener(d -> step4_CaptureButton());
        dialog.show();
    }

    // 튜토리얼 4단계: 검사 시작 버튼 설명
    private void step4_CaptureButton() {
        Balloon balloonAction = new Balloon.Builder(this)
                .setWidthRatio(0.7f)
                .setHeight(BalloonSizeSpec.WRAP)
                .setText("사진이나 영상이 준비되면\n이 '검사 시작' 버튼을 누르세요!")
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
                .setOverlayShape(BalloonOverlayOval.INSTANCE)
                .setOverlayPadding(8f)
                .setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                .setLifecycleOwner(this)
                .setDismissWhenClicked(true)
                .build();

        balloonAction.setOnBalloonDismissListener(this::step5_HistoryTab);
        btnCapture.post(() -> balloonAction.showAlignTop(btnCapture));
    }

    // 튜토리얼 5단계: 이력 탭 설명 후 이력 화면으로 이동
    private void step5_HistoryTab() {
        View navHistory = findViewById(R.id.nav_history);
        Balloon balloon = createBaseBalloon("과거 분석 기록을 \n보는 곳 입니다!\n화면이 이동됩니다!");

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

    @Override
    protected void onResume() {
        super.onResume();
        // 분석 또는 다른 탭 화면에서 돌아온 경우 메인 화면 상태 초기화
        if (isAnalyzing) {
            resetMainUI();
            isAnalyzing = false;
        }
    }

    // 메인 화면의 선택 이미지, 카메라, 버튼 상태 초기화
    private void resetMainUI() {
        currentBitmap = null;
        currentImageUri = null;
        BitmapHolder.originalBitmap = null;

        galleryImageView.setImageBitmap(null);
        galleryImageView.setVisibility(View.GONE);

        findViewById(R.id.centerContainer).setBackgroundColor(android.graphics.Color.parseColor("#1E1838"));

        viewFinder.setVisibility(View.GONE);

        btnCapture.setText("검사\n시작");

        sampleFlipper.setVisibility(View.VISIBLE);
        sampleFlipper.startFlipping();

        stopCameraResources();
    }

    // 튜토리얼에서 사용하는 공통 말풍선 생성
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

    // 카메라 촬영 또는 갤러리 선택 팝업 표시
    private void showImageSourceDialog() {
        String[] options = {"카메라로 촬영", "갤러리(이미지/영상) 선택"};
        new AlertDialog.Builder(this)
                .setTitle("이미지 가져오기")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) startCameraMode();
                    else galleryLauncher.launch("*/*");
                })
                .show();
    }

    // 카메라 프리뷰 모드 시작
    private void startCameraMode() {
        if (allPermissionsGranted()) {
            currentBitmap = null;
            currentImageUri = null;

            sampleFlipper.setVisibility(View.GONE);
            sampleFlipper.stopFlipping();

            viewFinder.setVisibility(View.VISIBLE);
            galleryImageView.setVisibility(View.GONE);
            btnCapture.setText("사진 촬영");

            cameraHandler.startCamera(this);

        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    // 카메라로 사진을 촬영하고 미리보기 화면에 표시
    private void capturePhotoFromHandler() {
        cameraHandler.takePhoto((bitmap, uri) -> {
            runOnUiThread(() -> {
                currentBitmap = bitmap;
                currentImageUri = uri;

                stopCameraResources();
                viewFinder.setVisibility(View.GONE);
                galleryImageView.setVisibility(View.VISIBLE);
                galleryImageView.setImageBitmap(currentBitmap);

                findViewById(R.id.centerContainer).setBackgroundColor(android.graphics.Color.parseColor("#110E1B"));

                sampleFlipper.setVisibility(View.GONE);
                sampleFlipper.stopFlipping();

                btnCapture.setText("검사\n시작");
            });
        });
    }

    // 갤러리에서 선택한 이미지 또는 영상 처리
    private void processGalleryMedia(Uri uri) {
        this.currentImageUri = uri;
        stopCameraResources();

        viewFinder.setVisibility(View.GONE);
        galleryImageView.setVisibility(View.VISIBLE);

        findViewById(R.id.centerContainer).setBackgroundColor(android.graphics.Color.parseColor("#110E1B"));

        sampleFlipper.setVisibility(View.GONE);
        sampleFlipper.stopFlipping();

        String mimeType = getContentResolver().getType(uri);

        if (mimeType != null && mimeType.startsWith("video")) {
            // 영상 파일은 썸네일을 추출해 미리보기로 표시
            try {
                Bitmap thumbnail = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    thumbnail = getContentResolver().loadThumbnail(uri, new android.util.Size(1024, 1024), null);
                } else {
                    String videoId = uri.getLastPathSegment();
                    if (videoId != null) {
                        thumbnail = android.provider.MediaStore.Video.Thumbnails.getThumbnail(
                                getContentResolver(),
                                Long.parseLong(videoId),
                                android.provider.MediaStore.Video.Thumbnails.MINI_KIND, null);
                    }
                }

                if (thumbnail != null) {
                    galleryImageView.setImageBitmap(thumbnail);
                    this.currentBitmap = thumbnail;
                } else {
                    galleryImageView.setImageResource(android.R.drawable.presence_video_online);
                }
                btnCapture.setText("검사\n시작");
            } catch (Exception e) {
                e.printStackTrace();
                galleryImageView.setImageResource(android.R.drawable.presence_video_online);
                btnCapture.setText("검사\n시작");
            }
        } else {
            // 이미지 파일은 Bitmap으로 불러와 미리보기로 표시
            this.currentBitmap = getResizedBitmap(uri, 1024);

            if (this.currentBitmap != null) {
                galleryImageView.setImageBitmap(this.currentBitmap);
                btnCapture.setText("검사\n시작");
            } else {
                Toast.makeText(this, "이미지를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 선택한 이미지 URI를 Bitmap으로 변환
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

    // 선택된 이미지를 LoadingActivity로 전달하여 분석 실행
    private void runDeepfakeAnalysisWithVisualization() {
        isAnalyzing = true;
        BitmapHolder.originalBitmap = currentBitmap;
        Intent intent = new Intent(MainActivity.this, LoadingActivity.class);
        if (currentImageUri != null) {
            intent.setData(currentImageUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra("original_image_uri", currentImageUri.toString());
        }
        startActivity(intent);
    }

    // 갤러리에서 선택한 영상을 LoadingActivity로 전달하여 분석 실행
    private void runVideoAnalysisFromGallery() {
        if (currentImageUri == null) {
            Toast.makeText(this, "선택된 영상이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        isAnalyzing = true;
        Intent intent = new Intent(MainActivity.this, LoadingActivity.class);

        intent.setData(currentImageUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        intent.putExtra("video_url", currentImageUri.toString());
        intent.putExtra("is_video_mode", true);
        intent.putExtra("is_local_video", true);
        startActivity(intent);
    }

    // 실행 중인 카메라 리소스 해제
    private void stopCameraResources() {
        try {
            ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
            cameraProvider.unbindAll();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 카메라 권한 허용 여부 확인
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    // URL 입력 팝업 표시
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

    // 입력된 URL 형식에 따라 이미지 또는 영상 분석으로 분기
    private void processUrl(String url) {
        if (isImageUrl(url)) processImageUrl(url);
        else if (isVideoUrl(url)) processVideoUrl(url);
        else Toast.makeText(this, "지원하지 않는 URL 형식입니다", Toast.LENGTH_SHORT).show();
    }

    // 이미지 URL 형식 여부 확인
    private boolean isImageUrl(String url) {
        String extPattern = "(?i)\\.(jpg|jpeg|png|gif|bmp|webp)(\\?.*)?$";
        return url.matches(".*" + extPattern) || url.toLowerCase().contains("/image") || url.toLowerCase().contains("/img");
    }

    // 영상 URL 형식 여부 확인
    private boolean isVideoUrl(String url) {
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains("youtube.com/watch") || lowerUrl.contains("youtu.be/") || lowerUrl.contains("/shorts/")) return true;
        if (lowerUrl.contains("instagram.com/reel/") || lowerUrl.contains("instagram.com/p/")) return true;
        return url.matches(".*(?i)\\.(mp4|avi|mov|wmv|flv|webm)(\\?.*)?$");
    }

    // 이미지 URL을 LoadingActivity로 전달하여 분석 실행
    private void processImageUrl(String url) {
        isAnalyzing = true;
        stopCameraResources();
        Intent intent = new Intent(MainActivity.this, LoadingActivity.class);
        intent.putExtra("image_url", url);
        intent.putExtra("is_from_url", true);
        startActivity(intent);
    }

    // 영상 URL을 LoadingActivity로 전달하여 분석 실행
    private void processVideoUrl(String url) {
        isAnalyzing = true;
        stopCameraResources();
        Intent intent = new Intent(MainActivity.this, LoadingActivity.class);
        intent.putExtra("video_url", url);
        intent.putExtra("is_video_mode", true);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // TFLite 모델 리소스 해제
        if (aiProcessor != null) aiProcessor.close();
    }
}