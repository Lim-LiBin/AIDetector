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
import android.widget.FrameLayout; // ★ 추가
import android.widget.ImageView;
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
    private FrameLayout ivContainer; // ★ XML의 iv_container를 받기 위한 변수
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

        if (currentRecord == null) {
            Log.d("ResultActivity", "방금 분석한 결과입니다. (이력 데이터 없음)");
        }
    }

    private void initViews() {
        tvResultText = findViewById(R.id.tv_result_text);
        pbResultGauge = findViewById(R.id.pb_result_gauge);
        ivOriginalImage = findViewById(R.id.iv_original_image);
        ivHeatmapImage = findViewById(R.id.iv_heatmap_image);
        ivContainer = findViewById(R.id.iv_container); // ★ XML의 iv_container 연결
        sbOpacitySlider = findViewById(R.id.sb_opacity_slider);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_result);

        Menu menu = toolbar.getMenu();

        // 메뉴 스타일 설정
        setupMenuItemStyle(menu.findItem(R.id.action_share));
        setupMenuItemStyle(menu.findItem(R.id.action_delete));
        setupMenuItemStyle(menu.findItem(R.id.action_contact));
        setupMenuItemStyle(menu.findItem(R.id.action_report));

        toolbar.setNavigationOnClickListener(v -> finish());

        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_share) {
                // ★ [수정] 원본+히트맵 합성 이미지를 공유하도록 변경
                shareResultWithCompositeImage();
                return true;
            } else if (id == R.id.action_delete) {
                showDeleteConfirmDialog();
                return true;
            } else if (item.getItemId() == R.id.action_report) {
                executeReport();
            } else if (item.getItemId() == R.id.action_contact) {
                Intent intent = new Intent(this, ContactActivity.class);
                startActivity(intent);
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

    // ★ [핵심] 원본 위에 히트맵이 겹쳐진 iv_container 영역을 통째로 캡처
    private void shareResultWithCompositeImage() {
        // 히트맵이 표시 중이 아닐 때는 텍스트만 공유
        if (ivHeatmapImage.getVisibility() != View.VISIBLE) {
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, "[D-Tect 분석 결과]\n" + shareSummary);
            sendIntent.setType("text/plain");
            startActivity(Intent.createChooser(sendIntent, "결과 공유하기"));
            return;
        }

        try {
            // 1. ivContainer(FrameLayout)의 현재 모습을 비트맵으로 생성
            Bitmap bitmap = Bitmap.createBitmap(ivContainer.getWidth(), ivContainer.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            ivContainer.draw(canvas);

            // 2. 비트맵을 임시 파일로 저장
            File cachePath = new File(getExternalCacheDir(), "images");
            cachePath.mkdirs();
            File file = new File(cachePath, "analysis_composite.png");
            FileOutputStream stream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            // 3. FileProvider를 통한 Uri 생성
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);

            if (contentUri != null) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.setDataAndType(contentUri, getContentResolver().getType(contentUri));
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.putExtra(Intent.EXTRA_TEXT, "[D-Tect 분석 결과]\n" + shareSummary);
                shareIntent.setType("image/png");
                startActivity(Intent.createChooser(shareIntent, "결과 공유하기"));
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "이미지 생성 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
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
            } else if (currentRecord != null && currentRecord.getOriginalUrl() != null) {
                Glide.with(this).load(currentRecord.getOriginalUrl()).into(ivOriginalImage);
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
            shareSummary = String.format("판별 결과 : 거짓 (%.1f%%)", probability);
            tvResultText.setText(shareSummary);
            tvResultText.setTextColor(Color.parseColor("#FF5E62"));
            pbResultGauge.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF5E62")));
            ivHeatmapImage.setVisibility(View.VISIBLE);
            sbOpacitySlider.setEnabled(true);
        } else {
            shareSummary = String.format("판별 결과 : 참 (%.1f%%)", probability);
            tvResultText.setText(shareSummary);
            tvResultText.setTextColor(Color.parseColor("#00D2FF"));
            pbResultGauge.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#00D2FF")));
            ivHeatmapImage.setVisibility(View.INVISIBLE);
            sbOpacitySlider.setEnabled(false);
            sbOpacitySlider.setProgress(0);
        }
        pbResultGauge.setProgress((int) probability);

        Toolbar toolbar = findViewById(R.id.toolbar);
        Menu menu = toolbar.getMenu();
        MenuItem reportItem = menu.findItem(R.id.action_report);

        if (reportItem != null) {
            String snsUrl = getSnsUrl();
            reportItem.setVisible(isFake && (snsUrl != null && !snsUrl.isEmpty()));
        }
    }

    private void setupSlider() {
        sbOpacitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ivHeatmapImage.setAlpha(progress / 100f);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void showDeleteConfirmDialog() {
        if (currentRecord == null) {
            Toast.makeText(this, "이력 화면에서만 삭제가 가능합니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("이 기록을 삭제하시겠습니까?")
                .setPositiveButton("네", (d, which) -> {
                    firebaseManager.deleteHistory(currentRecord, () -> {
                        Toast.makeText(ResultActivity.this, "기록이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                })
                .setNegativeButton("아니요", (d, which) -> d.dismiss())
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.BLACK);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.BLACK);
    }

    private void executeReport() {
        String snsUrl = getSnsUrl();
        if (snsUrl != null && !snsUrl.isEmpty()) {
            String reportUrl = null;
            String lowerUrl = snsUrl.toLowerCase();
            if (lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")) {
                reportUrl = "https://support.google.com/youtube/answer/2802027";
            } else if (lowerUrl.contains("instagram.com")) {
                reportUrl = "https://help.instagram.com/";
            } else if (lowerUrl.contains("tiktok.com")) {
                reportUrl = "https://www.tiktok.com/safety/ko-kr/reporting/";
            }

            if (reportUrl != null) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(reportUrl)));
            } else {
                Toast.makeText(this, "지원하지 않는 SNS URL입니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BitmapHolder.heatmapBitmap = null;
        BitmapHolder.originalBitmap = null;
    }
}