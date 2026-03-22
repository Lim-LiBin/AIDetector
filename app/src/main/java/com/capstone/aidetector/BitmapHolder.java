package com.capstone.aidetector;

import android.graphics.Bitmap;

/**
 * [v] Transaction too large 에러 방지용 보관소
 * Intent에 비트맵을 직접 넣으면 앱이 튕기기 때문에,
 * 잠시 이 static 변수에 담아서 ResultActivity로 전달합니다.
 */
public class BitmapHolder {
    // 히트맵 이미지를 임시로 담아둘 정적 변수
    public static Bitmap heatmapBitmap = null;
    public static Bitmap originalBitmap = null;
}