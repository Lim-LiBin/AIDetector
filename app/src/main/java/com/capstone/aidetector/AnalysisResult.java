package com.capstone.aidetector;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

public class AnalysisResult implements Parcelable {
    public float probability;    //확률값
    public Bitmap heatmapBitmap;  //히트맵 비트맵(이미지)

    public AnalysisResult(float probability, Bitmap heatmapBitmap) {
        this.probability = probability;
        this.heatmapBitmap = heatmapBitmap;
    }

    // Parcelable 구현부 (Intent 전송을 위해 필수)
    protected AnalysisResult(Parcel in) {
        probability = in.readFloat();
        heatmapBitmap = in.readParcelable(Bitmap.class.getClassLoader());
    }

    public static final Creator<AnalysisResult> CREATOR = new Creator<AnalysisResult>() {
        @Override
        public AnalysisResult createFromParcel(Parcel in) { return new AnalysisResult(in); }
        @Override
        public AnalysisResult[] newArray(int size) { return new AnalysisResult[size]; }
    };

    @Override
    public int describeContents() { return 0; }
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(probability);
        dest.writeParcelable(heatmapBitmap, flags);
    }
}