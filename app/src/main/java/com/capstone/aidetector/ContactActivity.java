package com.capstone.aidetector;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // 추가됨
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class ContactActivity extends AppCompatActivity {
    private EditText etTitle, etBody;
    private Button btnSend;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // --- 여기서부터 상단바 설정 (새로 추가된 부분) ---
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        etTitle = findViewById(R.id.et_contact_title);
        etBody = findViewById(R.id.et_contact_body);
        btnSend = findViewById(R.id.btn_contact_send);

        btnSend.setOnClickListener(v -> sendInquiry());
    }

    private void sendInquiry() {
        String title = etTitle.getText().toString().trim();
        String body = etBody.getText().toString().trim();

        if (title.isEmpty() || body.isEmpty()) {
            Toast.makeText(this, "제목과 내용을 모두 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> inquiry = new HashMap<>();
        inquiry.put("uid", auth.getCurrentUser().getUid());
        inquiry.put("title", title);
        inquiry.put("body", body);
        inquiry.put("status", "접수 완료"); // 문의 내역 페이지를 위한 상태값 추가
        inquiry.put("timestamp", new java.util.Date());

        db.collection("contacts").add(inquiry)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "문의가 전송되었습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "전송 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}