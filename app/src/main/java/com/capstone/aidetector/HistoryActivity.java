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

import com.capstone.aidetector.model.HistoryRecord;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private ImageButton btnToggleView;
    private LinearLayout layoutNormalBottom;
    private Button btnDeleteMode;
    private ImageButton btnCancelMode; // 뒤로가기(취소) 아이콘

    private HistoryAdapter adapter;
    private List<HistoryRecord> historyList = new ArrayList<>();

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

    private void setupRecyclerView() {
        adapter = new HistoryAdapter(this, new HistoryAdapter.OnItemClickListener() {
            @Override
            public void onShortClick(HistoryRecord record) {
                Intent intent = new Intent(HistoryActivity.this, ResultActivity.class);

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

                    // 💬 Firebase DB 및 Storage 삭제 로직 추가 (adapter.getSelectedDocIds() 활용)

                    Toast.makeText(this, "검사 기록이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                    exitSelectionMode();
                })
                .setNegativeButton("아니요", (dialog, which) -> {
                    dialog.dismiss();
                })
                .show();
    }
}