package com.capstone.aidetector;

import android.graphics.Bitmap;

public class BitmapHolder {
    // 히트맵 비트맵 보관
    public static Bitmap heatmapBitmap = null;
    
    // ⭐️ 추가: 원본 비트맵 보관 (Intent 용량 제한 회피용)
    public static Bitmap originalBitmap = null;
}
