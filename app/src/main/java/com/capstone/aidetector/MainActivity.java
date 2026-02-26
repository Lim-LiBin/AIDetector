package com.capstone.aidetector;

import android.Manifest;
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

import org.tensorflow.lite.support.image.TensorImage; // [변경됨: 라이브러리 추가]

import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    private PreviewView viewFinder;
    private ImageView galleryImageView;
    private Button btnCapture;
    private ImageCapture imageCapture;

    private static final String TAG = "GalleryTest";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    private ActivityResultLauncher<String> galleryLauncher;

    // [변경됨: 전처리용 변수 및 프로세서 추가]
    private Bitmap currentBitmap = null;
    private AiProcessor aiProcessor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // [변경됨: 프로세서 초기화]
        aiProcessor = new AiProcessor();

        viewFinder = findViewById(R.id.viewFinder);
        galleryImageView = findViewById(R.id.galleryImageView);
        btnCapture = findViewById(R.id.btnCapture);
        Button btnSelect = findViewById(R.id.btnSelect);

        // 갤러리 결과 처리
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) processGalleryImage(uri); }
        );

        // [왼쪽 버튼] 갤러리/카메라 선택창
        btnSelect.setOnClickListener(v -> showSelectionDialog());

        // [중앙 버튼] 상황에 따라 동작 변경
        btnCapture.setOnClickListener(v -> {
            String mode = btnCapture.getText().toString();
            if (mode.equals("사진 찍기")) {
                takePhoto();
            } else {
                // 검사 시작 로직
                if (galleryImageView.getDrawable() == null) {
                    Toast.makeText(this, "분석할 사진을 먼저 골라주세요!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "딥페이크 분석을 시작합니다...", Toast.LENGTH_SHORT).show();

                    // [변경됨: AiProcessor 연동 및 전처리 실행]
                    if (currentBitmap != null) {
                        TensorImage processedImage = aiProcessor.processImage(currentBitmap);
                        Log.d(TAG, "[체크리스트] 전처리 완료: " + processedImage.getWidth() + "x" + processedImage.getHeight());
                    }
                }
            }
        });
    }

    private void showSelectionDialog() {
        String[] options = {"📷 카메라 촬영", "🖼️ 갤러리 불러오기"};
        new AlertDialog.Builder(this)
                .setTitle("사진 가져오기")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) { // 카메라 선택
                        viewFinder.setVisibility(View.VISIBLE);
                        galleryImageView.setVisibility(View.GONE);
                        btnCapture.setText("사진 찍기"); // [문구 변경]
                        if (allPermissionsGranted()) startCamera();
                        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
                    } else { // 갤러리 선택
                        galleryLauncher.launch("image/*");
                    }
                }).show();
    }

    private void processGalleryImage(Uri uri) {
        viewFinder.setVisibility(View.GONE);
        galleryImageView.setVisibility(View.VISIBLE);
        // galleryImageView.setImageURI(uri); // [변경됨: 비트맵 직접 세팅을 위해 주석 처리]

        Log.d(TAG, "선택한 사진 Uri: " + uri.toString()); // [체크리스트] Uri 로그

        try {
            Bitmap bitmap;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(this.getContentResolver(), uri), (decoder, info, source) -> {
                    decoder.setMutableRequired(true); // [변경됨: 비트맵 수정을 위해 추가]
                });
            } else {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
            }

            // [변경됨: 변수에 저장 및 화면 출력]
            currentBitmap = bitmap;
            galleryImageView.setImageBitmap(currentBitmap);
            btnCapture.setText("검사 시작");

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
                    // [변경됨: 촬영된 비트맵 변수에 저장]
                    currentBitmap = bitmap;
                    viewFinder.setVisibility(View.GONE);
                    galleryImageView.setVisibility(View.VISIBLE);
                    galleryImageView.setImageBitmap(currentBitmap); // [변경됨: bitmap 대신 currentBitmap 사용]
                    btnCapture.setText("검사 시작"); // [문구 변경]
                    Log.d(TAG, "카메라 Bitmap 생성 성공: " + currentBitmap.getWidth() + "x" + currentBitmap.getHeight()); // [변경됨: 로그]
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