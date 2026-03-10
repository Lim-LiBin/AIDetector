package com.capstone.aidetector;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

public class AiProcessor {
    private static final String TAG = "AiProcessor";
    private Interpreter interpreter;
    private NnApiDelegate nnApiDelegate;
    private static final String MODEL_PATH = "model.tflite";

    public AiProcessor(Context context) {
        try {
            // [x] NNAPI 가속 설정 (추론 속도 1.0s 이내 목표)
            NnApiDelegate.Options nnApiOptions = new NnApiDelegate.Options();
            nnApiDelegate = new NnApiDelegate(nnApiOptions);

            Interpreter.Options options = new Interpreter.Options();
            options.addDelegate(nnApiDelegate);
            options.setNumThreads(4);

            // [x] TFLite 모델 로드 및 초기화
            MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_PATH);
            interpreter = new Interpreter(modelBuffer, options);

            // [x] 로그: [모델 로드 완료] Interpreter 및 NNAPI 설정 성공
            Log.d(TAG, "[모델 로드 완료] Interpreter 및 NNAPI 설정 성공");
        } catch (Exception e) {
            Log.e(TAG, "모델 로드 실패: " + e.getMessage());
        }
    }

    private MappedByteBuffer loadModelFile(Context context, String modelName) throws Exception {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
    }

    // [x] 다중 출력 추론 구현
    public Map<String, Object> runInference(TensorImage tensorImage) {
        if (interpreter == null || tensorImage == null) return null;

        // [x] 출력 버퍼 준비 (확률값: float[1][1], 히트맵: float[1][7][7][1280])
        float[][] probability = new float[1][1];
        float[][][][] heatmapMatrix = new float[1][7][7][1280];

        Object[] inputs = { tensorImage.getBuffer() };
        Map<Integer, Object> outputs = new HashMap<>();

        // 모델 인덱스 매핑 (0: 히트맵, 1: 확률값)
        outputs.put(0, heatmapMatrix);
        outputs.put(1, probability);

        try {
            long startTime = SystemClock.uptimeMillis();
            // [x] 추론 실행
            interpreter.runForMultipleInputsOutputs(inputs, outputs);
            long endTime = SystemClock.uptimeMillis();

            // [x] 데이터 표준 규격 추출
            float finalScore = probability[0][0]; // 0.0 ~ 1.0 사이의 실수

            // 히트맵 행렬 가공 (7x7)
            float[][] finalHeatmap = new float[7][7];
            for (int i = 0; i < 7; i++) {
                for (int j = 0; j < 7; j++) {
                    finalHeatmap[i][j] = heatmapMatrix[0][i][j][0];
                }
            }

            Log.i(TAG, "추론 시간: " + (endTime - startTime) + "ms / 결과 점수: " + finalScore);

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("score", finalScore);
            resultMap.put("heatmap", finalHeatmap);
            return resultMap;

        } catch (Exception e) {
            Log.e(TAG, "추론 오류: " + e.getMessage());
            return null;
        }
    }

    /**
     * 파이썬 MobileNetV2 preprocess_input 규격 구현
     */
    public TensorImage processImage(Bitmap bitmap) {
        //
        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                // 🔥 파이썬 전처리 핵심: (x / 127.5) - 1.0 => NormalizeOp(127.5f, 127.5f)
                // 이 설정이 되어야 가짜 사진에서 0.8 이상의 높은 점수가 나옵니다.
                .add(new NormalizeOp(127.5f, 127.5f))
                .build();

        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(bitmap);
        return imageProcessor.process(tensorImage);
    }

    public void close() {
        if (interpreter != null) interpreter.close();
        if (nnApiDelegate != null) nnApiDelegate.close();
    }
}