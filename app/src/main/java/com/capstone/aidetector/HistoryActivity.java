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

import com.skydoves.balloon.Balloon;
import com.skydoves.balloon.BalloonAnimation;
import com.skydoves.balloon.BalloonSizeSpec;
import com.skydoves.balloon.overlay.BalloonOverlayRect;

// 분석 이력 목록 조회, 화면 전환, 선택 삭제 기능을 담당하는 화면
public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private ImageButton btnToggleView;
    private LinearLayout layoutNormalBottom;
    private Button btnDeleteMode;
    private ImageButton btnCancelMode;

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

        // 뷰 모드 전환 (리스트/그리드)
        btnToggleView.setOnClickListener(v -> {
            boolean isCurrentlyGallery = adapter.isGalleryMode();
            adapter.setGalleryMode(!isCurrentlyGallery);
            updateLayoutManager(!isCurrentlyGallery);
        });

        // 선택 모드 취소
        btnCancelMode.setOnClickListener(v -> {
            exitSelectionMode();
        });

        // 선택 항목 삭제 실행
        btnDeleteMode.setOnClickListener(v -> {
            if (adapter.getSelectedDocIds().isEmpty()) {
                Toast.makeText(this, "삭제할 항목을 선택해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            showDeleteConfirmDialog();
        });

        // 뒤로가기 제어 (선택 모드 해제 또는 액티비티 종료)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (adapter != null && adapter.isSelectionMode()) {
                    exitSelectionMode();
                } else {
                    finish();
                }
            }
        });

        // 탭 이동 처리 (홈)
        TextView tabHome = findViewById(R.id.tabHome);
        if (tabHome != null) {
            tabHome.setOnClickListener(v -> finish());
        }

        // 탭 이동 처리 (설정)
        TextView tabSettings = findViewById(R.id.tabSettings);
        if (tabSettings != null) {
            tabSettings.setOnClickListener(v -> {
                startActivity(new Intent(this, SettingsActivity.class));
            });
        }

        // 튜토리얼 체크 및 실행
        checkAndRunTutorial();
    }

    // 이력 화면 튜토리얼 실행 여부 확인
    private void checkAndRunTutorial() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean needsTutorial = prefs.getBoolean("NEEDS_HISTORY_TUTORIAL", false);

        if (needsTutorial) {
            getWindow().getDecorView().post(() -> showHistoryTutorial(prefs));
        }
    }

    // 이력 화면 튜토리얼 표시 후 설정 화면으로 이동
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

        // 튜토리얼 종료 시 설정 화면 튜토리얼로 이어지도록 상태값 저장
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
        // 화면 복귀 시 최신 분석 이력 다시 조회
        if (adapter != null) {
            fetchData();
        }
    }

    // 분석 이력 RecyclerView 초기화 및 클릭 이벤트 설정
    private void setupRecyclerView() {
        adapter = new HistoryAdapter(this, new HistoryAdapter.OnItemClickListener() {
            @Override
            public void onShortClick(HistoryRecord record) {
                // 이력 항목 클릭 시 결과 상세 화면으로 이동
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
                // 이력 항목 길게 클릭 시 선택 모드 진입
                enterSelectionMode();
            }

            @Override
            public void onSelectionChanged(int selectedCount) {
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    // 분석 이력 목록 갱신
    public void updateData(List<HistoryRecord> newList) {
        this.historyList = newList;
        adapter.setItems(historyList);
        checkEmptyState();
    }

    // 분석 이력 존재 여부에 따라 빈 화면 표시
    private void checkEmptyState() {
        if (historyList == null || historyList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    // 리스트 보기와 갤러리 보기 전환
    private void updateLayoutManager(boolean isGallery) {
        if (isGallery) {
            recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
            btnToggleView.setImageResource(android.R.drawable.ic_menu_sort_by_size);
        } else {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            btnToggleView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    // 분석 이력 선택 모드 진입
    private void enterSelectionMode() {
        adapter.setSelectionMode(true);
        layoutNormalBottom.setVisibility(View.GONE);
        btnDeleteMode.setVisibility(View.VISIBLE);
        btnCancelMode.setVisibility(View.VISIBLE);
        btnToggleView.setVisibility(View.GONE);
    }

    // 분석 이력 선택 모드 해제
    private void exitSelectionMode() {
        adapter.setSelectionMode(false);
        layoutNormalBottom.setVisibility(View.VISIBLE);
        btnDeleteMode.setVisibility(View.GONE);
        btnCancelMode.setVisibility(View.GONE);
        btnToggleView.setVisibility(View.VISIBLE);
    }

    // 선택된 분석 이력 삭제 확인
    private void showDeleteConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("삭제하시겠습니까?")
                .setPositiveButton("네", (dialog, which) -> {
                    Set<String> selectedIds = adapter.getSelectedDocIds();
                    if (selectedIds.isEmpty()) return;

                    int totalToDelete = selectedIds.size();
                    final int[] deletedCount = {0};

                    List<HistoryRecord> copyList = new ArrayList<>(historyList);

                    // 선택된 하목을 Firebase에서 삭제
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

    // Firebase에서 분석 이력 데이터 조회
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

                    // 조회된 이력 여부에 따라 목록 또는 빈 화면 표시
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