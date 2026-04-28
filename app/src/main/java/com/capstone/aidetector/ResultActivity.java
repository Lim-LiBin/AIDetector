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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        initViews();
        setupToolbar();

        // currentRecord를 먼저 할당해야 receiveAndSetData에서 활용 가능합니다.
        currentRecord = (HistoryRecord) getIntent().getSerializableExtra("record");

        receiveAndSetData();
        setupSlider();

        if (currentRecord == null) {
            Log.d("ResultActivity", "방금 분석한 결과입니다. (이력 데이터 없음)");
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
        MenuItem deleteItem = menu.findItem(R.id.action_delete);
        if (deleteItem != null) {
            SpannableString s = new SpannableString(deleteItem.getTitle());
            s.setSpan(new ForegroundColorSpan(Color.parseColor("#000000")), 0, s.length(), 0);
            s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), 0);
            deleteItem.setTitle(s);
        }

        // 신고하기 버튼 폰트 설정
        MenuItem reportItem = menu.findItem(R.id.action_report);
        if (reportItem != null) {
            SpannableString s = new SpannableString(reportItem.getTitle());
            s.setSpan(new ForegroundColorSpan(Color.parseColor("#000000")), 0, s.length(), 0);
            s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), 0);
            reportItem.setTitle(s);
        }

        toolbar.setNavigationOnClickListener(v -> finish());

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_delete) {
                showDeleteConfirmDialog();
                return true;
            } else if (item.getItemId() == R.id.action_report) {
                executeReport(); // 팝업 없이 바로 신고 페이지 이동
                return true;
            }
            return false;
        });
    }

    // URL을 가져오는 공통 메서드 분리
    private String getSnsUrl() {
        if (currentRecord != null && currentRecord.getSnsUrl() != null) {
            return currentRecord.getSnsUrl();
        } else {
            Intent intent = getIntent();
            if (intent == null) return null;
            String url = intent.getStringExtra("snsUrl");
            if (url == null) url = intent.getStringExtra("image_url");
            if (url == null) url = intent.getStringExtra("video_url");
            return url;
        }
    }

    private void receiveAndSetData() {
        Intent intent = getIntent();
        if (intent == null) return;

        boolean fromHistory = intent.getBooleanExtra("from_history", false);

        if (fromHistory) {
            // --- 이력에서 온 경우 ---
            if (currentRecord != null) {
                updateUIByResult(currentRecord.getResult(), currentRecord.getProbability());
                Glide.with(this).load(currentRecord.getOriginalUrl()).into(ivOriginalImage);
                Glide.with(this).load(currentRecord.getHeatmapUrl()).into(ivHeatmapImage);
            }
        } else {
            // --- 방금 막 분석을 완료하고 로딩 화면을 거쳐 넘어온 경우 ---

            // 1. 원본 이미지 표시 (바이트 배열 -> 로컬 URI -> Firebase URL 순서로 확인)
            if (intent.hasExtra("original_image_bytes")) {
                byte[] byteArray = intent.getByteArrayExtra("original_image_bytes");
                if (byteArray != null) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
                    ivOriginalImage.setImageBitmap(bitmap);
                }
            } else if (intent.hasExtra("original_image_uri")) {
                String uriString = intent.getStringExtra("original_image_uri");
                Glide.with(this).load(Uri.parse(uriString)).into(ivOriginalImage);
            } else if (currentRecord != null && currentRecord.getOriginalUrl() != null) {
                // ⭐️ URL 분석이나 영상 분석의 경우 Firebase에 저장된 URL을 사용
                Glide.with(this).load(currentRecord.getOriginalUrl()).into(ivOriginalImage);
            }

            // 2. 히트맵 및 결과 텍스트 표시
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
            tvResultText.setText(String.format("판별 결과 : 거짓 (%.1f%%)", probability));
            tvResultText.setTextColor(Color.parseColor("#FF5E62"));
            pbResultGauge.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF5E62")));
            ivHeatmapImage.setVisibility(View.VISIBLE);
            sbOpacitySlider.setEnabled(true);
        } else {
            tvResultText.setText(String.format("판별 결과 : 참 (%.1f%%)", probability));
            tvResultText.setTextColor(Color.parseColor("#00D2FF"));
            pbResultGauge.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#00D2FF")));
            ivHeatmapImage.setVisibility(View.INVISIBLE);
            sbOpacitySlider.setEnabled(false);
            sbOpacitySlider.setProgress(0);
        }
        pbResultGauge.setProgress((int) probability);

        // 가짜(Fake)이면서, SNS URL이 존재할 때만 툴바에 신고하기 버튼 노출
        Toolbar toolbar = findViewById(R.id.toolbar);
        MenuItem reportItem = toolbar.getMenu().findItem(R.id.action_report);
        if (reportItem != null) {
            String snsUrl = getSnsUrl();
            boolean hasUrl = (snsUrl != null && !snsUrl.isEmpty());
            reportItem.setVisible(isFake && hasUrl);
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

    // 실제 플랫폼별 웹페이지 이동 로직
    private void executeReport() {
        String snsUrl = getSnsUrl();

        if (snsUrl != null && !snsUrl.isEmpty()) {
            String reportUrl = null;

            String lowerUrl = snsUrl.toLowerCase();

            if (lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")) {
                reportUrl = "https://support.google.com/youtube/answer/2802027?hl=ko&co=GENIE.Platform%3DDesktop";
            } else if (lowerUrl.contains("instagram.com")) {
                reportUrl = "https://help.instagram.com/?locale=ko_KR";
            } else if (lowerUrl.contains("tiktok.com")) {
                reportUrl = "https://www.tiktok.com/safety/ko-kr/reporting/";
            }

            if (reportUrl != null) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(reportUrl)));
            } else {
                Toast.makeText(this, "지원하지 않는 SNS URL입니다.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "URL 정보가 없는 데이터입니다.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 메모리 누수를 막기 위해 액티비티가 종료될 때 둘 다 비워줍니다.
        BitmapHolder.heatmapBitmap = null;
        BitmapHolder.originalBitmap = null;
    }
}