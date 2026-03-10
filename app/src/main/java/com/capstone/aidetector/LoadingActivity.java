package com.capstone.aidetector;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class LoadingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        TextView tvStepText = findViewById(R.id.tv_step_text);

        View step1 = findViewById(R.id.step1);
        View step2 = findViewById(R.id.step2);
        View step3 = findViewById(R.id.step3);

        // 0에서 100까지 3초(3000ms) 동안 진행하면서 단계 변화를 시뮬레이션
        ValueAnimator animator = ValueAnimator.ofInt(0, 100);
        animator.setDuration(3000);
        animator.addUpdateListener(animation -> {
            int progress = (int) animation.getAnimatedValue();

            // 구간별로 동그라미 색상과 텍스트 변경
            if (progress < 33) {
                // 1단계: 첫 번째 빨간불 ON, 나머지 OFF
                step1.setBackgroundResource(R.drawable.step_active);
                step1.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF5E62"))); // 네온 핑크(빨강)

                step2.setBackgroundResource(R.drawable.step_inactive);
                step2.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#333333"))); // 꺼짐(다크 그레이)

                step3.setBackgroundResource(R.drawable.step_inactive);
                step3.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#333333"))); // 꺼짐

                tvStepText.setText("사진을 읽고 있음...");

            } else if (progress < 66) {
                // 2단계: 두 번째 노란불 ON, 나머지 OFF
                step1.setBackgroundResource(R.drawable.step_inactive);
                step1.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#333333"))); // 꺼짐

                step2.setBackgroundResource(R.drawable.step_active);
                step2.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFEA00"))); // 네온 옐로우

                step3.setBackgroundResource(R.drawable.step_inactive);
                step3.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#333333"))); // 꺼짐

                tvStepText.setText("AI가 판별 중...");

            } else {
                // 3단계: 세 번째 초록불 ON, 나머지 OFF
                step1.setBackgroundResource(R.drawable.step_inactive);
                step1.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#333333"))); // 꺼짐

                step2.setBackgroundResource(R.drawable.step_inactive);
                step2.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#333333"))); // 꺼짐

                step3.setBackgroundResource(R.drawable.step_active);
                step3.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#00FF7F"))); // 네온 그린

                tvStepText.setText("결과 화면 준비 중...");
            }
        });

        // 100이 되면 결과 화면으로 데이터 넘기고 이동!
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Intent currentIntent = getIntent();
                Intent nextIntent = new Intent(LoadingActivity.this, ResultActivity.class);

                // 받은 데이터를 그대로 ResultActivity로 전달
                if (currentIntent != null && currentIntent.getExtras() != null) {
                    nextIntent.putExtras(currentIntent.getExtras());
                }
                startActivity(nextIntent);
                finish();
            }
        });

        // 애니메이션 시작
        animator.start();
    }
}