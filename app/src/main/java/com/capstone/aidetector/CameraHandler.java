package com.capstone.aidetector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;

// CameraX를 이용한 카메라 프리뷰 출력 및 사진 촬영 처리 클래스
public class CameraHandler {
    private static final String TAG = "CameraHandler";
    private ImageCapture imageCapture;
    private final Context context;
    private final PreviewView viewFinder;

    public CameraHandler(Context context, PreviewView viewFinder) {
        this.context = context;
        this.viewFinder = viewFinder;
    }

    // 카메라 프리뷰와 이미지 캡처 기능을 초기화
    public void startCamera(LifecycleOwner lifecycleOwner) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                // Preview와 ImageCapture를 카메라 생명주기에 바인딩
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (Exception e) {
                Log.e(TAG, "카메라 시작 실패", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    // 사진을 촬영하고 Bitmap과 Uri 형태로 결과를 전달
    public void takePhoto(OnPhotoCapturedListener listener) {
        if (imageCapture == null) return;

        imageCapture.takePicture(ContextCompat.getMainExecutor(context), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                // 촬영 이미지를 Bitmap으로 변환하고 회전 정보 반영
                Bitmap bitmap = rotateImageIfNeeded(image);

                // 내부 저장소에 저장한 뒤 현재 미디어 정보 갱신
                Uri uri = MediaHandler.saveBitmapToInternal(context, bitmap);
                MediaHandler.setMedia(bitmap, uri);

                // 촬영 결과를 호출한 화면으로 전달
                listener.onCaptured(bitmap, uri);
                image.close();
            }

            @Override
            public void onError(@NonNull ImageCaptureException e) {
                Log.e(TAG, "촬영 실패", e);
            }
        });
    }

    // ImageProxy를 Bitmap으로 변환하고 회전 정보를 반영
    private Bitmap rotateImageIfNeeded(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        int rotation = image.getImageInfo().getRotationDegrees();
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }
        return bitmap;
    }

    // 촬영 결과 전달용 콜백 인터페이스
    public interface OnPhotoCapturedListener {
        void onCaptured(Bitmap bitmap, Uri uri);
    }
}