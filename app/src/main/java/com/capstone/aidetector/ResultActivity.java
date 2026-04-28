package com.capstone.aidetector;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import com.bumptech.glide.Glide;

public class ResultActivity extends AppCompatActivity {

    private TextView tvResultText;
    private ProgressBar pbResultGauge;
    private ImageView ivOriginalImage;
    private ImageView ivHeatmapImage;
    private SeekBar sbOpacitySlider;
    private FirebaseManager firebaseManager = new FirebaseManager();
    private HistoryRecord currentRecord;

    // [공유 기능 추가] 결과 텍스트를 저장할 변수
    private String shareSummary = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        initViews();
        setupToolbar();
        receiveAndSetData();
        setupSlider();

        // currentRecord 할당 위치 조정 (데이터 로딩 전/후 상관없이 안전하게 체크)
        if (getIntent().hasExtra("record")) {
            currentRecord = (HistoryRecord) getIntent().getSerializableExtra("record");
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
        toolbar.inflateMenu(R.menu.menu_result);

        Menu menu = toolbar.getMenu();

        // [공유 기능 추가] 공유 메뉴 아이템 스타일 설정
        MenuItem shareItem = menu.findItem(R.id.action_share);
        if (shareItem != null) {
            SpannableString s = new SpannableString(shareItem.getTitle());
            s.setSpan(new ForegroundColorSpan(Color.parseColor("#000000")), 0, s.length(), 0);
            s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), 0);
            shareItem.setTitle(s);
        }

        MenuItem deleteItem = menu.findItem(R.id.action_delete);
        if (deleteItem != null) {
            SpannableString s = new SpannableString(deleteItem.getTitle());
            s.setSpan(new ForegroundColorSpan(Color.parseColor("#000000")), 0, s.length(), 0);
            s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), 0);
            deleteItem.setTitle(s);
        }

        toolbar.setNavigationOnClickListener(v -> finish());

        // [수정된 부분] 공유하기 메뉴 클릭 리스너 연결
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_share) {
                // 🚀 공유 로직 실행
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, "[D-Tect 분석 결과]\n" + shareSummary);
                sendIntent.setType("text/plain");
                startActivity(Intent.createChooser(sendIntent, "결과 공유하기"));
                return true;
            } else if (id == R.id.action_delete) {
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
            currentRecord = (HistoryRecord) intent.getSerializableExtra("record");
            if (currentRecord != null) {
                updateUIByResult(currentRecord.getResult(), currentRecord.getProbability());
                Glide.with(this).load(currentRecord.getOriginalUrl()).into(ivOriginalImage);
                Glide.with(this).load(currentRecord.getHeatmapUrl()).into(ivHeatmapImage);
            }
        } else {
            if (intent.hasExtra("original_image_bytes")) {
                byte[] byteArray = intent.getByteArrayExtra("original_image_bytes");
                if (byteArray != null) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
                    ivOriginalImage.setImageBitmap(bitmap);
                }
            } else if (intent.hasExtra("original_image_uri")) {
                String uriString = intent.getStringExtra("original_image_uri");
                Glide.with(this).load(Uri.parse(uriString)).into(ivOriginalImage);
            }

            AnalysisResult result = intent.getParcelableExtra("analysis_result");
            if (result != null) {
                updateUIByResult(result.probability >= 50.0f ? "Fake" : "Real", result.probability);

                if (BitmapHolder.heatmapBitmap != null) {
                    ivHeatmapImage.setImageBitmap(BitmapHolder.heatmapBitmap);
                }
            }
        }
    }

    private void updateUIByResult(String result, float probability) {
        boolean isFake = "Fake".equalsIgnoreCase(result) || probability >= 50.0f;

        if (isFake) {
            // [공유 기능 추가] shareSummary 변수 업데이트
            shareSummary = String.format("판별 결과 : 거짓 (%.1f%%)", probability);
            tvResultText.setText(shareSummary);
            tvResultText.setTextColor(Color.parseColor("#FF5E62"));
            pbResultGauge.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF5E62")));
            ivHeatmapImage.setVisibility(View.VISIBLE);
            sbOpacitySlider.setEnabled(true);
        } else {
            // [공유 기능 추가] shareSummary 변수 업데이트
            shareSummary = String.format("판별 결과 : 참 (%.1f%%)", probability);
            tvResultText.setText(shareSummary);
            tvResultText.setTextColor(Color.parseColor("#00D2FF"));
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
                        Toast.makeText(ResultActivity.this, "기록이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                })
                .setNegativeButton("아니요", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BitmapHolder.heatmapBitmap = null;
    }
}