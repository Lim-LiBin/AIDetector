package com.capstone.aidetector;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.util.ArrayList; // [추가]
import java.util.List;      // [추가]

public class AiProcessor {
    private static final String TAG = "AiProcessor";

    // [변경됨: void에서 TensorImage로 반환 타입 변경]
    public TensorImage processImage(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "전처리할 비트맵이 null입니다.");
            return null;
        }

        try {
            // [수정] 비트맵 설정이 HARDWARE인 경우 TFLite가 로드하지 못하므로 소프트웨어 비트맵으로 변환
            Bitmap softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

            ImageProcessor imageProcessor = new ImageProcessor.Builder()
                    .add(new ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                    .add(new NormalizeOp(0.0f, 255.0f)) // 0~1 사이로 정규화
                    .build();

            TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
            tensorImage.load(softwareBitmap); // 변환된 비트맵 로드

            tensorImage = imageProcessor.process(tensorImage);

            Log.d(TAG, "[이미지 전처리 완료] Tensor 변환 성공: " +
                    tensorImage.getWidth() + "x" + tensorImage.getHeight());

            return tensorImage;

        } catch (Exception e) {
            // 여기서 에러가 발생하면 null을 반환함
            Log.e(TAG, "이미지 전처리 중 오류 발생: " + e.getMessage());
            return null;
        }
    }

    // 영상 분석의 경우 여러 프레임의 결과가 나오므로 List 형태로 반환하도록 구성하면 좋습니다.
    public List<TensorImage> processVideo(Context context, Uri videoUri) {
        if (videoUri == null) return null;

        List<TensorImage> processedFrames = new ArrayList<>(); // [추가]
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        try {
            retriever.setDataSource(context, videoUri);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = (durationStr != null) ? Long.parseLong(durationStr) : 0;

            for (long i = 0; i < durationMs; i += 1000) {
                // 1초 단위로 프레임 추출
                Bitmap frame = retriever.getFrameAtTime(i * 1000, MediaMetadataRetriever.OPTION_CLOSEST);
                if (frame != null) {
                    // [변경됨: 위에서 수정한 processImage를 호출하여 리스트에 저장]
                    TensorImage ti = processImage(frame);
                    if (ti != null) {
                        processedFrames.add(ti);
                        Log.d(TAG, "[영상 분석 중] " + (i / 1000) + "초 지점 추출 및 전처리 완료");
                    }
                }
            }
            return processedFrames; // [추가]
        } catch (Exception e) {
            Log.e(TAG, "영상 분석 중 오류 발생: " + e.getMessage());
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }
}