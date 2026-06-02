package com.capstone.aidetector;

import android.graphics.Bitmap;

// 분석 결과 화면에서 사용할 원본 이미지와 히트맵 이미지를 임시로 보관하는 클래스
public class BitmapHolder {
    public static Bitmap heatmapBitmap = null;
    public static Bitmap originalBitmap = null;
}
