package com.capstone.aidetector;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class ResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        TextView tvResultScore = findViewById(R.id.tvResultScore);
        TextView tvResultStatus = findViewById(R.id.tvResultStatus);
        ImageView ivHeatmapOverlay = findViewById(R.id.ivHeatmapOverlay);

        // activity_result.xml의 버튼 id를 btnBack으로 맞췄는지 확인하세요.
        Button btnBack = findViewById(R.id.btnBack);

        // [v] Intent를 통해 AiProcessor가 만든 원본 데이터 수신
        AnalysisResult result = getIntent().getParcelableExtra("analysis_result");

        if (result != null) {
            // 🔥 AiProcessor에서 전달받은 원본 확률값 (0.0 ~ 1.0)
            float score = result.probability;
            Bitmap heatmap = result.heatmapBitmap;

            // 1. 점수 표시: 모델이 준 소수점 데이터를 100배 하여 퍼센트로 출력
            // 소수점 둘째 자리까지 표시하여 아주 미세한 수치도 확인할 수 있게 함
            float percentValue = score * 100;
            tvResultScore.setText(String.format("%.2f%%", percentValue));

            // 2. 판독 상태 표시: 표준 규격 (0.5 이상이면 FAKE, 미만이면 REAL)
            // 모델이 가짜 사진을 넣었을 때 0.000...을 준다면 여기서 REAL로 표시되는 것이 "정직한" 출력입니다.
            if (score > 0.5f) {
                tvResultStatus.setText("분석 결과: FAKE (조작 의심)");
                tvResultStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                tvResultScore.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            } else {
                tvResultStatus.setText("분석 결과: REAL (정상 이미지)");
                tvResultStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                tvResultScore.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            }

            // 3. 히트맵 이미지 표시
            if (heatmap != null) {
                ivHeatmapOverlay.setImageBitmap(heatmap);
            }
        }

        // 다시 검사하기 버튼 동작
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }
}