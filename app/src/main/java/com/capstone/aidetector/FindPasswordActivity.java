package com.capstone.aidetector;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class FindPasswordActivity extends AppCompatActivity {

    private EditText emailInput;
    private Button btnSend;
    private ImageButton backBtn;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_password);

        auth = FirebaseAuth.getInstance();

        emailInput = findViewById(R.id.email_input);
        btnSend = findViewById(R.id.btn_send);
        backBtn = findViewById(R.id.back_btn);

        // 뒤로가기 버튼
        backBtn.setOnClickListener(v -> finish());

        // 전송 버튼 클릭
        btnSend.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();

            if (email.isEmpty()) {
                Toast.makeText(this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // [핵심] 파이어베이스 비밀번호 재설정 메일 전송
            auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "재설정 메일을 보냈습니다! 메일함을 확인해주세요.", Toast.LENGTH_LONG).show();
                            finish(); // 성공 시 화면 닫기
                        } else {
                            Toast.makeText(this, "오류: 가입되지 않은 메일이거나 일시적 서버 오류입니다.", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }
}