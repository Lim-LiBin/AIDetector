package com.capstone.aidetector;

import android.graphics.Bitmap;
import android.graphics.Color;

public class HeatmapProcessor {
    //원본 이미지 및 전처리 규격과 동일하게 설정
    private static final int TARGET_SIZE = 224;
    private static final int MATRIX_SIZE = 7;

    //7x7 행렬을 컬러 비트맵으로 변환하는 함수
    public Bitmap createHeatmapImage(float[][] matrix) {
        //7x7 크기의 비트맵 생성
        Bitmap smallBitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < MATRIX_SIZE; y++) {
            for (int x = 0; x < MATRIX_SIZE; x++) {
                float value = matrix[y][x]; //0.0~1.0 사이의 값

                //수치에 맞는 색상 추출 로직
                int alpha = (int) (value * 200);
                int color = Color.argb(alpha, 255, 0, 0);

                smallBitmap.setPixel(x, y, color);
            }
        }

        //7x7 데이터를 224x224 크기로 키우기
        Bitmap finalBitmap = Bitmap.createScaledBitmap(smallBitmap, TARGET_SIZE, TARGET_SIZE, true);

        return finalBitmap;
    }

    public Bitmap createHeatmapImage(float[][] heatmap, int targetWidth, int targetHeight) {
        // 1. 먼저 224x224 히트맵 생성
        Bitmap heatmap224 = createHeatmapImage(heatmap);

        // 2. 원본 크기로 리사이즈
        Bitmap resizedHeatmap = Bitmap.createScaledBitmap(
                heatmap224,
                targetWidth,
                targetHeight,
                true  // 부드러운 필터링
        );

        return resizedHeatmap;
    }
}
