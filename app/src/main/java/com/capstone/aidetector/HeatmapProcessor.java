package com.capstone.aidetector;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

public class HeatmapProcessor {
    //원본 이미지 및 전처리 규격과 동일하게 설정
    private static final int TARGET_SIZE = 224;
    private static final int MATRIX_SIZE = 7;

    //7x7 행렬을 컬러 비트맵으로 변환하는 함수
    public Bitmap createRawHeatmap(float[][] matrix) {
        //7x7 크기의 비트맵 생성
        Bitmap smallBitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < MATRIX_SIZE; y++) {
            for (int x = 0; x < MATRIX_SIZE; x++) {
                float value = matrix[y][x]; //0.0~1.0 사이의 값

                //수치에 맞는 색상 추출 로직
                int alpha = (int) (value * 300); // (value * 200);
                int color = Color.argb(alpha, 255, 0, 0);

                smallBitmap.setPixel(x, y, color);
            }
        }

        //7x7 데이터를 224x224 크기로 키우기
        //Bitmap finalBitmap = Bitmap.createScaledBitmap(smallBitmap, TARGET_SIZE, TARGET_SIZE, true);

        return smallBitmap;
    }

    //얼굴 부위에만 히트맵
    public Bitmap createAlignedHeatmapImage(float[][] matrix, int fullWidth, int fullHeight, int x1, int y1, int cropW, int cropH) {
        Bitmap rawHeatmap = createRawHeatmap(matrix);
        Bitmap faceHeatmap = Bitmap.createScaledBitmap(rawHeatmap, cropW, cropH, true);

        Bitmap fullSizeOverlay = Bitmap.createBitmap(fullWidth, fullHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(fullSizeOverlay);

        canvas.drawBitmap(faceHeatmap, x1, y1, null);

        rawHeatmap.recycle();
        faceHeatmap.recycle();

        return fullSizeOverlay;
    }

    public Bitmap createHeatmapImage(float[][] heatmap, int targetWidth, int targetHeight) {
        // 1. 먼저 224x224 히트맵 생성
        Bitmap raw = createRawHeatmap(heatmap);

        // 2. 원본 크기로 리사이즈
        /*Bitmap resizedHeatmap = Bitmap.createScaledBitmap(
                heatmap224,
                targetWidth,
                targetHeight,
                true  // 부드러운 필터링
        );*/

        Bitmap res = Bitmap.createScaledBitmap(raw, targetWidth, targetHeight, true);
        raw.recycle();
        return res;
    }
}
