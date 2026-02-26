package com.capstone.aidetector;

import android.Manifest;
import android.content.Intent; // [추가] 화면 전환을 위한 Intent 추가
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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView; // [추가] 하단 탭 TextView 추가
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
    private ProgressBar loadingIndicator; // 대기 화면 스피너
    private Button btnCapture;
    private ImageButton btnSelect; // XML 타입에 맞춰 ImageButton으로 변경
    private Button btnUrl;
    private ImageCapture imageCapture;

    // [추가] 하단 탭 변수 선언
    private TextView nav_history;
    private TextView nav_settings;

    private static final String TAG = "GalleryTest";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    private ActivityResultLauncher<String> galleryLauncher;

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

        // [중앙 버튼] 상황에 따라 동작 변경
        btnCapture.setOnClickListener(v -> {
            applyClickAnimation(v);
            String mode = btnCapture.getText().toString();

            // [수정 1] "사진 찍기" -> 줄바꿈 대응을 위해 contains 사용
            if (mode.contains("사진")) {
                takePhoto();
            } else {
                // [수정 2] 카메라 뷰어도 켜져있지 않은지 함께 검사하여 버그 방지
                if (galleryImageView.getVisibility() == View.GONE && viewFinder.getVisibility() == View.GONE) {
                    Toast.makeText(this, "분석할 사진을 먼저 골라주세요!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "검사 시작합니다...", Toast.LENGTH_SHORT).show();
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
                .setTitle("사진 가져오기")
                .setItems(options, (dialog, which) -> {
                    // [수정 3] 미디어를 선택하면 스피너 숨김
                    loadingIndicator.setVisibility(View.GONE);

                    if (which == 0) {
                        viewFinder.setVisibility(View.VISIBLE);
                        galleryImageView.setVisibility(View.GONE);
                        btnCapture.setText("사진\n촬영");
                        if (allPermissionsGranted()) startCamera();
                        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
                    } else {
                        galleryLauncher.launch("image/*");
                    }
                }).show();
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

        try {
            Bitmap bitmap;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(this.getContentResolver(), uri));
            } else {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
            }
            Log.d(TAG, "Bitmap 생성 성공: " + bitmap.getWidth() + "x" + bitmap.getHeight()); // [체크리스트] 비트맵 로그
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void takePhoto() {
        if (imageCapture == null) return;
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                Bitmap bitmap = imageProxyToBitmap(image);
                runOnUiThread(() -> {
                    viewFinder.setVisibility(View.GONE);
                    galleryImageView.setVisibility(View.VISIBLE);
                    galleryImageView.setImageBitmap(bitmap);
                    btnCapture.setText("검사\n시작");
                    Log.d(TAG, "카메라 Bitmap 생성 성공: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                });
                image.close();
            }
            @Override
            public void onError(@NonNull ImageCaptureException e) { Log.e(TAG, "촬영 실패", e); }
        });
    }

    // --- 카메라X 설정 및 유틸리티 메서드 ---
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
}