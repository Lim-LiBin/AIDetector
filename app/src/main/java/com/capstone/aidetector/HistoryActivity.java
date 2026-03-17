package com.capstone.aidetector;

import android.content.Intent;
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
    private TextView tvEmptyMessage;

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

        if (firebaseManager == null) {
            firebaseManager = new FirebaseManager();
        }

        loadData();

        setupRecyclerView();
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // ⭐️ 중요: setupRecyclerView가 onCreate에서 먼저 실행되었는지 확인하세요.
        // 어댑터가 생성된 후에 데이터를 불러와야 안전합니다.
        if (adapter != null) {
            fetchData();
        }
    }

    private void loadData() {
        // FirebaseManager의 loadHistory 호출
        firebaseManager.loadHistory(new FirebaseManager.OnHistoryLoadedListener() {
            @Override
            public void onSuccess(List<HistoryRecord> list) {
                // 받아온 리스트를 어댑터에 갱신
                historyList = list;
                adapter.setItems(historyList);

                // 데이터 유무에 따른 빈 화면 처리
                if (historyList.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new HistoryAdapter(this, new HistoryAdapter.OnItemClickListener() {
            @Override
            public void onShortClick(HistoryRecord record) {
                Intent intent = new Intent(HistoryActivity.this, ResultActivity.class);

                intent.putExtra("from_history", true);
                intent.putExtra("record", record);

                // ResultActivity가 이력에서 왔다는 걸 알 수 있게 꼬리표 달기 및 데이터 전송
                intent.putExtra("from_history", true);
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

    // 데이터를 불러온 후 어댑터에 넘겨주고 빈 화면을 체크하는 용도
    public void updateData(List<HistoryRecord> newList) {
        this.historyList = newList;
        adapter.setItems(historyList);
        checkEmptyState();
    }

    // 예외 화면 처리
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

    // 팝업 및 피드백
    private void showDeleteConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("삭제하시겠습니까?")
                .setPositiveButton("네", (dialog, which) -> {
                    Set<String> selectedIds = adapter.getSelectedDocIds();
                    if (selectedIds.isEmpty()) return;

                    int totalToDelete = selectedIds.size();
                    final int[] deletedCount = {0};

                    // 원본 리스트 복사본으로 반복문 돌리기 (안전성 확보)
                    List<HistoryRecord> copyList = new ArrayList<>(historyList);

                    for (HistoryRecord record : copyList) {
                        if (selectedIds.contains(record.getDocumentId())) {
                            firebaseManager.deleteHistory(record, () -> {
                                deletedCount[0]++;
                                // ⭐️ 선택한 모든 항목이 Firebase에서 지워졌을 때만 새로고침
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

    // 데이터 새로고침 함수
    public void fetchData() {
        if (firebaseManager == null) {
            firebaseManager = new FirebaseManager();
        }

        firebaseManager.loadHistory(new FirebaseManager.OnHistoryLoadedListener() {
            @Override
            public void onSuccess(List<HistoryRecord> list) {
                // 1. 데이터 리스트 업데이트
                historyList = (list != null) ? list : new ArrayList<>();

                // 2. UI 업데이트 (Main Thread 보장 및 Null 체크)
                runOnUiThread(() -> {
                    if (adapter != null) {
                        adapter.setItems(historyList);
                    }

                    // 3. 빈 화면 처리 (tvEmptyMessage 대신 tvEmpty 사용)
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