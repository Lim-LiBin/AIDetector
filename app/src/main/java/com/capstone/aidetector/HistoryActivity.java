package com.capstone.aidetector;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.capstone.aidetector.model.HistoryRecord;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private FirebaseManager firebaseManager;
    private TextView tvEmptyMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // 1. 초기화
        firebaseManager = new FirebaseManager();
        tvEmptyMessage = findViewById(R.id.tv_empty_message);
        recyclerView = findViewById(R.id.recyclerView);

        // 2. 리사이클러뷰 설정
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter();
        recyclerView.setAdapter(adapter);

        // 3. 데이터 가져오기
        fetchData();


    }

    // 데이터 새로고침 함수
    public void fetchData() {
        firebaseManager.loadHistory(new FirebaseManager.OnHistoryLoadedListener() {
            @Override
            public void onSuccess(List<HistoryRecord> list) {
                if (list == null || list.isEmpty()) {
                    tvEmptyMessage.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    tvEmptyMessage.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    adapter.setItems(list);
                }
            }
        });
    }

    // 검증용 삭제 팝업 (길게 눌렀을 때 실행됨)
    public void requestDelete(HistoryRecord record) {
        new AlertDialog.Builder(this)
                .setTitle("기록 삭제")
                .setMessage("정말 이 기록을 삭제하시겠습니까?")
                .setPositiveButton("네", (dialog, which) -> {
                    firebaseManager.deleteHistory(record, () -> {
                        Toast.makeText(this, "삭제되었습니다.", Toast.LENGTH_SHORT).show();
                        fetchData(); // 삭제 후 목록 새로고침
                    });
                })
                .setNegativeButton("아니요", null)
                .show();
    }
}