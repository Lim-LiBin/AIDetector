package com.capstone.aidetector;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class InquiryHistoryActivity extends AppCompatActivity {
    private RecyclerView rvInquiry;
    private TextView tvEmpty;
    private InquiryHistoryAdapter adapter;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inquiry_history);

        // 상단바 뒤로가기 설정
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        rvInquiry = findViewById(R.id.rv_inquiry);
        tvEmpty = findViewById(R.id.tv_empty_inquiry);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        adapter = new InquiryHistoryAdapter(this);
        rvInquiry.setLayoutManager(new LinearLayoutManager(this));
        rvInquiry.setAdapter(adapter);

        loadInquiries();
    }

    private void loadInquiries() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        // ⚠️ 중요: 여기서 데이터가 안 나오면 Logcat에서 에러 메시지를 확인하세요.
        // Index가 필요하다는 파란색 링크가 뜨면 클릭해서 인덱스를 생성해야 합니다.
        db.collection("contacts")
                .whereEqualTo("uid", uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<InquiryRecord> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        InquiryRecord record = doc.toObject(InquiryRecord.class);
                        record.setId(doc.getId()); // 문서 ID 수동 세팅
                        list.add(record);
                    }

                    if (list.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        rvInquiry.setVisibility(View.GONE);
                    } else {
                        tvEmpty.setVisibility(View.GONE);
                        rvInquiry.setVisibility(View.VISIBLE);
                        adapter.setItems(list);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("InquiryError", "데이터 로드 실패: " + e.getMessage());
                });
    }
}