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

public class CameraHandler {
    private static final String TAG = "CameraHandler";
    private ImageCapture imageCapture;
    private final Context context;
    private final PreviewView viewFinder;

    public CameraHandler(Context context, PreviewView viewFinder) {
        this.context = context;
        this.viewFinder = viewFinder;
    }

    // 카메라 시작 로직
    public void startCamera(LifecycleOwner lifecycleOwner) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (Exception e) {
                Log.e(TAG, "카메라 시작 실패", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    // 사진 촬영 로직
    public void takePhoto(OnPhotoCapturedListener listener) {
        if (imageCapture == null) return;

        imageCapture.takePicture(ContextCompat.getMainExecutor(context), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                // 1. 비트맵 변환 및 회전 보정
                Bitmap bitmap = rotateImageIfNeeded(image);

                // 2. 파일 저장 및 MediaHandler 세팅
                Uri uri = MediaHandler.saveBitmapToInternal(context, bitmap);
                MediaHandler.setMedia(bitmap, uri);

                // 3. UI 업데이트를 위해 결과를 MainActivity로 알려줌
                listener.onCaptured(bitmap, uri);
                image.close();
            }

            @Override
            public void onError(@NonNull ImageCaptureException e) {
                Log.e(TAG, "촬영 실패", e);
            }
        });
    }

    // [회전 보정 유틸리티]
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

    // 결과를 돌려주기 위한 인터페이스
    public interface OnPhotoCapturedListener {
        void onCaptured(Bitmap bitmap, Uri uri);
    }
}