package com.capstone.aidetector;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.bumptech.glide.Glide;

public class ResultActivity extends AppCompatActivity {

    private TextView tvResultText;
    private ProgressBar pbResultGauge;
    private ImageView ivOriginalImage;
    private ImageView ivHeatmapImage;
    private SeekBar sbOpacitySlider;
    private FirebaseManager firebaseManager = new FirebaseManager();
    private HistoryRecord currentRecord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        initViews();
        setupToolbar();
        receiveAndSetData();
        setupSlider();

        currentRecord = (HistoryRecord) getIntent().getSerializableExtra("record");

        if (currentRecord == null) {
            Log.d("ResultActivity", "방금 분석한 결과입니다. (이력 데이터 없음)");
            return;
        }
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

        // 툴바 안에서 직접 메뉴 아이템을 찾아 글씨 속성 변경
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
        if (intent == null) return;

        boolean fromHistory = intent.getBooleanExtra("from_history", false);

        if (fromHistory) {
            // --- 이력에서 온 경우 (기존 코드 유지) ---
            currentRecord = (HistoryRecord) intent.getSerializableExtra("record");
            if (currentRecord != null) {
                updateUIByResult(currentRecord.getResult(), currentRecord.getProbability());
                Glide.with(this).load(currentRecord.getOriginalUrl()).into(ivOriginalImage);
                Glide.with(this).load(currentRecord.getHeatmapUrl()).into(ivHeatmapImage);
            }
        } else {
            // --- 방금 막 분석을 완료하고 로딩 화면을 거쳐 넘어온 경우 ---

            // 1. 보관소에서 원본 이미지 꺼내서 표시 (기존 byte/uri 방식 대체)
            if (BitmapHolder.originalBitmap != null) {
                ivOriginalImage.setImageBitmap(BitmapHolder.originalBitmap);
            }

            // 2. 히트맵 및 결과 텍스트 표시
            AnalysisResult result = intent.getParcelableExtra("analysis_result");
            if (result != null) {
                updateUIByResult(result.probability >= 50.0f ? "Fake" : "Real", result.probability);

                // 로딩 액티비티에서 보관소에 넣어둔 히트맵 비트맵을 꺼내서 표시
                if (BitmapHolder.heatmapBitmap != null) {
                    ivHeatmapImage.setImageBitmap(BitmapHolder.heatmapBitmap);
                }
            }
        }
    }

    private void updateUIByResult(String result, float probability) {
        boolean isFake = "Fake".equalsIgnoreCase(result) || probability >= 50.0f;

        if (isFake) {
            tvResultText.setText(String.format("판별 결과 : 거짓 (%.1f%%)", probability));
            tvResultText.setTextColor(Color.parseColor("#FF5E62")); // 네온 핑크
            pbResultGauge.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF5E62")));
            ivHeatmapImage.setVisibility(View.VISIBLE);
            sbOpacitySlider.setEnabled(true);
        } else {
            tvResultText.setText(String.format("판별 결과 : 참 (%.1f%%)", probability));
            tvResultText.setTextColor(Color.parseColor("#00D2FF")); // 네온 시안
            pbResultGauge.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#00D2FF")));
            ivHeatmapImage.setVisibility(View.INVISIBLE);
            sbOpacitySlider.setEnabled(false);
            sbOpacitySlider.setProgress(0);
        }
        pbResultGauge.setProgress((int) probability);
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
        if (currentRecord == null) {
            Toast.makeText(this, "이력(History) 화면에서 들어와야 삭제가 가능합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setMessage("이 기록을 삭제하시겠습니까?")
                .setPositiveButton("네", (dialog, which) -> {
                    firebaseManager.deleteHistory(currentRecord, () -> {
                        // 삭제가 성공적으로 끝난 후 실행될 코드
                        Toast.makeText(ResultActivity.this, "기록이 삭제되었습니다.", Toast.LENGTH_SHORT).show();

                        // 삭제되었으니 결과 화면을 닫고 이전 화면(이력 리스트)으로 돌아갑니다.
                        finish();
                    });
                })
                .setNegativeButton("아니요", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 메모리 누수를 막기 위해 액티비티가 종료될 때 둘 다 비워줍니다.
        BitmapHolder.heatmapBitmap = null;
        BitmapHolder.originalBitmap = null;
    }
}