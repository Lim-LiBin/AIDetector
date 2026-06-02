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

// 사용자가 작성한 문의 내역을 조회화고 목록으로 표시하는 화면
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

        // 뒤로가기 버튼 클릭 시 현재 화면 종료
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        rvInquiry = findViewById(R.id.rv_inquiry);
        tvEmpty = findViewById(R.id.tv_empty_inquiry);

        // Firebase 인증 및 Firestore 인스턴스 초기화
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // 문의 내역 RecyclerView 초기화
        adapter = new InquiryHistoryAdapter(this);
        rvInquiry.setLayoutManager(new LinearLayoutManager(this));
        rvInquiry.setAdapter(adapter);

        loadInquiries();
    }

    // 로그인한 사용자의 문의 내역을 Firestore에서 조회
    private void loadInquiries() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        // 사용자 UID 기준으로 문의 내역을 최신순 조회
        db.collection("contacts")
                .whereEqualTo("uid", uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<InquiryRecord> list = new ArrayList<>();
                    // Firestore 문서를 InquiryRecord 객체로 변환
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        InquiryRecord record = doc.toObject(InquiryRecord.class);
                        // Firestore 문서 ID를 객체에 저장
                        record.setId(doc.getId());
                        list.add(record);
                    }

                    // 조회된 문의 내역 여부에 따라 목록 또는 빈 화면 표시
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