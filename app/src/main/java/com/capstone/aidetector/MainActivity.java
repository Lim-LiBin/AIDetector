package com.capstone.aidetector;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
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
    private Button btnCapture;
    private ImageCapture imageCapture;
    private CameraHandler cameraHandler;

    private static final String TAG = "AIDetector_Main";
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
        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.viewFinder);
        galleryImageView = findViewById(R.id.galleryImageView);
        btnCapture = findViewById(R.id.btnCapture);
        Button btnSelect = findViewById(R.id.btnSelect);

        btnSelect.setOnClickListener(v -> showSelectionDialog());

        // 카메라 핸들러 초기화
        cameraHandler = new CameraHandler(this, viewFinder);

        btnCapture.setOnClickListener(v -> {
            if (btnCapture.getText().toString().equals("사진 찍기")) {
                takePhoto();
            } else {
                // MediaHandler에서 데이터를 가져와 분석 시작
                Bitmap bitmapToAnalyze = MediaHandler.getBitmap();
                Uri uriToAnalyze = MediaHandler.getUri();

                if (bitmapToAnalyze != null) {
                    // 최종 확인 로그
                    Log.d(TAG, "== 분석 시작 (MediaHandler 데이터 확인) ==");
                    Log.d(TAG, "최종 Bitmap: " + bitmapToAnalyze.getWidth() + "x" + bitmapToAnalyze.getHeight());
                    Log.d(TAG, "최종 URI: " + (uriToAnalyze != null ? uriToAnalyze.toString() : "No URI"));
                    Toast.makeText(this, "딥페이크 분석을 시작합니다...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "분석할 미디어를 선택해주세요.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showSelectionDialog() {
        String[] options = {"📷 카메라 촬영", "🖼️ 갤러리 불러오기"};
        new AlertDialog.Builder(this)
                .setTitle("데이터 가져오기")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        viewFinder.setVisibility(View.VISIBLE);
                        galleryImageView.setVisibility(View.GONE);
                        btnCapture.setText("사진 찍기");
                        if (allPermissionsGranted()) cameraHandler.startCamera(this);
                        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
                    } else {
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