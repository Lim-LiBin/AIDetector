package com.capstone.aidetector; // 패키지명은 프로젝트에 맞게 확인해 주세요.

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class ResultActivity extends AppCompatActivity {

    private TextView tvResultText;
    private ProgressBar pbResultGauge;
    private ImageView ivOriginalImage;
    private ImageView ivHeatmapImage;
    private SeekBar sbOpacitySlider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        initViews();
        setupToolbar();
        receiveAndSetData();
        setupSlider();
    }

    private void initViews() {
        tvResultText = findViewById(R.id.tv_result_text);
        pbResultGauge = findViewById(R.id.pb_result_gauge);
        ivOriginalImage = findViewById(R.id.iv_original_image);
        ivHeatmapImage = findViewById(R.id.iv_heatmap_image);
        sbOpacitySlider = findViewById(R.id.sb_opacity_slider);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_result); // 만들어둔 메뉴 파일 연결

        // [수정된 부분] 툴바 안에서 직접 메뉴 아이템을 찾아 글씨 속성 변경
        Menu menu = toolbar.getMenu();
        MenuItem deleteItem = menu.findItem(R.id.action_delete);
        if (deleteItem != null) {
            SpannableString s = new SpannableString(deleteItem.getTitle());
            s.setSpan(new ForegroundColorSpan(Color.parseColor("#000000")), 0, s.length(), 0); // 검은색
            s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), 0); // 굵은 글씨
            deleteItem.setTitle(s);
        }

        toolbar.setNavigationOnClickListener(v -> finish());

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_delete) {
                showDeleteConfirmDialog();
                return true;
            }
            return false;
        });
    }

    private void receiveAndSetData() {
        Intent intent = getIntent();
        if (intent != null) {

            // 1. 원본 이미지 세팅
            if (intent.hasExtra("original_image_uri")) {
                String uriString = intent.getStringExtra("original_image_uri");
                Uri imageUri = Uri.parse(uriString);
                ivOriginalImage.setImageURI(imageUri);
            }

            // 2. 판별 결과 및 확률 세팅
            if (intent.hasExtra("analysis_result")) {
                AnalysisResult result = intent.getParcelableExtra("analysis_result");
                if (result != null) {
                    ivHeatmapImage.setImageBitmap(result.heatmapBitmap);

                    float probability = result.probability;
                    pbResultGauge.setProgress((int) probability);

                    // 50% 이상이면 Fake(네온 핑크), 미만이면 Real(네온 시안)
                    if (probability >= 50.0f) {
                        tvResultText.setText(String.format("판별 결과 : 거짓 (%.1f%%)", probability));
                        tvResultText.setTextColor(Color.parseColor("#FF5E62"));
                        pbResultGauge.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF5E62")));

                        // [추가] 가짜면 히트맵을 보여주고 슬라이더 활성화
                        ivHeatmapImage.setVisibility(View.VISIBLE);
                        sbOpacitySlider.setEnabled(true);
                    } else {
                        tvResultText.setText(String.format("판별 결과 : 참 (%.1f%%)", probability));
                        tvResultText.setTextColor(Color.parseColor("#00D2FF"));
                        pbResultGauge.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#00D2FF")));

                        // [추가] 진짜면 히트맵을 숨기고 슬라이더 비활성화(잠금)
                        ivHeatmapImage.setVisibility(View.INVISIBLE);
                        sbOpacitySlider.setEnabled(false);
                        sbOpacitySlider.setProgress(0); // 슬라이더 동그라미를 맨 왼쪽으로 초기화
                    }
                }
            }
        }
    }

    private void setupSlider() {
        sbOpacitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float alpha = progress / 100f;
                ivHeatmapImage.setAlpha(alpha);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void showDeleteConfirmDialog() {
        new AlertDialog.Builder(this)
                .setMessage("이 기록을 삭제하시겠습니까?")
                .setPositiveButton("네", (dialog, which) -> {
                    Toast.makeText(ResultActivity.this, "삭제되었습니다.", Toast.LENGTH_SHORT).show();
                    // 추후 Firebase 삭제 로직 추가 위치
                })
                .setNegativeButton("아니요", (dialog, which) -> dialog.dismiss())
                .show();
    }
}