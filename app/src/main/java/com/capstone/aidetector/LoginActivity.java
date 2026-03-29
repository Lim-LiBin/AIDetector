package com.capstone.aidetector;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput, pwInput;
    private Button loginBtn;
    private FirebaseAuth auth;
    private TextView goToSignupText, findPwText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        // 1. 자동 로그인 체크 (로그인 되어 있으면 바로 메인으로)
        if (auth.getCurrentUser() != null) {
            moveToMain();
            return; // 자동 로그인 시 아래 리스너 설정은 건너뜀
        }

        // 2. 뷰 초기화 (onCreate에서 수행해야 함)
        initViews();

        // 3. 리스너 설정
        setupListeners();
    }

    private void initViews() {
        emailInput = findViewById(R.id.email_input);
        pwInput = findViewById(R.id.pw_input);
        loginBtn = findViewById(R.id.login_btn);
        goToSignupText = findViewById(R.id.go_to_signup_text);
        findPwText = findViewById(R.id.find_pw_text);
    }

    private void setupListeners() {
        // [로그인 버튼 클릭]
        loginBtn.setOnClickListener(v -> performLogin());

        // [회원가입 텍스트 클릭] -> 이제 정상 작동합니다.
        goToSignupText.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(intent);
        });

        // [비밀번호 찾기 클릭]
        findPwText.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, FindPasswordActivity.class);
            startActivity(intent);
        });
    }

    private void performLogin() {
        String email = emailInput.getText().toString().trim();
        String password = pwInput.getText().toString().trim();

        // 빈칸 검사
        if (email.isEmpty()) {
            emailInput.setError("아이디(이메일)를 입력해주세요.");
            emailInput.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            pwInput.setError("비밀번호를 입력해주세요.");
            pwInput.requestFocus();
            return;
        }

        // Firebase 로그인 시도
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    Toast.makeText(this, "환영합니다!", Toast.LENGTH_SHORT).show();
                    moveToMain();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "로그인 실패: 아이디/비번을 확인하세요.", Toast.LENGTH_SHORT).show()
                );
    }

    private void moveToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish(); // 로그인 화면 종료
    }
}