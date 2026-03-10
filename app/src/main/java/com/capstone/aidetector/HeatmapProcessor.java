package com.capstone.aidetector;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class HeatmapProcessor {
    // [v] HeatmapProcessor의 createHeatmapImage 메서드를 호출하여 7x7 행렬 전달 및 비트맵 수신
    public Bitmap createHeatmapImage(float[][] heatmapMatrix) {
        int size = 224;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        int cellSize = size / 7;

        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                float value = heatmapMatrix[i][j];
                // 조작 확률이 높을수록(값이 클수록) 더 진한 빨간색으로 칠함
                int alpha = (int) (Math.min(value * 255, 160));
                paint.setColor(Color.argb(alpha, 255, 0, 0));

                canvas.drawRect(j * cellSize, i * cellSize, (j + 1) * cellSize, (i + 1) * cellSize, paint);
            }
        }
        return bitmap;
    }
}