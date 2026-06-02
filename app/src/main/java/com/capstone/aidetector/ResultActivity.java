package com.capstone.aidetector;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

// 분석 결과 표시, 히트맵 조절, 결과 공유, 신고 및 삭제 기능을 담당하는 화면
public class ResultActivity extends AppCompatActivity {

    private TextView tvResultText;
    private ProgressBar pbResultGauge;
    private ImageView ivOriginalImage;
    private ImageView ivHeatmapImage;
    private FrameLayout ivContainer;
    private LinearLayout llResultContent;
    private SeekBar sbOpacitySlider;
    private FirebaseManager firebaseManager = new FirebaseManager();
    private HistoryRecord currentRecord;

    private String shareSummary = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        initViews();
        setupToolbar();

        // 이력 화면에서 전달된 분석 기록이 있으면 현재 기록으로 저장
        if (getIntent().hasExtra("record")) {
            currentRecord = (HistoryRecord) getIntent().getSerializableExtra("record");
        }

        receiveAndSetData();
        setupSlider();
    }

    // 결과 화면에서 사용하는 View 연결
    private void initViews() {
        tvResultText = findViewById(R.id.tv_result_text);
        pbResultGauge = findViewById(R.id.pb_result_gauge);
        ivOriginalImage = findViewById(R.id.iv_original_image);
        ivHeatmapImage = findViewById(R.id.iv_heatmap_image);
        ivContainer = findViewById(R.id.iv_container);
        sbOpacitySlider = findViewById(R.id.sb_opacity_slider);
        llResultContent = findViewById(R.id.ll_result_content);
    }

    // 상단 툴바 메뉴와 클릭 이벤트 설정
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_result);
        Menu menu = toolbar.getMenu();

        setupMenuItemStyle(menu.findItem(R.id.action_share));
        setupMenuItemStyle(menu.findItem(R.id.action_delete));
        setupMenuItemStyle(menu.findItem(R.id.action_contact));
        setupMenuItemStyle(menu.findItem(R.id.action_report));

        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_share) {
                shareResultWithCompositeImage();
                return true;
            } else if (id == R.id.action_delete) {
                showDeleteConfirmDialog();
                return true;
            } else if (id == R.id.action_report) {
                executeReport();
                return true;
            } else if (id == R.id.action_contact) {
                startActivity(new Intent(this, ContactActivity.class));
                return true;
            }
            return false;
        });
    }

    // 툴바 메뉴 항목의 글자 색상과 굵기 설정
    private void setupMenuItemStyle(MenuItem item) {
        if (item != null) {
            SpannableString s = new SpannableString(item.getTitle());
            s.setSpan(new ForegroundColorSpan(Color.BLACK), 0, s.length(), 0);
            s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), 0);
            item.setTitle(s);
        }
    }

    // 결과 영역을 이미지로 캡처하여 공유
    private void shareResultWithCompositeImage() {
        if (llResultContent == null) {
            Toast.makeText(this, "공유 레이아웃을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 텍스트와 이미지가 포함된 결과 영역 캡쳐
            Bitmap bitmap = Bitmap.createBitmap(llResultContent.getWidth(), llResultContent.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            llResultContent.draw(canvas);

            // 캡처한 이미지를 캐시 파일로 저장
            File cachePath = new File(getExternalCacheDir(), "images");
            if (!cachePath.exists()) cachePath.mkdirs();
            File file = new File(cachePath, "analysis_result.png");
            FileOutputStream stream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            // FileProvider Uri를 이용해 외부 앱으로 결과 공유
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            if (contentUri != null) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.setType("image/png");
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.putExtra(Intent.EXTRA_TEXT, "[D-Tect 분석 결과]\n" + shareSummary);
                startActivity(Intent.createChooser(shareIntent, "결과 공유하기"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 이력 데이터 또는 Intent에서 원본 SNS URL 조회
    private String getSnsUrl() {
        String url = null;
        if (currentRecord != null && currentRecord.getSnsUrl() != null && !currentRecord.getSnsUrl().isEmpty()) {
            url = currentRecord.getSnsUrl();
        }
        if (url == null || url.isEmpty()) {
            Intent intent = getIntent();
            url = intent.getStringExtra("snsUrl");
            if (url == null) url = intent.getStringExtra("image_url");
            if (url == null) url = intent.getStringExtra("video_url");
        }
        return url;
    }

    // 전달받은 분석 결과 또는 이력 데이터를 화면에 표시
    private void receiveAndSetData() {
        Intent intent = getIntent();
        if (intent == null) return;

        boolean fromHistory = intent.getBooleanExtra("from_history", false);

        if (fromHistory) {
            // 이력 화면에서 진입한 경우 저장된 이미지가 URL과 결과값 사용
            currentRecord = (HistoryRecord) intent.getSerializableExtra("record");
            if (currentRecord != null) {
                updateUIByResult(currentRecord.getResult(), currentRecord.getProbability());
                Glide.with(this).load(currentRecord.getOriginalUrl()).into(ivOriginalImage);
                Glide.with(this).load(currentRecord.getHeatmapUrl()).into(ivHeatmapImage);
            }
        } else {
            // 새 분석 결과 화면으로 진입한 경우 전달받은 원본 이미지 표시
            if (intent.hasExtra("original_image_bytes")) {
                byte[] byteArray = intent.getByteArrayExtra("original_image_bytes");
                if (byteArray != null) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
                    ivOriginalImage.setImageBitmap(bitmap);
                }
            } else if (intent.hasExtra("original_image_uri")) {
                String uriString = intent.getStringExtra("original_image_uri");
                Glide.with(this).load(Uri.parse(uriString)).into(ivOriginalImage);
            } else if (BitmapHolder.originalBitmap != null) {
                ivOriginalImage.setImageBitmap(BitmapHolder.originalBitmap);
            }

            // 새 분석 결과의 확률값과 히트맵 표시
            AnalysisResult result = intent.getParcelableExtra("analysis_result");
            if (result != null) {
                updateUIByResult(result.probability >= 50.0f ? "Fake" : "Real", result.probability);
                if (BitmapHolder.heatmapBitmap != null) {
                    ivHeatmapImage.setImageBitmap(BitmapHolder.heatmapBitmap);
                }
            }
        }
    }

    // 확률값에 따라 결과 문구, 색상, 히트맵 표시 여부를 설정
    private void updateUIByResult(String result, float probability) {
        if (probability <= 35.0f) {
            shareSummary = String.format("%.1f%% 확률로 '진짜' 콘텐츠입니다.", probability);
            tvResultText.setText(shareSummary);
            tvResultText.setTextColor(Color.parseColor("#00D2FF"));
            pbResultGauge.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#00D2FF")));
            ivHeatmapImage.setVisibility(View.INVISIBLE);
            sbOpacitySlider.setEnabled(false);
            sbOpacitySlider.setProgress(0);
        } else if (probability <= 65.0f) {
            shareSummary = String.format("%.1f%% 확률로 '조작 가능성'이 있습니다.", probability);
            tvResultText.setText(shareSummary);
            tvResultText.setTextColor(Color.parseColor("#FFBB00"));
            pbResultGauge.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FFBB00")));
            ivHeatmapImage.setVisibility(View.VISIBLE);
            sbOpacitySlider.setEnabled(true);
        } else {
            shareSummary = String.format("%.1f%% 확률로 'AI 생성'이 의심됩니다.", probability);
            tvResultText.setText(shareSummary);
            tvResultText.setTextColor(Color.parseColor("#FF5E62"));
            pbResultGauge.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF5E62")));
            ivHeatmapImage.setVisibility(View.VISIBLE);
            sbOpacitySlider.setEnabled(true);
        }

        pbResultGauge.setProgress((int) probability);

        // 분석 결과와 URL 존재 여부에 따라 신고 메뉴 표시 여부 설정
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            MenuItem reportItem = toolbar.getMenu().findItem(R.id.action_report);
            if (reportItem != null) {
                String snsUrl = getSnsUrl();
                reportItem.setVisible(probability > 35.0f && snsUrl != null && !snsUrl.isEmpty());
            }
        }
    }

    // 히트맵 투명도 조절 슬라이더 설정
    private void setupSlider() {
        sbOpacitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ivHeatmapImage.setAlpha(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    // 이력 삭제 여부 확인 다이얼로그 표시
    private void showDeleteConfirmDialog() {
        if (currentRecord == null) {
            Toast.makeText(this, "이력 화면에서만 삭제가 가능합니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage("이 기록을 삭제하시겠습니까?")
                .setPositiveButton("네", (d, which) -> {
                    firebaseManager.deleteHistory(currentRecord, () -> {
                        Toast.makeText(this, "기록이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                })
                .setNegativeButton("아니요", null)
                .show();
    }

    // 원본 SNS URL에 따라 해당 플랫폼 신고 페이지로 이동
    private void executeReport() {
        String snsUrl = getSnsUrl();
        if (snsUrl == null) return;

        String reportUrl = null;
        String lowerUrl = snsUrl.toLowerCase();
        if (lowerUrl.contains("youtube") || lowerUrl.contains("youtu.be")) reportUrl = "https://support.google.com/youtube/answer/2802027";
        else if (lowerUrl.contains("instagram")) reportUrl = "https://help.instagram.com/";
        else if (lowerUrl.contains("tiktok")) reportUrl = "https://www.tiktok.com/safety/ko-kr/reporting/";

        if (reportUrl != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(reportUrl)));
        else Toast.makeText(this, "지원하지 않는 SNS URL입니다.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 결과 화면 조료 시 임시 Bitmap 참조 해제
        BitmapHolder.heatmapBitmap = null;
        BitmapHolder.originalBitmap = null;
    }
}