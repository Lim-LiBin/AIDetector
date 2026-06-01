package com.capstone.aidetector;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

// 히트맵 행렬 데이터를 Bitmap 이미지로 변환하는 클래스
public class HeatmapProcessor {
    private static final int MATRIX_SIZE = 7;

    // 7x7 히트맵 행렬을 컬러 Bitmap으로 변환
    public Bitmap createRawHeatmap(float[][] matrix) {
        Bitmap smallBitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < MATRIX_SIZE; y++) {
            for (int x = 0; x < MATRIX_SIZE; x++) {
                float value = matrix[y][x];

                // 히트맵 값에 따라 빨간색 투명도 조절
                int alpha = (int) (value * 300);
                int color = Color.argb(alpha, 255, 0, 0);

                smallBitmap.setPixel(x, y, color);
            }
        }

        return smallBitmap;
    }

    //얼굴 영역의 위치와 크기에 맞춘 히트맵 오버레이 생성
    public Bitmap createAlignedHeatmapImage(float[][] matrix, int fullWidth, int fullHeight, int x1, int y1, int cropW, int cropH) {
        Bitmap rawHeatmap = createRawHeatmap(matrix);

        // 7x7 히트맵을 얼굴 영역 크기에 맞게 확대
        Bitmap faceHeatmap = Bitmap.createScaledBitmap(rawHeatmap, cropW, cropH, true);

        // 원본 이미지 크기의 투명 오버레이 생성
        Bitmap fullSizeOverlay = Bitmap.createBitmap(fullWidth, fullHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(fullSizeOverlay);

        // 얼굴 위치에 히트맵 배치
        canvas.drawBitmap(faceHeatmap, x1, y1, null);

        rawHeatmap.recycle();
        faceHeatmap.recycle();

        return fullSizeOverlay;
    }

    // 원본 이미지 크기에 맞는 히트맵 오버레이 이미지 생성
    public Bitmap createHeatmapImage(float[][] heatmap, int fullWidth, int fullHeight, int x1, int y1, int cropW, int cropH) {
        Bitmap raw = createRawHeatmap(heatmap);

        // 7x7 히트맵을 분석 영역 크기에 맞게 확대
        Bitmap faceHeatmap = Bitmap.createScaledBitmap(raw, cropW, cropH, true);
        raw.recycle();

        // 원본 이미지 크기의 Bitmap을 생성한 뒤 히트맵을 지정 위치에 합성
        Bitmap fullSizeBitmap = Bitmap.createBitmap(fullWidth, fullHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(fullSizeBitmap);

        canvas.drawBitmap(faceHeatmap, x1, y1, null);

        faceHeatmap.recycle();
        return fullSizeBitmap;
    }
}
