package com.capstone.aidetector;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

// 사용자 문의 작성 및 Firestore 저장을 담당하는 화면
public class ContactActivity extends AppCompatActivity {
    private EditText etTitle, etBody;
    private Button btnSend;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact);

        // Firebase 인증 및 Firestore 인스턴스 초기화
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // 뒤로가기 버튼 클릭 시 현재 화면 종료
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 문의 제목, 내용 입력창 및 전송 버튼 연결
        etTitle = findViewById(R.id.et_contact_title);
        etBody = findViewById(R.id.et_contact_body);
        btnSend = findViewById(R.id.btn_contact_send);

        // 전송 버튼 클릭 시 문의 저장 처리
        btnSend.setOnClickListener(v -> sendInquiry());
    }

    // 입력된 문의 내용을 Firestore에 저장
    private void sendInquiry() {
        String title = etTitle.getText().toString().trim();
        String body = etBody.getText().toString().trim();

        // 제목 또는 내용이 비어 있으면 저장하지 않음
        if (title.isEmpty() || body.isEmpty()) {
            Toast.makeText(this, "제목과 내용을 모두 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 문의 데이터를 Firestore에 저장할 형식으로 구성
        Map<String, Object> inquiry = new HashMap<>();
        inquiry.put("uid", auth.getCurrentUser().getUid());
        inquiry.put("title", title);
        inquiry.put("body", body);
        inquiry.put("status", "접수 완료");
        inquiry.put("timestamp", new java.util.Date());

        // contacts 컬렉션에 문의 데이터 저장
        db.collection("contacts").add(inquiry)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "문의가 전송되었습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "전송 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}