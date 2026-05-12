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
import android.util.Log;
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

        if (getIntent().hasExtra("record")) {
            currentRecord = (HistoryRecord) getIntent().getSerializableExtra("record");
        }

        receiveAndSetData();
        setupSlider();
    }

    private void initViews() {
        tvResultText = findViewById(R.id.tv_result_text);
        pbResultGauge = findViewById(R.id.pb_result_gauge);
        ivOriginalImage = findViewById(R.id.iv_original_image);
        ivHeatmapImage = findViewById(R.id.iv_heatmap_image);
        ivContainer = findViewById(R.id.iv_container);
        sbOpacitySlider = findViewById(R.id.sb_opacity_slider);
        // XML에서 추가한 캡처 영역 ID 연결
        llResultContent = findViewById(R.id.ll_result_content);
    }

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

    private void setupMenuItemStyle(MenuItem item) {
        if (item != null) {
            SpannableString s = new SpannableString(item.getTitle());
            s.setSpan(new ForegroundColorSpan(Color.BLACK), 0, s.length(), 0);
            s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), 0);
            item.setTitle(s);
        }
    }

    private void shareResultWithCompositeImage() {
        if (llResultContent == null) {
            Toast.makeText(this, "공유 레이아웃을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 텍스트+이미지 영역 캡처
            Bitmap bitmap = Bitmap.createBitmap(llResultContent.getWidth(), llResultContent.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            llResultContent.draw(canvas);

            File cachePath = new File(getExternalCacheDir(), "images");
            if (!cachePath.exists()) cachePath.mkdirs();
            File file = new File(cachePath, "analysis_result.png");
            FileOutputStream stream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

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
            } else if (BitmapHolder.originalBitmap != null) {
                ivOriginalImage.setImageBitmap(BitmapHolder.originalBitmap);
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

        // Toolbar 형변환 에러 방지
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            MenuItem reportItem = toolbar.getMenu().findItem(R.id.action_report);
            if (reportItem != null) {
                String snsUrl = getSnsUrl();
                reportItem.setVisible(probability > 35.0f && snsUrl != null && !snsUrl.isEmpty());
            }
        }
    }

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
        BitmapHolder.heatmapBitmap = null;
        BitmapHolder.originalBitmap = null;
    }
}