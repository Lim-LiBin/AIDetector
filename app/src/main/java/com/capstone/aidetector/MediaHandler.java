package com.capstone.aidetector;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Size;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

// 이미지와 영상 파일의 Bitmap 변환, 크기 확인, 임시 저장을 담당하는 유틸리티 클래스
public class MediaHandler {
    private static final String TAG = "MediaHandler";

    // 현재 선택되거나 촬영된 미디어 정보를 임시 보관
    private static Bitmap currentBitmap;
    private static Uri currentUri;

    // 현재 저장된 Bitmap과 Uri 반환
    public static Bitmap getBitmap() { return currentBitmap; }
    public static Uri getUri() { return currentUri; }

    // 현재 선택되거나 촬영된 미디어 정보 저장
    public static void setMedia(Bitmap bitmap, Uri uri) {
        currentBitmap = bitmap;
        currentUri = uri;
    }

    // 선택한 파일이 20MB 이하인지 확인
    public static boolean isSizeValid(Context context, Uri uri) {
        try {
            AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(uri, "r");
            if (afd == null) return false;
            long fileSize = afd.getLength();
            afd.close();
            return fileSize <= 20 * 1024 * 1024;
        } catch (Exception e) { return false; }
    }

    // 이미지 파일은 Bitmap으로 변환하고, 영상 파일은 썸네일 Bitmap으로 변환
    public static Bitmap processBitmap(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        try {
            if (mimeType != null && mimeType.startsWith("video")) {
                // Anroid 10 이상에서는 ContentResolver의 썸네일 로드 기능 사용
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return context.getContentResolver().loadThumbnail(uri, new Size(640, 480), null);
                }
            } else {
                // Android 버전에 따라 이미지 Bitmap 변환 방식 구분
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(), uri));
                } else {
                    return MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
        return null;
    }

    // 카메라로 촬영한 Bitmap을 냅두 캐시 파일로 임시 저장
    public static Uri saveBitmapToInternal(Context context, Bitmap bitmap) {
        File tempFile = new File(context.getCacheDir(), "capture_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            return Uri.fromFile(tempFile);
        } catch (Exception e) { return null; }
    }
}