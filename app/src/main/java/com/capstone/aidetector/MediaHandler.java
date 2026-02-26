package com.capstone.aidetector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.database.Cursor;
import java.io.IOException;

public class MediaHandler {
    private Bitmap currentBitmap;
    private Uri currentUri;

    // [isSizeValid()] 파일이 20MB를 초과하는지 검사 (설계 반영)
    public boolean isSizeValid(Context context, Uri uri) {
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            long size = cursor.getLong(sizeIndex);
            cursor.close();
            // 20MB = 20 * 1024 * 1024 bytes
            return size <= 20 * 1024 * 1024;
        }
        return false;
    }

    // [processBitmap()] URI를 실제 Bitmap으로 변환 (설계 반영)
    public Bitmap processBitmap(Context context, Uri uri) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.Source source = ImageDecoder.createSource(context.getContentResolver(), uri);
            currentBitmap = ImageDecoder.decodeBitmap(source);
        } else {
            currentBitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
        }
        currentUri = uri;
        return currentBitmap;
    }

    // [getBitmap() / getUri()] 데이터 반환 (설계 반영)
    public Bitmap getBitmap() { return currentBitmap; }
    public Uri getUri() { return currentUri; }
}