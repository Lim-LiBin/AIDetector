package com.capstone.aidetector;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
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

    // [v3 모델 구조 반영]
    // 코랩 분석 결과에 따라 인덱스가 바뀔 수 있으니 로그를 꼭 확인하세요.
    private static final int OUTPUT_INDEX_HEATMAP = 0; // 4D 텐서 [1, 7, 7, 1280]가 0번임
    private static final int OUTPUT_INDEX_SCORE = 1;   // 2D 텐서 [1, 1]가 1번임

    public AiProcessor(Context context) {
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4); // CPU 가속 설정

            /* GPU 가속(NNAPI)이 필요한 경우 아래 주석 해제
            NnApiDelegate.Options nnApiOptions = new NnApiDelegate.Options();
            nnApiDelegate = new NnApiDelegate(nnApiOptions);
            options.addDelegate(nnApiDelegate);
            */

            MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_PATH);
            interpreter = new Interpreter(modelBuffer, options);

            Log.d(TAG, "모델 로드 성공: " + MODEL_PATH);
        } catch (Exception e) {
            Log.e(TAG, "모델 초기화 실패: " + e.getMessage());
        }
    }

    private MappedByteBuffer loadModelFile(Context context, String modelName) throws Exception {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
    }

    /**
     * [핵심 수정] 파이썬 ImageDataGenerator(rescale=1./255)와 동일하게 세팅
     */
    // AiProcessor.java 내 processImage 수정
    public TensorImage processImage(Bitmap bitmap) {
        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                // 1. 먼저 리사이징 (PIL과 최대한 비슷한 알고리즘)
                .add(new ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                // 2. 그 다음 정규화
                .add(new NormalizeOp(0.0f, 255.0f))
                .build();

        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        // ⭐️ Bitmap을 로드할 때 ARGB_8888 형식을 유지하는지 확인
        tensorImage.load(bitmap);
        return imageProcessor.process(tensorImage);
    }

    public Map<String, Object> runInference(TensorImage tensorImage) {
        if (interpreter == null || tensorImage == null) return null;

        // 출력 버퍼 할당 (v3 모델 형태)
        float[][] scoreBuffer = new float[1][1];
        float[][][][] heatmapBuffer = new float[1][7][7][1280];

        Object[] inputs = { tensorImage.getBuffer() };
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(OUTPUT_INDEX_SCORE, scoreBuffer);
        outputs.put(OUTPUT_INDEX_HEATMAP, heatmapBuffer);

        try {
            interpreter.runForMultipleInputsOutputs(inputs, outputs);

            float rawScore = scoreBuffer[0][0];

            // [히트맵 가공] 파이썬 분석 코드와 동일하게 채널 평균(Mean) 방식 적용
            float[][] processedHeatmap = new float[7][7];
            for (int i = 0; i < 7; i++) {
                for (int j = 0; j < 7; j++) {
                    float sum = 0;
                    for (int c = 0; c < 1280; c++) {
                        sum += heatmapBuffer[0][i][j][c];
                    }
                    // 1280개 채널의 평균값을 계산
                    processedHeatmap[i][j] = sum / 1280.0f;
                }
            }

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("score", rawScore);     // 0~1 사이의 확률값
            resultMap.put("heatmap", processedHeatmap); // 7x7 데이터

            Log.d(TAG, "추론 성공 - Score: " + rawScore);
            return resultMap;

        } catch (Exception e) {
            Log.e(TAG, "추론 중 오류 발생: " + e.getMessage());
            return null;
        }
    }

    public void close() {
        if (interpreter != null) interpreter.close();
        if (nnApiDelegate != null) nnApiDelegate.close();
    }
}