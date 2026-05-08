package com.capstone.aidetector;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// 말풍선 라이브러리 import
import com.skydoves.balloon.Balloon;
import com.skydoves.balloon.BalloonAnimation;
import com.skydoves.balloon.BalloonSizeSpec;
import com.skydoves.balloon.overlay.BalloonOverlayRect;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private ImageButton btnToggleView;
    private LinearLayout layoutNormalBottom;
    private Button btnDeleteMode;
    private ImageButton btnCancelMode; // 뒤로가기(취소) 아이콘

    private List<HistoryRecord> historyList = new ArrayList<>();
    private HistoryAdapter adapter;
    private FirebaseManager firebaseManager;

    private static final String PREF_NAME = "TutorialPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        recyclerView = findViewById(R.id.recyclerViewHistory);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnToggleView = findViewById(R.id.btnToggleView);
        layoutNormalBottom = findViewById(R.id.layoutNormalBottom);
        btnDeleteMode = findViewById(R.id.btnDeleteMode);
        btnCancelMode = findViewById(R.id.btnCancelMode);

        firebaseManager = new FirebaseManager();

        setupRecyclerView();

        // 데이터 불러오기는 어댑터 세팅 이후에 진행 (loadData()는 삭제하고 중복 방지를 위해 제외)
        // 실제 데이터 로딩은 onResume()의 fetchData()에서 일괄 처리하도록 놔둡니다.
        checkEmptyState();

        // 뷰 모드 전환 버튼 이벤트
        btnToggleView.setOnClickListener(v -> {
            boolean isCurrentlyGallery = adapter.isGalleryMode();
            adapter.setGalleryMode(!isCurrentlyGallery);
            updateLayoutManager(!isCurrentlyGallery);
        });

        // 취소 버튼 클릭 이벤트
        btnCancelMode.setOnClickListener(v -> {
            exitSelectionMode();
        });

        // 하단 [삭제하기] 버튼 클릭 이벤트
        btnDeleteMode.setOnClickListener(v -> {
            if (adapter.getSelectedDocIds().isEmpty()) {
                Toast.makeText(this, "삭제할 항목을 선택해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            showDeleteConfirmDialog();
        });

        // 뒤로가기 제어 - 선택 모드 해제
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (adapter != null && adapter.isSelectionMode()) {
                    exitSelectionMode();
                } else {
                    finish(); // 일반 뒤로가기
                }
            }
        });

        // '홈' 탭 클릭 시 MainActivity로 돌아감
        TextView tabHome = findViewById(R.id.tabHome);
        if (tabHome != null) {
            tabHome.setOnClickListener(v -> finish());
        }

        // '설정' 탭 클릭 시 SettingsActivity로 이동
        TextView tabSettings = findViewById(R.id.tabSettings);
        if (tabSettings != null) {
            tabSettings.setOnClickListener(v -> {
                startActivity(new Intent(this, SettingsActivity.class));
            });
        }

        // 튜토리얼 체크 및 실행
        checkAndRunTutorial();
    }

    private void checkAndRunTutorial() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean needsTutorial = prefs.getBoolean("NEEDS_HISTORY_TUTORIAL", false);

        if (needsTutorial) {
            getWindow().getDecorView().post(() -> showHistoryTutorial(prefs));
        }
    }

    private void showHistoryTutorial(SharedPreferences prefs) {
        View targetView = getWindow().getDecorView();

        Balloon balloon = new Balloon.Builder(this)
                .setWidthRatio(0.7f)
                .setHeight(BalloonSizeSpec.WRAP)
                .setText("이곳에 과거 분석 결과들이 \n 저장됩니다.\n이제 마지막으로 \n설정을 볼까요?")
                .setTextColorResource(android.R.color.black)
                .setBackgroundColor(android.graphics.Color.parseColor("#FFFF00"))
                .setCornerRadius(8f)
                .setArrowSize(0)
                .setPadding(16)
                .setTextSize(20f)
                .setIsVisibleOverlay(true)
                .setOverlayColor(android.graphics.Color.parseColor("#E6000000"))
                .setOverlayShape(BalloonOverlayRect.INSTANCE)
                .setBalloonAnimation(BalloonAnimation.FADE)
                .setLifecycleOwner(this)
                .setDismissWhenClicked(true)
                .build();

        balloon.setOnBalloonDismissListener(() -> {
            prefs.edit()
                    .putBoolean("NEEDS_HISTORY_TUTORIAL", false)
                    .putBoolean("NEEDS_SETTINGS_TUTORIAL", true)
                    .apply();

            startActivity(new Intent(HistoryActivity.this, SettingsActivity.class));
            finish();
        });

        balloon.showAtCenter(targetView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // ⭐️ 데이터 로드는 여기서만 진행하여 두 번 중복 호출되는 것을 막습니다.
        if (adapter != null) {
            fetchData();
        }
    }

    private void setupRecyclerView() {
        adapter = new HistoryAdapter(this, new HistoryAdapter.OnItemClickListener() {
            @Override
            public void onShortClick(HistoryRecord record) {
                Intent intent = new Intent(HistoryActivity.this, ResultActivity.class);

                intent.putExtra("from_history", true);
                intent.putExtra("record", record);
                intent.putExtra("snsUrl", record.getSnsUrl());
                intent.putExtra("documentId", record.getDocumentId());
                intent.putExtra("originalUrl", record.getOriginalUrl());
                intent.putExtra("heatmapUrl", record.getHeatmapUrl());
                intent.putExtra("probability", record.getProbability());
                intent.putExtra("result", record.getResult());

                startActivity(intent);
            }

            @Override
            public void onLongClick() {
                enterSelectionMode();
            }

            @Override
            public void onSelectionChanged(int selectedCount) {
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    public void updateData(List<HistoryRecord> newList) {
        this.historyList = newList;
        adapter.setItems(historyList);
        checkEmptyState();
    }

    private void checkEmptyState() {
        if (historyList == null || historyList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void updateLayoutManager(boolean isGallery) {
        if (isGallery) {
            recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
            btnToggleView.setImageResource(android.R.drawable.ic_menu_sort_by_size);
        } else {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            btnToggleView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    private void enterSelectionMode() {
        adapter.setSelectionMode(true);
        layoutNormalBottom.setVisibility(View.GONE);
        btnDeleteMode.setVisibility(View.VISIBLE);
        btnCancelMode.setVisibility(View.VISIBLE);
        btnToggleView.setVisibility(View.GONE);
    }

    private void exitSelectionMode() {
        adapter.setSelectionMode(false);
        layoutNormalBottom.setVisibility(View.VISIBLE);
        btnDeleteMode.setVisibility(View.GONE);
        btnCancelMode.setVisibility(View.GONE);
        btnToggleView.setVisibility(View.VISIBLE);
    }

    private void showDeleteConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("삭제하시겠습니까?")
                .setPositiveButton("네", (dialog, which) -> {
                    Set<String> selectedIds = adapter.getSelectedDocIds();
                    if (selectedIds.isEmpty()) return;

                    int totalToDelete = selectedIds.size();
                    final int[] deletedCount = {0};

                    List<HistoryRecord> copyList = new ArrayList<>(historyList);

                    for (HistoryRecord record : copyList) {
                        if (selectedIds.contains(record.getDocumentId())) {
                            firebaseManager.deleteHistory(record, () -> {
                                deletedCount[0]++;
                                if (deletedCount[0] == totalToDelete) {
                                    runOnUiThread(() -> {
                                        fetchData();
                                        Toast.makeText(HistoryActivity.this, "삭제 완료되었습니다.", Toast.LENGTH_SHORT).show();
                                    });
                                }
                            });
                        }
                    }
                    exitSelectionMode();
                })
                .setNegativeButton("아니요", (dialog, which) -> dialog.dismiss())
                .show();
    }

    public void fetchData() {
        if (firebaseManager == null) {
            firebaseManager = new FirebaseManager();
        }

        firebaseManager.loadHistory(new FirebaseManager.OnHistoryLoadedListener() {
            @Override
            public void onSuccess(List<HistoryRecord> list) {
                historyList = (list != null) ? list : new ArrayList<>();

                runOnUiThread(() -> {
                    if (adapter != null) {
                        adapter.setItems(historyList);
                    }

                    if (historyList.isEmpty()) {
                        if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
                    } else {
                        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                        if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }
}