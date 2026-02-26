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

public class MediaHandler {
    private static final String TAG = "MediaHandler";

    // 공용 데이터 공간
    private static Bitmap currentBitmap;
    private static Uri currentUri;

    // 데이터 전달 함수
    public static Bitmap getBitmap() { return currentBitmap; }
    public static Uri getUri() { return currentUri; }

    // 데이터를 업데이트할 때 사용하는 함수
    public static void setMedia(Bitmap bitmap, Uri uri) {
        currentBitmap = bitmap;
        currentUri = uri;
    }

    // 20MB 용량 체크
    public static boolean isSizeValid(Context context, Uri uri) {
        try {
            AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(uri, "r");
            if (afd == null) return false;
            long fileSize = afd.getLength();
            afd.close();
            return fileSize <= 20 * 1024 * 1024;
        } catch (Exception e) { return false; }
    }

    // 사진/영상 비트맵 변환 및 썸네일 추출
    public static Bitmap processBitmap(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        try {
            if (mimeType != null && mimeType.startsWith("video")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return context.getContentResolver().loadThumbnail(uri, new Size(640, 480), null);
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(), uri));
                } else {
                    return MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
        return null;
    }

    // 카메라 촬영본 임시 저장
    public static Uri saveBitmapToInternal(Context context, Bitmap bitmap) {
        File tempFile = new File(context.getCacheDir(), "capture_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            return Uri.fromFile(tempFile);
        } catch (Exception e) { return null; }
    }
}