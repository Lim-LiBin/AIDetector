package com.capstone.aidetector;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;
import java.io.ByteArrayOutputStream;

public class AnalysisResult implements Parcelable {
    public float probability;
    public Bitmap heatmapBitmap;

    public AnalysisResult(float probability, Bitmap heatmapBitmap) {
        this.probability = probability;
        this.heatmapBitmap = heatmapBitmap;
    }

    protected AnalysisResult(Parcel in) {
        probability = in.readFloat();

        // ✅ byte array로 읽어서 Bitmap으로 변환
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
        dest.writeFloat(probability);

        // ✅ Bitmap을 JPEG로 압축해서 byte array로 전달
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