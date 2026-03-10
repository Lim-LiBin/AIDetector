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
            // [v] NnApiDelegate를 연동하여 가속 설정
            NnApiDelegate.Options nnApiOptions = new NnApiDelegate.Options();
            nnApiDelegate = new NnApiDelegate(nnApiOptions);

            Interpreter.Options options = new Interpreter.Options();
            options.addDelegate(nnApiDelegate);
            options.setNumThreads(4);

            // [v] 모델 로드 및 초기화
            MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_PATH);
            interpreter = new Interpreter(modelBuffer, options);

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

    /**
     * 다중 출력 추론 실행
     * 결과: score(확률값), heatmap(7x7 행렬)
     */
    public Map<String, Object> runInference(TensorImage tensorImage) {
        if (interpreter == null || tensorImage == null) return null;

        // 1. 출력 버퍼 준비
        float[][] probability = new float[1][1];
        float[][][][] heatmapMatrix = new float[1][7][7][1280]; // 1280개 채널!

        Object[] inputs = { tensorImage.getBuffer() };
        Map<Integer, Object> outputs = new HashMap<>();

        outputs.put(0, heatmapMatrix); // 모델에 따라 인덱스가 다를 수 있으니 확인 필수!
        outputs.put(1, probability);

        try {
            interpreter.runForMultipleInputsOutputs(inputs, outputs);

            float finalScore = probability[0][0];

            // 2. 히트맵 가공: 1280개 채널의 평균값 구하기
            float[][] finalHeatmap = new float[7][7];
            for (int i = 0; i < 7; i++) {
                for (int j = 0; j < 7; j++) {
                    float sum = 0;
                    for (int c = 0; c < 1280; c++) {
                        sum += heatmapMatrix[0][i][j][c]; // 모든 채널을 다 더함
                    }
                    finalHeatmap[i][j] = sum / 1280f; // 평균값 저장
                }
            }

            //HeatmapProcessor heatmapProcessor = new HeatmapProcessor();
            //Bitmap heatmapBitmap = heatmapProcessor.createHeatmapImage(finalHeatmap);

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
     * 이미지 전처리 (가장 중요한 부분)
     */
    public TensorImage processImage(Bitmap bitmap) {
        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                // 0.0 ~ 1.0 범위로 학습 코드와 일치시킴
                .add(new NormalizeOp(0.0f, 255.0f))
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