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

// 딥페이크 탐지 TFLite 모델 로드, 이미지 전처리, 추론 결과 생성을 담당하는 클래스
public class AiProcessor {
    private static final String TAG = "AiProcessor";
    private Interpreter interpreter;
    private NnApiDelegate nnApiDelegate;
    private static final String MODEL_PATH = "model.tflite";

    // 모델의 다중 출력 인덱스
    private static final int OUTPUT_INDEX_HEATMAP = 0; // 4D 텐서 [1, 7, 7, 1280]
    private static final int OUTPUT_INDEX_SCORE = 1;   // 2D 텐서 [1, 1]

    public AiProcessor(Context context) {
        try {
            // 모델 실행 옵션 설정 및 Interpreter 초기화
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);

            MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_PATH);
            interpreter = new Interpreter(modelBuffer, options);

            Log.d(TAG, "모델 로드 성공: " + MODEL_PATH);
        } catch (Exception e) {
            Log.e(TAG, "모델 초기화 실패: " + e.getMessage());
        }
    }

    // assets 폴더의 TFlite 모델 파일을 메모리 매핑 방식으로 로드
    private MappedByteBuffer loadModelFile(Context context, String modelName) throws Exception {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
    }

    // Bitmap을 모델 입력 크기와 정규화 방식에 전처리
    public TensorImage processImage(Bitmap bitmap) {
        // 모델 입력 형식에 맞게 Bitmap 포맷을 ARGB_8888로 통일
        Bitmap argbBitmap = bitmap.getConfig() == Bitmap.Config.ARGB_8888
                ? bitmap
                : bitmap.copy(Bitmap.Config.ARGB_8888, true);

        // 입력 이미지를 224x224로 리사이징하고 픽셀 값을 0~1 범위로 정규화
        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                .add(new NormalizeOp(0.0f, 255.0f))
                .build();

        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(argbBitmap);
        return imageProcessor.process(tensorImage);
    }

    // 전처리된 텐서 이미지로 추론 실행하고 확률값과 히트맵 데이터를 반환
    public Map<String, Object> runInference(TensorImage tensorImage) {
        if (interpreter == null || tensorImage == null) return null;

        // 모델 출력 구조에 맞는 버퍼 생성
        float[][] scoreBuffer = new float[1][1];
        float[][][][] heatmapBuffer = new float[1][7][7][1280];

        Object[] inputs = { tensorImage.getBuffer() };
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(OUTPUT_INDEX_SCORE, scoreBuffer);
        outputs.put(OUTPUT_INDEX_HEATMAP, heatmapBuffer);

        try {
            // 모델 추론 실행
            interpreter.runForMultipleInputsOutputs(inputs, outputs);

            float rawScore = scoreBuffer[0][0];

            // 특징맵 채널 평균을 이용해 7x7 히트맵 데이터 생성
            float[][] processedHeatmap = new float[7][7];
            for (int i = 0; i < 7; i++) {
                for (int j = 0; j < 7; j++) {
                    float sum = 0;
                    for (int c = 0; c < 1280; c++) {
                        sum += heatmapBuffer[0][i][j][c];
                    }
                    processedHeatmap[i][j] = sum / 1280.0f;
                }
            }

            // 추론 결과를 화면에서 사용하기 쉬운 형태로 저장
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("score", rawScore);
            resultMap.put("heatmap", processedHeatmap);

            Log.d(TAG, "추론 성공 - Score: " + rawScore);
            return resultMap;

        } catch (Exception e) {
            Log.e(TAG, "추론 중 오류 발생: " + e.getMessage());
            return null;
        }
    }

    //모델 사용 후 자원 해제
    public void close() {
        if (interpreter != null) interpreter.close();
        if (nnApiDelegate != null) nnApiDelegate.close();
    }
}