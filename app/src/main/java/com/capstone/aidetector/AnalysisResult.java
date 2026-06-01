package com.capstone.aidetector;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;
import java.io.ByteArrayOutputStream;

// AI 분석 결과인 확률값과 히트맵 이미지를 화면 간 전달하기 위한 Parcelable 데이터 클래스
public class AnalysisResult implements Parcelable {
    public float probability;
    public Bitmap heatmapBitmap;

    public AnalysisResult(float probability, Bitmap heatmapBitmap) {
        this.probability = probability;
        this.heatmapBitmap = heatmapBitmap;
    }

    protected AnalysisResult(Parcel in) {
        // Parcel에 저장된 확률값과 히트맵 이미지를 복원
        probability = in.readFloat();

        byte[] bitmapBytes = in.createByteArray();
        if (bitmapBytes != null && bitmapBytes.length > 0) {
            heatmapBitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
        }
    }

    public static final Creator<AnalysisResult> CREATOR = new Creator<AnalysisResult>() {
        @Override
        public AnalysisResult createFromParcel(Parcel in) {
            return new AnalysisResult(in);
        }

        @Override
        public AnalysisResult[] newArray(int size) {
            return new AnalysisResult[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // 화면 간 전달을 위해 확률값과 히트맵 이미지를 Parcel에 저장
        dest.writeFloat(probability);

        // Bitmap 전달 용량을 줄이기 위해 JPEG byte array로 변환
        if (heatmapBitmap != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            heatmapBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
            byte[] bitmapBytes = stream.toByteArray();
            dest.writeByteArray(bitmapBytes);
        } else {
            dest.writeByteArray(new byte[0]);
        }
    }
}